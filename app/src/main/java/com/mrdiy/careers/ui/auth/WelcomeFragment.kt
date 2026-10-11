package com.mrdiy.careers.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.mrdiy.careers.R
import com.mrdiy.careers.data.auth.AccountRules
import com.mrdiy.careers.data.auth.AuthManager
import com.mrdiy.careers.data.auth.SupabaseProvider
import com.mrdiy.careers.data.repository.ProfileRepository
import com.mrdiy.careers.data.search.LocationDistance
import com.mrdiy.careers.databinding.FragmentWelcomeBinding
import com.mrdiy.careers.ui.profile.ProfileViewModel
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

/** First-time setup after signing up or signing in with Google: the basics HR and the job matching need. */
class WelcomeFragment : Fragment() {

    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWelcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupFields()
        setupClickListeners()
    }

    private fun setupFields() {
        val context = requireContext()

        // Years of experience
        binding.actvExperience.setAdapter(
            ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, (0..10).map(Int::toString))
        )

        // Places we know, so the distance to a branch can always be worked out.
        binding.etLocation.setAdapter(
            ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, LocationDistance.suggestions())
        )

        // Positions at MR.DIY (any other position can still be typed).
        binding.etPosition.setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, POSITIONS))

        // Clear an error as soon as the applicant starts fixing it.
        listOf(
            binding.etFullName to binding.tilFullName,
            binding.etPhone to binding.tilPhone,
            binding.etLocation to binding.tilLocation,
            binding.etPosition to binding.tilPosition,
            binding.actvExperience to binding.tilExperience
        ).forEach { (field, layout) ->
            field.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { layout.error = null }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }

        val auth = AuthManager(context)
        val cached = ProfileRepository(context).loadFromPrefs()
        val registered = cached.fullName.ifBlank { auth.getCurrentUserName() }
        if (binding.etFullName.text.isNullOrBlank()) binding.etFullName.setText(properName(registered))
        if (binding.etPhone.text.isNullOrBlank() && cached.phone.isNotBlank()) binding.etPhone.setText(cached.phone)
    }

    /** Google and some sign-ups give names in lower case ("khenard vince"); show them as names. */
    private fun properName(name: String): String =
        if (name == name.lowercase() || name == name.uppercase()) {
            name.trim().split(Regex("\\s+")).joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }
        } else name

    private fun setupClickListeners() {
        binding.btnContinue.setOnClickListener {
            if (!binding.btnContinue.isEnabled) return@setOnClickListener

            val fullName = binding.etFullName.text?.trim().toString()
            val phoneInput = binding.etPhone.text?.trim().toString()
            val location = binding.etLocation.text?.trim().toString()
            val position = binding.etPosition.text?.trim().toString()
            val experience = binding.actvExperience.text?.trim().toString()
            val skills = binding.etSkills.text?.toString().orEmpty()
                .split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

            // Check everything and show every problem at once, starting from the first field.
            var firstProblem: View? = null
            fun problem(layout: com.google.android.material.textfield.TextInputLayout, message: String) {
                layout.error = message
                if (firstProblem == null) firstProblem = layout
            }

            if (fullName.length < 2) problem(binding.tilFullName, "Please enter your full name")

            val phone = AccountRules.phoneNumber(phoneInput)
            if (phoneInput.isEmpty()) problem(binding.tilPhone, "Please enter your mobile number")
            else if (phone == null) problem(binding.tilPhone, "Enter a valid mobile number, for example 09171234567")

            if (location.isEmpty()) problem(binding.tilLocation, "Please enter your city")
            else if (LocationDistance.locate(location) == null) {
                problem(binding.tilLocation, "We don't know that place. Pick your city from the list, for example Taytay, Rizal")
            }

            if (position.isEmpty()) problem(binding.tilPosition, "Please enter your desired position")
            if (AccountRules.experience(experience) == null) problem(binding.tilExperience, "Please select your years of experience")

            firstProblem?.let { view ->
                view.parent?.requestChildFocus(view, view)
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                val user = SupabaseProvider.client.auth.currentUserOrNull()
                val email = user?.email.orEmpty()
                val uid = AuthManager(requireContext()).getCurrentUserId().orEmpty()
                if (uid.isEmpty()) {
                    Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                saveUserProfile(fullName, phone.orEmpty(), location, position, experience, skills, uid, email)
            }
        }
    }

    private fun saveUserProfile(
        fullName: String, phone: String, location: String, position: String, experience: String,
        skills: List<String>, userId: String, email: String
    ) {
        binding.btnContinue.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        val profile = viewModel.currentProfile().copy(
            id = userId,
            fullName = fullName,
            email = email,
            phone = phone,
            location = location,
            desiredPosition = position,
            skills = (viewModel.currentProfile().skills + skills).distinctBy { it.lowercase() },
            yearsOfExperience = AccountRules.experience(experience) ?: 0
        )

        viewModel.saveProfile(profile) { success ->
            if (_binding == null || !isAdded) return@saveProfile
            binding.progressBar.visibility = View.GONE
            binding.btnContinue.isEnabled = true

            if (success == true) {
                val pending = ProfileRepository(requireContext()).isProfileSyncPending(userId)
                Toast.makeText(
                    requireContext(),
                    if (pending) "Profile saved on this device. We'll sync it when connected." else "Profile saved!",
                    Toast.LENGTH_SHORT
                ).show()
                navigateToHome()
            } else {
                Toast.makeText(requireContext(), "Failed to save profile. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToHome() {
        findNavController().navigate(R.id.action_welcomeFragment_to_homeFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        val POSITIONS = listOf(
            "Cashier", "Sales Associate", "Stock Clerk", "Customer Service Representative",
            "Visual Merchandiser", "Inventory Analyst", "Assistant Store Supervisor", "Store Supervisor",
            "Store Manager", "Warehouse Operations Lead", "HR Recruitment Officer", "Security Guard",
            "Utility / Maintenance Staff"
        )
    }
}
