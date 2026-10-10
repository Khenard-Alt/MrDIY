package com.mrdiy.careers.ui.resume

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.mrdiy.careers.R
import com.mrdiy.careers.databinding.FragmentResumeUploadBinding

/**
 * Lets the user pick a PDF, DOCX, or TXT resume, parses it with AI, and shows
 * the extracted skills/experience before saving to their profile.
 */
class ResumeUploadFragment : Fragment() {

    private var _binding: FragmentResumeUploadBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ResumeUploadViewModel by viewModels()

    // File picker — accepts PDF and plain text only
    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        if (_binding == null) return@registerForActivityResult
        val fileName = getFileName(uri)

        // Get MIME type
        val mimeType = requireContext().contentResolver.getType(uri) ?: ""

        // Check file extension as backup
        val lowerFileName = fileName.lowercase()
        val isSupportedType = mimeType == "application/pdf" ||
                          mimeType == "text/plain" ||
                          mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                          lowerFileName.endsWith(".pdf") ||
                          lowerFileName.endsWith(".docx") ||
                          lowerFileName.endsWith(".txt")

        if (!isSupportedType) {
            binding.tvErrorMessage.text = "Please select a PDF, DOCX, or text file (.pdf, .docx, .txt)"
            binding.errorCard.visibility = View.VISIBLE
            binding.resultCard.visibility = View.GONE
            binding.progressGroup.visibility = View.GONE
            return@registerForActivityResult
        }

        // Hide error card when new file is selected
        binding.errorCard.visibility = View.GONE

        // Take persistable permission so we can read the file later
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Permission failed, but we might still be able to read the file
        }

        viewModel.processResume(uri, fileName)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResumeUploadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { viewModel.cancelUpload(); findNavController().navigateUp() }

        binding.btnPickFile.setOnClickListener {
            // Accept PDF, DOCX, and plain text files only
            if (viewModel.busy) viewModel.cancelUpload()
            else pickFileLauncher.launch(arrayOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain"
            ))
        }

        binding.btnViewRecommendations.setOnClickListener {
            findNavController().navigate(R.id.action_resumeUpload_to_recommendations)
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            binding.btnPickFile.text = if (state is ResumeUploadState.FileSelected || state is ResumeUploadState.ExtractingText || state is ResumeUploadState.UploadingFile) "Cancel upload" else "Choose File"
            // Reset visibility
            binding.progressGroup.visibility = View.GONE
            binding.resultCard.visibility = View.GONE
            binding.errorCard.visibility = View.GONE
            binding.btnViewRecommendations.visibility = View.GONE

            when (state) {
                is ResumeUploadState.FileSelected -> {
                    binding.tvFileName.text = state.fileName
                }
                is ResumeUploadState.ExtractingText -> {
                    binding.progressGroup.visibility = View.VISIBLE
                    binding.tvProgressMessage.text = "Reading your resume…"
                    binding.progressBar.isIndeterminate = true
                }
                is ResumeUploadState.UploadingFile, is ResumeUploadState.ParsingWithAI -> {
                    binding.progressGroup.visibility = View.VISIBLE
                    binding.tvProgressMessage.text = "Uploading your resume…"
                }
                is ResumeUploadState.ParseSuccess -> {
                    binding.resultCard.visibility = View.VISIBLE
                    binding.btnViewRecommendations.visibility = View.VISIBLE

                    val result = state.result
                    binding.tvParsedName.text = state.profile.fullName.ifBlank { "Profile updated" }
                    binding.tvParsedExp.text = "Review your experience in Edit Profile"
                    binding.tvParsedSummary.text = result.summary

                    // Show skills as chips
                    binding.chipGroupParsedSkills.removeAllViews()
                    result.skills.take(10).forEach { skill ->
                        val chip = Chip(requireContext()).apply {
                            text = skill
                            isClickable = false
                        }
                        binding.chipGroupParsedSkills.addView(chip)
                    }

                    // Show education
                    binding.tvParsedEducation.text = result.education
                        .joinToString("\n") { "• ${it.degree} — ${it.school} (${it.year})" }

                    // Show work experience
                    binding.tvParsedExperience.text = result.workExperiences
                        .joinToString("\n") { "• ${it.role} at ${it.company} (${it.displayPeriod})" }
                }
                is ResumeUploadState.Error -> {
                    binding.errorCard.visibility = View.VISIBLE
                    binding.tvErrorMessage.text = state.message
                }
                else -> { /* Idle */ }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "resume"
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) name = cursor.getString(idx)
        }
        return name
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
