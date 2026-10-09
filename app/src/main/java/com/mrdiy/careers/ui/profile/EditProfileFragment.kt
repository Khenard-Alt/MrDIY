package com.mrdiy.careers.ui.profile

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.util.PatternsCompat
import com.mrdiy.careers.data.auth.AccountRules
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.mrdiy.careers.R
import com.mrdiy.careers.databinding.FragmentEditProfileBinding
import com.mrdiy.careers.model.UserProfile
import com.mrdiy.careers.model.WorkExperience
import java.io.File
import java.io.FileOutputStream

class EditProfileFragment : Fragment() {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by activityViewModels()

    // Local mutable copies edited before final save
    private val currentSkills = mutableListOf<String>()
    private val currentExperiences = mutableListOf<WorkExperience>()
    private var currentPhotoPath = ""

    // ── Photo picker ──────────────────────────────────────────────────────────
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { copyPhotoToInternal(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observe profile - only populate when data is available
        viewModel.profile.observe(viewLifecycleOwner) { profile ->
            populateFields(profile)
        }

        loadAvatarPhoto()

        // Toolbar back
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        // Photo button
        binding.btnChangePhoto.setOnClickListener { pickPhoto.launch("image/*") }

        // Add skill on button click
        binding.btnAddSkill.setOnClickListener { submitSkillInput() }

        // Add skill on keyboard "Done"
        binding.etSkillInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submitSkillInput(); true } else false
        }

        // Add experience
        binding.btnAddExperienceEdit.setOnClickListener {
            AddExperienceBottomSheet.show(childFragmentManager, null) { exp ->
                currentExperiences.add(exp)
                renderExperienceList()
            }
        }

        // Save
        binding.btnSaveChanges.setOnClickListener {
            if (validateInputs()) saveProfile()
        }
    }

    private fun populateFields(profile: UserProfile) {
        // Pre-fill text fields
        binding.etFirstName.setText(profile.firstName)
        binding.etLastName.setText(profile.lastName)
        binding.etEmail.setText(profile.email)
        binding.etPhone.setText(profile.phone)
        binding.etLocation.setText(profile.location)
        binding.etDesiredPosition.setText(profile.desiredPosition)
        binding.etAbout.setText(profile.about)
        currentSkills.clear()
        currentSkills.addAll(profile.skills)
        currentExperiences.clear()
        currentExperiences.addAll(profile.workExperiences)
        currentPhotoPath = profile.photoPath

        // Render dynamic sections
        renderSkillChips()
        renderExperienceList()
        loadAvatarPhoto()
    }

    // ── Photo ─────────────────────────────────────────────────────────────────

    private fun loadAvatarPhoto() {
        val file = if (currentPhotoPath.isNotEmpty()) File(currentPhotoPath) else null
        if (file != null && file.exists()) {
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            if (bmp != null) {
                binding.tvAvatarInitialsEdit.visibility = View.INVISIBLE
                binding.ivProfilePhotoEdit.setImageBitmap(bmp)
                binding.ivProfilePhotoEdit.visibility = View.VISIBLE
                return
            }
        }
        binding.ivProfilePhotoEdit.visibility   = View.GONE
        binding.tvAvatarInitialsEdit.visibility = View.VISIBLE
        binding.tvAvatarInitialsEdit.text       = viewModel.currentProfile().initials
    }

    private fun copyPhotoToInternal(uri: Uri) {
        try {
            val resolver: ContentResolver = requireContext().contentResolver
            val dest = File(requireContext().filesDir, "profile_photo.jpg")
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            currentPhotoPath = dest.absolutePath

            val bmp = BitmapFactory.decodeFile(dest.absolutePath)
            if (bmp != null) {
                binding.tvAvatarInitialsEdit.visibility = View.INVISIBLE
                binding.ivProfilePhotoEdit.setImageBitmap(bmp)
                binding.ivProfilePhotoEdit.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Could not load photo", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Skills ────────────────────────────────────────────────────────────────

    private fun submitSkillInput() {
        val text = binding.etSkillInput.text?.trim().toString()
        if (text.isEmpty()) return
        if (currentSkills.any { it.equals(text, ignoreCase = true) }) {
            binding.etSkillInput.error = "Skill already added"
            return
        }
        currentSkills.add(text)
        binding.etSkillInput.text?.clear()
        binding.etSkillInput.error = null
        renderSkillChips()
    }

    private fun renderSkillChips() {
        binding.skillsEditChipGroup.removeAllViews()
        currentSkills.toList().forEach { skill ->
            val chip = Chip(requireContext()).apply {
                text               = skill
                isCloseIconVisible = true
                isClickable        = false
                isFocusable        = false
                setChipBackgroundColorResource(R.color.orange_light)
                setTextColor(resources.getColor(R.color.orange_dark, null))
                setCloseIconTintResource(R.color.orange_primary)
                chipStrokeWidth = 0f
                setOnCloseIconClickListener {
                    currentSkills.remove(skill)
                    binding.skillsEditChipGroup.removeView(this)
                }
            }
            binding.skillsEditChipGroup.addView(chip)
        }
    }

    // ── Work Experience ───────────────────────────────────────────────────────

    private fun renderExperienceList() {
        val container = binding.experienceEditContainer
        container.removeAllViews()

        if (currentExperiences.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text     = "No experience added yet."
                textSize = 12f
                setTextColor(resources.getColor(R.color.text_muted, null))
            }
            container.addView(empty)
            return
        }

        currentExperiences.toList().forEachIndexed { index, exp ->
            val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_experience_row, container, false)

            row.findViewById<TextView>(R.id.tv_exp_initials).text = exp.initials
            row.findViewById<TextView>(R.id.tv_exp_role).text     = exp.role
            row.findViewById<TextView>(R.id.tv_exp_company).text  = exp.company
            row.findViewById<TextView>(R.id.tv_exp_period).text   = exp.displayPeriod

            // Tap row to edit
            row.setOnClickListener {
                AddExperienceBottomSheet.show(childFragmentManager, exp) { updated ->
                    currentExperiences[currentExperiences.indexOfFirst { it.id == exp.id }] = updated
                    renderExperienceList()
                }
            }

            // Delete button
            row.findViewById<ImageButton>(R.id.btn_delete_exp).apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    currentExperiences.removeAt(index)
                    renderExperienceList()
                }
            }

            container.addView(row)

            if (index < currentExperiences.lastIndex) {
                val divider = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).also { it.setMargins(0, 10, 0, 10) }
                    setBackgroundColor(resources.getColor(R.color.border_light, null))
                }
                container.addView(divider)
            }
        }
    }

    // ── Validation & Save ─────────────────────────────────────────────────────

    private fun validateInputs(): Boolean {
        var valid = true

        fun check(view: com.google.android.material.textfield.TextInputEditText, msg: String, condition: Boolean = true): Boolean {
            return if (view.text.isNullOrBlank() || !condition) {
                view.error = msg; if (valid) view.requestFocus(); valid = false; false
            } else { view.error = null; true }
        }

        check(binding.etFirstName, "First name is required")
        check(binding.etLastName,  "Last name is required")

        val email = binding.etEmail.text?.trim().toString()
        if (email.isEmpty()) {
            check(binding.etEmail, "Email is required")
        } else if (!AccountRules.email(email)) {
            binding.etEmail.error = "Enter a valid email address"
            if (valid) binding.etEmail.requestFocus()
            valid = false
        } else {
            binding.etEmail.error = null
        }

        check(binding.etPhone, "Phone number is required")

        return valid
    }

    // EditProfileFragment.kt — saveProfile()
    private fun saveProfile() {
        val existing = viewModel.currentProfile()
        val updated = existing.copy(           // ← use copy() to preserve ALL existing fields
            firstName       = binding.etFirstName.text?.trim().toString(),
            lastName        = binding.etLastName.text?.trim().toString(),
            fullName        = "",              // toRow() will rebuild from first+last
            email           = binding.etEmail.text?.trim().toString(),
            phone           = binding.etPhone.text?.trim().toString(),
            location        = binding.etLocation.text?.trim().toString(),
            headline        = binding.etHeadline.text?.trim().toString(),
            desiredPosition = binding.etDesiredPosition.text?.trim().toString(),
            about           = binding.etAbout.text?.trim().toString(),
            skills          = currentSkills.toList(),
            workExperiences = currentExperiences.toList(),
            photoPath       = currentPhotoPath
        )
        viewModel.saveProfile(updated) { success ->
            if (!isAdded || _binding == null) return@saveProfile
            if (success) {
                findNavController().navigateUp()   // navigate only after save is done
            } else {
                Toast.makeText(requireContext(), "Save failed, try again", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}