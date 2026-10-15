package com.mrdiy.careers.ui.jobs

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mrdiy.careers.R
import com.mrdiy.careers.data.SafeDiagnostics
import com.mrdiy.careers.databinding.DialogFaqChatBinding
import com.mrdiy.careers.network.AdminRetrofitClient
import com.mrdiy.careers.network.FaqChatRequest
import kotlinx.coroutines.launch

/**
 * §6.3 Conversational FAQ assistant — a grounded, job-specific Q&A sheet, not a general
 * chatbot. Each question is answered fresh server-side using only that job's own content
 * (see mrdiy-admin's lib/faqAssistant.ts); there is no multi-turn memory kept here or on
 * the server, which keeps the "grounded, not open chat" framing honest and simple.
 */
class FaqChatBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: DialogFaqChatBinding? = null
    private val binding get() = _binding!!

    private var jobId: String = ""
    private var jobTitle: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFaqChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        jobId = arguments?.getString(ARG_JOB_ID) ?: ""
        jobTitle = arguments?.getString(ARG_JOB_TITLE) ?: ""
        binding.tvFaqSubtitle.text = "About: $jobTitle"

        binding.btnFaqSend.setOnClickListener { sendQuestion() }
    }

    private fun sendQuestion() {
        val question = binding.etFaqQuestion.text?.trim()?.toString().orEmpty()
        if (question.isEmpty() || jobId.isEmpty()) return

        addMessageRow(question, isQuestion = true)
        binding.etFaqQuestion.setText("")
        binding.faqProgress.isVisible = true
        binding.btnFaqSend.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val answerText = try {
                val response = AdminRetrofitClient.faqApiService.askQuestion(FaqChatRequest(jobId, question))
                when {
                    response.ok && !response.answer.isNullOrBlank() -> response.answer
                    response.reason == "rate-limited" ->
                        "You've asked a lot of questions — please wait a few minutes and try again."
                    response.reason == "out-of-scope" ->
                        "I can only answer questions about this job posting or the application process."
                    else -> "Couldn't answer that right now — please contact HR if you need help."
                }
            } catch (e: Exception) {
                SafeDiagnostics.record("request", e)
                "Couldn't reach the assistant right now — please check your connection and try again."
            }

            if (!isAdded || _binding == null) return@launch
            binding.faqProgress.isVisible = false
            binding.btnFaqSend.isEnabled = true
            addMessageRow(answerText, isQuestion = false)
        }
    }

    private fun addMessageRow(text: String, isQuestion: Boolean) {
        val row = TextView(requireContext()).apply {
            this.text = text
            textSize = 13f
            setTypeface(typeface, if (isQuestion) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isQuestion) R.color.text_primary else R.color.text_secondary
                )
            )
            setPadding(0, 8, 0, 8)
        }
        binding.faqMessagesContainer.addView(row)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "FaqChatBottomSheet"
        private const val ARG_JOB_ID = "jobId"
        private const val ARG_JOB_TITLE = "jobTitle"

        fun newInstance(jobId: String, jobTitle: String) = FaqChatBottomSheetFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_JOB_ID, jobId)
                putString(ARG_JOB_TITLE, jobTitle)
            }
        }
    }
}
