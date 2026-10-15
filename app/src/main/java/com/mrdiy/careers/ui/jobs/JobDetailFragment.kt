package com.mrdiy.careers.ui.jobs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.mrdiy.careers.R
import com.mrdiy.careers.data.repository.PublishedJobsRepository
import com.mrdiy.careers.data.repository.ApplicationsRepository
import com.mrdiy.careers.data.repository.SavedJobsRepository
import com.mrdiy.careers.databinding.FragmentJobDetailBinding
import com.mrdiy.careers.model.Job
import com.mrdiy.careers.ui.base.BaseFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JobDetailFragment : BaseFragment() {

    private var _binding: FragmentJobDetailBinding? = null
    private val binding get() = _binding!!

    private val publishedJobsRepository = PublishedJobsRepository()
    private var applicationsRepository: ApplicationsRepository? = null
    private var savedJobsRepository: SavedJobsRepository? = null
    private var currentJob: Job? = null
    private var isJobSaved = false
    private var currentEmployment: com.mrdiy.careers.data.repository.ApplicationsRepository.CurrentEmployment? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentJobDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setName("JobDetailFragment")

        try {
            savedJobsRepository = SavedJobsRepository(requireContext())
            applicationsRepository = ApplicationsRepository(requireContext())
        } catch (e: Exception) {
            com.mrdiy.careers.data.SafeDiagnostics.record("request", e)
        }

        val jobId = arguments?.getString("jobId") ?: ""

        if (jobId.isEmpty()) {
            Toast.makeText(requireContext(), "Invalid job ID", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        showLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            publishedJobsRepository.getJobById(jobId).onSuccess { job ->
                currentJob = job
                checkIfJobSaved(job.id)
                displayJob(job)
                checkIfAlreadyEmployed()
            }.onFailure {
                Toast.makeText(requireContext(), "This job is unavailable or could not be loaded.", Toast.LENGTH_LONG).show()
                findNavController().navigateUp()
            }
            if (_binding != null) showLoading(false)
        }

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.btnAskQuestion.setOnClickListener {
            currentJob?.let { job ->
                FaqChatBottomSheetFragment.newInstance(job.id, job.title)
                    .show(childFragmentManager, FaqChatBottomSheetFragment.TAG)
            }
        }

        binding.btnShare.setOnClickListener {
            currentJob?.let { job ->
                val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, "${job.title} at ${job.company}\n${job.location}\n\n${job.aboutRole}")
                }
                startActivity(android.content.Intent.createChooser(share, "Share job"))
            }
        }

        binding.btnApply.setOnClickListener {
            val employment = currentEmployment
            if (employment != null) {
                showAlreadyEmployedDialog(employment)
                return@setOnClickListener
            }
            currentJob?.let { job ->
                binding.btnApply.isEnabled = false
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val result = applicationsRepository?.apply(job)
                        ?: Result.failure(IllegalStateException("Application service unavailable."))
                    withContext(Dispatchers.Main) {
                        if (!isAdded || _binding == null) return@withContext
                        binding.btnApply.isEnabled = true
                        result.onSuccess {
                            Toast.makeText(requireContext(), "Application submitted.", Toast.LENGTH_SHORT).show()
                        }.onFailure { error ->
                            Toast.makeText(
                                requireContext(),
                                error.message?.takeIf { it.isNotBlank() } ?: "Application failed. Please try again.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }

        binding.btnSave.setOnClickListener {
            currentJob?.let { job ->
                toggleSaveJob(job)
            }
        }
    }

    /**
     * Relabels Apply (kept clickable, so tapping it explains why) if the applicant is already
     * Hired elsewhere — see [ApplicationsRepository.currentEmployment].
     */
    private fun checkIfAlreadyEmployed() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val employment = applicationsRepository?.currentEmployment()
            withContext(Dispatchers.Main) {
                if (!isAdded || _binding == null || employment == null) return@withContext
                currentEmployment = employment
                binding.btnApply.text = "Already employed"
            }
        }
    }

    /**
     * Explains why Apply is blocked, and points to HR instead of a self-service action: moving to
     * another branch while employed is a transfer, which Philippine courts treat as a management
     * decision (not something settled by submitting a new job application) — see
     * [ApplicationsRepository.currentEmployment].
     */
    private fun showAlreadyEmployedDialog(employment: com.mrdiy.careers.data.repository.ApplicationsRepository.CurrentEmployment) {
        val where = employment.branchName?.let { " at $it" } ?: ""
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("You're already employed")
            .setMessage(
                "You're currently working as ${employment.jobTitle}$where, so you can't apply for another position " +
                    "right now.\n\nIf you'd like to request a transfer to a different branch or role, this isn't " +
                    "done by applying — please message HR directly and they'll help you with it."
            )
            .setPositiveButton("Message HR") { _, _ ->
                findNavController().navigate(R.id.messagesFragment)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun checkIfJobSaved(jobId: String) {
        if (savedJobsRepository == null) {
            isJobSaved = false
            updateSaveButtonIcon()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val saved = savedJobsRepository?.isJobSaved(jobId) ?: false
                withContext(Dispatchers.Main) {
                    if (!isAdded || _binding == null) return@withContext
                    isJobSaved = saved
                    updateSaveButtonIcon()
                }
            } catch (e: Exception) {
                com.mrdiy.careers.data.SafeDiagnostics.record("request", e)
                withContext(Dispatchers.Main) {
                    isJobSaved = false
                    updateSaveButtonIcon()
                }
            }
        }
    }

    private fun toggleSaveJob(job: Job) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val success = if (isJobSaved) {
                savedJobsRepository?.unsaveJob(job.id) == true
            } else {
                savedJobsRepository?.saveJob(job) == true
            }
            withContext(Dispatchers.Main) {
                if (!isAdded || _binding == null) return@withContext
                if (!success) {
                    Toast.makeText(requireContext(), "Could not update saved job. Please retry.", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                isJobSaved = !isJobSaved
                updateSaveButtonIcon()
                val message = if (isJobSaved) "Job saved!" else "Job removed from saved"
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateSaveButtonIcon() {
        binding.btnSave.setIconResource(
            if (isJobSaved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_outline
        )
    }

    private fun showLoading(show: Boolean) {
        binding.toolbar.isVisible = !show
    }

    private fun displayJob(job: Job) {
        try {
            binding.btnApply.isEnabled = true
            binding.tvJdTitle.text      = job.title
            binding.tvJdCompanyLoc.text = "${job.company} - ${job.location}"

            binding.jdTags.removeAllViews()
            val tags = listOf(job.jobType, job.category, job.level).filter { it.isNotBlank() && it != "Not specified" }
            tags.forEach { tag ->
                binding.jdTags.addView(com.google.android.material.chip.Chip(requireContext()).apply { text = tag; isClickable = false })
            }
            job.openingsRemaining()?.let { remaining ->
                binding.jdTags.addView(com.google.android.material.chip.Chip(requireContext()).apply {
                    text = if (remaining > 0) "$remaining opening${if (remaining == 1) "" else "s"} left" else "Fully booked"
                    isClickable = false
                })
            }
            binding.jdTags.isVisible = binding.jdTags.childCount > 0

            // Don't let applicants apply once the position is filled.
            if (job.isFull()) {
                binding.btnApply.isEnabled = false
                binding.btnApply.text = "Fully booked"
            }
            val monthlyMin = job.getMonthlySalary()
            val monthlyMax = job.getMonthlySalaryMax()
            binding.tvSalaryRange.text = if (job.salaryMin == 0 && job.salaryMax == 0) {
                "Salary not disclosed"
            } else if (monthlyMin == monthlyMax) {
                "₱${"%,d".format(monthlyMin)}/mo"
            } else {
                "₱${"%,d".format(monthlyMin)} – ₱${"%,d".format(monthlyMax)}/mo"
            }

            binding.tvAboutRole.text = job.aboutRole.ifEmpty { "No description provided." }

            addBullets(binding.responsibilitiesContainer, job.responsibilities)
            addBullets(binding.requirementsContainer, job.requirements)
            addBullets(binding.benefitsContainer, job.benefits)
        } catch (e: Exception) {
            com.mrdiy.careers.data.SafeDiagnostics.record("request", e)
            Toast.makeText(requireContext(), "Error displaying job details", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addBullets(container: ViewGroup, items: List<String>) {
        container.removeAllViews()
        if (items.isEmpty()) {
            val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_bullet_point, container, false)
            row.findViewById<TextView>(R.id.tv_bullet_text).text = "Information not available"
            container.addView(row)
        } else {
            items.forEach { text ->
                val row = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_bullet_point, container, false)
                row.findViewById<TextView>(R.id.tv_bullet_text).text = text
                container.addView(row)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
