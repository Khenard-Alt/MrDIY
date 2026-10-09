package com.mrdiy.careers.ui.profile

import io.github.jan.supabase.storage.storage
import kotlin.time.Duration.Companion.minutes
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.mrdiy.careers.R
import com.mrdiy.careers.data.auth.AuthManager
import com.mrdiy.careers.data.repository.SavedJobsRepository
import com.mrdiy.careers.model.UserProfile
import com.mrdiy.careers.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by activityViewModels()
    private lateinit var authManager: AuthManager
    private lateinit var savedJobsRepository: SavedJobsRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        authManager = AuthManager(requireContext())
        savedJobsRepository = SavedJobsRepository(requireContext())

        setupClickListeners()

        viewModel.refreshProfile()
        viewModel.profile.observe(viewLifecycleOwner) { profile ->
            populateProfile(profile)
        }
        viewModel.profileCompletion.observe(viewLifecycleOwner) { completion ->
            binding.profileProgressBar.progress = completion

        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshProfile()
        updateSavedJobsCount()
        viewLifecycleOwner.lifecycleScope.launch {
            com.mrdiy.careers.data.repository.PublishedJobsRepository().fetchOpenJobs().onSuccess { jobs ->
                val top = com.mrdiy.careers.data.ml.JobMatchingEngine.matchJobs(viewModel.currentProfile(), jobs).maxOfOrNull { it.matchScore }
                binding.tvStatProfile.text = top?.let { "$it%" } ?: "—"
            }.onFailure { binding.tvStatProfile.text = "—" }
        }
    }

    private fun setupClickListeners() {
        binding.btnAvatarCamera.setOnClickListener { findNavController().navigate(R.id.action_profile_to_editProfile) }
        binding.sectionPersonalInfo.btnSectionEdit.setOnClickListener { findNavController().navigate(R.id.action_profile_to_editProfile) }
        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }

        binding.btnEditProfile.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_editProfile)
        }

        binding.btnEditAbout.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_editProfile)
        }

        binding.btnEditSkills.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_editProfile)
        }

        binding.btnAddExperience.setOnClickListener {
            AddExperienceBottomSheet.show(childFragmentManager, null) { newExperience ->
                val currentProfile = viewModel.currentProfile()
                val updatedExperiences = currentProfile.workExperiences.toMutableList()
                updatedExperiences.add(newExperience)
                viewModel.saveProfile(currentProfile.copy(workExperiences = updatedExperiences)) { success ->
                    if (isAdded && !success) Toast.makeText(requireContext(), "Could not save experience. Please retry.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnUploadResume.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_resumeUpload)
        }

        binding.btnSavedJobs.setOnClickListener {
            findNavController().navigate(R.id.savedJobsFragment)
        }

        binding.btnApplications.setOnClickListener {
            findNavController().navigate(R.id.applicationsFragment)
        }
    }

    private fun viewResumePdf(url: String) {
        viewLifecycleOwner.lifecycleScope.launch {
        try {
            val uid = authManager.getCurrentUserId().orEmpty()
            val prefix = "https://sfjpiyevasnmvddgtofz.supabase.co/storage/v1/object/public/resumes/"
            val legacy = url.startsWith(prefix)
            val path = if (legacy) url.removePrefix(prefix) else url
            require(com.mrdiy.careers.data.repository.ResumeFiles.ownedPath(path, uid))
            val signed = com.mrdiy.careers.data.auth.SupabaseProvider.client.storage
                .from(if (legacy) "resumes" else com.mrdiy.careers.data.repository.ResumeFiles.BUCKET)
                .createSignedUrl(path, 5.minutes)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(signed)
            }
            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), "No app available to view PDF", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Could not open resume. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    }

    private fun updateSavedJobsCount() {
        viewLifecycleOwner.lifecycleScope.launch {
            val count = savedJobsRepository.getSavedJobIds().size
            binding.tvSavedCount.text = "$count jobs"
            binding.tvStatInterviews.text = count.toString()
            com.mrdiy.careers.data.repository.ApplicationsRepository(requireContext()).getApplications().onSuccess { applications ->
                binding.tvStatApplications.text = applications.size.toString()

            }
        }
    }

    private fun populateProfile(profile: UserProfile) {
        val initials = profile.fullName.split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercase() }
            .joinToString("")

        binding.tvAvatarInitials.text = initials.ifEmpty { "?" }
        binding.ivProfilePhoto.visibility = if (profile.photoPath.isBlank()) View.GONE else View.VISIBLE
        if (profile.photoPath.isNotBlank()) com.bumptech.glide.Glide.with(this).load(profile.photoPath).into(binding.ivProfilePhoto)
        binding.tvProfileName.text = profile.fullName.ifEmpty { "Your Name" }

        binding.tvProfileRoleLoc.text = buildString {
            if (profile.desiredPosition.isNotBlank()) append(profile.desiredPosition)
            if (profile.desiredPosition.isNotBlank() && profile.location.isNotBlank()) append(" · ")
            if (profile.location.isNotBlank()) append(profile.location)
        }

        binding.sectionPersonalInfo.tvFullName.text = profile.fullName.ifEmpty { "Your Name" }
        binding.sectionPersonalInfo.tvEmail.text = profile.email.ifEmpty { "Email not set" }
        binding.sectionPersonalInfo.tvPhone.text = profile.phone.ifEmpty { "Phone not set" }
        binding.sectionPersonalInfo.tvLocation.text = profile.location.ifEmpty { "Location not set" }

        binding.tvAboutText.text = profile.about.ifEmpty { "No about section added yet." }

        binding.skillsChipGroup.removeAllViews()
        val skillList = profile.skills.filter { it.isNotEmpty() }
        if (skillList.isEmpty()) {
            val emptyChip = Chip(requireContext()).apply {
                text = "No skills added"
                isClickable = false
                isFocusable = false
            }
            binding.skillsChipGroup.addView(emptyChip)
        } else {
            skillList.forEach { skill ->
                val chip = Chip(requireContext()).apply {
                    text = skill
                    isClickable = false
                    isFocusable = false
                    setChipBackgroundColorResource(R.color.orange_light)
                    setTextColor(resources.getColor(R.color.orange_dark, null))
                    chipStrokeWidth = 0f
                }
                binding.skillsChipGroup.addView(chip)
            }
        }

        val hasResume = profile.resumeUrl.isNotBlank()
        binding.tvResumeStatus.text = if (hasResume) profile.resumeName else "No resume uploaded yet"
        binding.btnViewResume.visibility = if (hasResume) View.VISIBLE else View.GONE
        binding.btnUploadResume.text = if (hasResume) "Replace Resume" else "Upload Resume"

        binding.btnViewResume.setOnClickListener {
            if (hasResume) viewResumePdf(profile.resumeUrl)
        }
        binding.btnUploadResume.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_resumeUpload)
        }

        binding.experienceContainer.removeAllViews()
        val emptyExp = TextView(requireContext()).apply {
            text = "No work experience added yet."
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_secondary, null))
        }
        if (profile.workExperiences.isEmpty()) binding.experienceContainer.addView(emptyExp)
        profile.workExperiences.forEach { experience ->
            val row = layoutInflater.inflate(R.layout.item_experience_row, binding.experienceContainer, false)
            row.findViewById<TextView>(R.id.tv_exp_initials).text = experience.initials
            row.findViewById<TextView>(R.id.tv_exp_role).text = experience.role
            row.findViewById<TextView>(R.id.tv_exp_company).text = experience.company
            row.findViewById<TextView>(R.id.tv_exp_period).text = experience.displayPeriod
            row.findViewById<View>(R.id.btn_delete_exp).visibility = View.GONE
            binding.experienceContainer.addView(row)
        }

        updateSavedJobsCount()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
