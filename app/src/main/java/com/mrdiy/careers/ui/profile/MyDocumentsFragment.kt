package com.mrdiy.careers.ui.profile

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.mrdiy.careers.R
import com.mrdiy.careers.data.repository.ApplicantDocumentRow
import com.mrdiy.careers.data.repository.ApplicantDocumentsRepository
import com.mrdiy.careers.databinding.FragmentMyDocumentsBinding
import kotlinx.coroutines.launch

/**
 * Profile-level document checklist (Valid ID + pre-employment requirements), uploadable
 * any time from the Profile tab — not tied to one job application. ADMIN_MAJOR_PLAN.md §O/R4.
 */
class MyDocumentsFragment : Fragment() {

    private var _binding: FragmentMyDocumentsBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: ApplicantDocumentsRepository
    private var pendingDocumentName: String? = null

    private val pickDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val name = pendingDocumentName
        if (uri != null && name != null) upload(name, uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyDocumentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = ApplicantDocumentsRepository(requireContext())
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        load()
    }

    private fun load() {
        binding.progressBar.visibility = View.VISIBLE
        binding.scrollView.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            val result = repository.myDocuments()
            if (_binding == null) return@launch
            binding.progressBar.visibility = View.GONE
            binding.scrollView.visibility = View.VISIBLE
            result.onSuccess { rows -> render(rows) }
            result.onFailure {
                Toast.makeText(requireContext(), "Could not load your documents.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun render(rows: List<ApplicantDocumentRow>) {
        val container = binding.documentsContainer
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        rows.forEach { row ->
            val item = inflater.inflate(R.layout.item_applicant_document, container, false)
            val title = item.findViewById<TextView>(R.id.tv_document_name)
            val status = item.findViewById<TextView>(R.id.tv_document_status)
            val uploadBtn = item.findViewById<MaterialButton>(R.id.btn_upload_document)

            title.text = row.document_name

            val (label, tint) = when (row.status) {
                "Verified" -> "Verified" to "#157A3D"
                "Uploaded" -> "Uploaded, waiting for HR" to "#1D4ED8"
                "Rejected" -> ("Returned" + (row.remarks?.let { ": $it" } ?: "")) to "#B4141B"
                else -> "Not submitted yet" to "#6B6B63"
            }
            status.text = label
            status.setTextColor(Color.parseColor(tint))

            uploadBtn.text = if (row.status == "Pending") "Upload" else "Replace"
            uploadBtn.isEnabled = row.status != "Verified"
            uploadBtn.setOnClickListener {
                pendingDocumentName = row.document_name
                pickDocument.launch(arrayOf("application/pdf", "image/jpeg", "image/png"))
            }

            container.addView(item)
        }
    }

    private fun upload(documentName: String, uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val error = repository.uploadDocument(documentName, uri)
            if (_binding == null) return@launch
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Uploaded.", Toast.LENGTH_SHORT).show()
                load()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
