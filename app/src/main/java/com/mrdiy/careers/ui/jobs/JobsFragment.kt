package com.mrdiy.careers.ui.jobs

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.mrdiy.careers.R
import com.mrdiy.careers.databinding.FragmentJobsBinding
import com.mrdiy.careers.ui.base.BaseFragment
import com.mrdiy.careers.ui.home.JobAdapter

class JobsFragment : BaseFragment() {

    private var _binding: FragmentJobsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: JobsViewModel by viewModels()
    private lateinit var jobAdapter: JobAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentJobsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setName("JobsFragment")

        jobAdapter = JobAdapter(
            jobs = mutableListOf(),
            onJobClick = { job ->
                val action = JobsFragmentDirections.actionJobsToJobDetail(job.id)
                findNavController().navigate(action)
            }
        )
        binding.jobsRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = jobAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(12)
        }

        viewModel.filteredJobs.observe(viewLifecycleOwner) { jobs ->
            jobAdapter.updateList(jobs)
            binding.tvEmptyState.visibility = if (jobs.isEmpty()) View.VISIBLE else View.GONE
            binding.tvJobCount.text = "${jobs.size} jobs found"
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (message != null) com.google.android.material.snackbar.Snackbar
                .make(binding.root, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setAction("Retry") { viewModel.reload() }.show()
        }

        // Search bar
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                viewModel.onSearchQuery(s?.toString())
            }
        })

        // Category chips
        binding.chipGroupCategory.setOnCheckedStateChangeListener { _, checkedIds ->
            val cat = when {
                checkedIds.contains(R.id.chip_retail)    -> "Retail"
                checkedIds.contains(R.id.chip_logistics) -> "Logistics"
                checkedIds.contains(R.id.chip_corporate) -> "Corporate"
                checkedIds.contains(R.id.chip_it)        -> "IT"
                else -> null
            }
            viewModel.onCategoryFilter(cat)
        }

        // Location chips
        binding.chipGroupLocation.setOnCheckedStateChangeListener { _, checkedIds ->
            val loc = when {
                checkedIds.contains(R.id.chip_loc_manila) -> "Metro Manila"
                checkedIds.contains(R.id.chip_loc_cebu)   -> "Cebu City"
                checkedIds.contains(R.id.chip_loc_davao)  -> "Davao City"
                checkedIds.contains(R.id.chip_loc_laguna) -> "Laguna"
                checkedIds.contains(R.id.chip_loc_taguig) -> "BGC, Taguig"
                else -> null
            }
            viewModel.onLocationFilter(loc)
        }

        // Salary range slider
        binding.salarySlider.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            viewModel.onSalaryFilter(values[0].toInt(), values[1].toInt())
        }

        binding.btnClearFilters.setOnClickListener {
            viewModel.clearFilters()
            binding.searchInput.text?.clear()
            binding.chipGroupCategory.clearCheck()
            binding.chipGroupLocation.clearCheck()
            binding.salarySlider.values = listOf(0f, 100000f)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}