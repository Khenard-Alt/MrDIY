package com.mrdiy.careers.ui.auth

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.mrdiy.careers.R
import com.mrdiy.careers.data.auth.AccountRules
import com.mrdiy.careers.data.auth.AuthManager
import com.mrdiy.careers.databinding.FragmentRegisterBinding

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private lateinit var authManager: AuthManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authManager = AuthManager(requireContext())
        FormValidation.bind(binding.tilEmail) { if (AccountRules.email(it)) null else "Enter a valid email" }
        FormValidation.bind(binding.tilPassword) { if (AccountRules.password(it)) null else "Password must be at least 6 characters" }
        FormValidation.bind(binding.tilPhone) { if (AccountRules.phone(it)) null else "Enter a valid phone number" }
        binding.cardFacebook.visibility = View.GONE
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.ivBack.setOnClickListener      { findNavController().navigateUp() }
        binding.tvLogin.setOnClickListener     { findNavController().navigateUp() }
        binding.btnRegister.setOnClickListener { registerUser() }

        binding.cardGoogle.setOnClickListener {
            if (!binding.cbTerms.isChecked) {
                Toast.makeText(requireContext(), "Please agree to the Terms of Service and Privacy Policy", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            authManager.signInWithGoogle { _, message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
        binding.cardFacebook.setOnClickListener {
            Toast.makeText(requireContext(), "Use Google Sign-In", Toast.LENGTH_SHORT).show()
        }
    }

    private fun registerUser() {
        if (!binding.btnRegister.isEnabled) return
        if (!binding.cbTerms.isChecked) {
            binding.cbTerms.error = "Please agree to the Terms of Service and Privacy Policy"
            Toast.makeText(requireContext(), "Please agree to the Terms of Service and Privacy Policy", Toast.LENGTH_LONG).show()
            return
        }
        binding.cbTerms.error = null
        val fullName        = binding.etFullName.text?.trim().toString()
        val email           = binding.etEmail.text?.trim().toString()
        val phone           = binding.etPhone.text?.trim().toString()
        val password        = binding.etPassword.text?.toString().orEmpty()
        val confirmPassword = binding.etConfirmPassword.text?.toString().orEmpty()

        if (!validateInput(fullName, email, phone, password, confirmPassword)) return

        if (!binding.cbTerms.isChecked) {
            Toast.makeText(requireContext(), "Please agree to the Terms of Service", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.isVisible = true
        binding.btnRegister.isEnabled = false

        authManager.register(fullName, email, password, binding.cbTerms.isChecked, phone) { success, message ->
                    if (!isAdded || _binding == null) return@register
            binding.progressBar.isVisible = false
            binding.btnRegister.isEnabled = true

            if (success) {
                // ── Navigate to the email verification screen ──────────────────
                // Safe Args: pass the email so the screen can display it and
                // use it for the "Resend" button.
                val action = RegisterFragmentDirections
                    .actionRegisterFragmentToEmailVerification(email = email)
                findNavController().navigate(action)
            } else {
                // Real failure (e.g. email already registered, network error)
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun validateInput(
        fullName:        String,
        email:           String,
        phone:           String,
        password:        String,
        confirmPassword: String
    ): Boolean {
        var isValid = true

        if (fullName.isEmpty()) {
            binding.tilFullName.error = "Full name is required"; isValid = false
        } else { binding.tilFullName.error = null }

        if (email.isEmpty()) {
            binding.tilEmail.error = "Email is required"; isValid = false
        } else if (!AccountRules.email(email)) {
            binding.tilEmail.error = "Enter a valid email"; isValid = false
        } else { binding.tilEmail.error = null }

        if (phone.isEmpty()) {
            binding.tilPhone.error = "Phone number is required"; isValid = false
        } else if (!AccountRules.phone(phone)) {
            binding.tilPhone.error = "Enter a valid phone number"; isValid = false
        } else { binding.tilPhone.error = null }

        if (password.isEmpty()) {
            binding.tilPassword.error = "Password is required"; isValid = false
        } else if (password.length < 6) {
            binding.tilPassword.error = "Password must be at least 6 characters"; isValid = false
        } else { binding.tilPassword.error = null }

        if (confirmPassword.isEmpty()) {
            binding.tilConfirmPassword.error = "Please confirm your password"; isValid = false
        } else if (confirmPassword != password) {
            binding.tilConfirmPassword.error = "Passwords do not match"; isValid = false
        } else { binding.tilConfirmPassword.error = null }

        return isValid
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
