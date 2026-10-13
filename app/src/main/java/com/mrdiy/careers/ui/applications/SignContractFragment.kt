package com.mrdiy.careers.ui.applications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.mrdiy.careers.data.repository.RecruitmentRepository
import com.mrdiy.careers.databinding.FragmentSignContractBinding
import kotlinx.coroutines.launch

/**
 * In-app digital signature for the "Signed Employment Contract" pre-employment requirement,
 * replacing the old print → sign on paper → photograph → upload flow (ADMIN_MAJOR_PLAN.md §R,
 * decided 2026-10-13). Requires scrolling the contract to the end before the pad unlocks —
 * same "I agree" pattern used for the branch manager's countersignature on the admin web.
 * Writes through RecruitmentRepository.uploadRequirementSignature(), so it plugs into the
 * exact same pre_employment_requirements row/status the file-upload path already uses.
 */
class SignContractFragment : Fragment() {

    private var _binding: FragmentSignContractBinding? = null
    private val binding get() = _binding!!

    private lateinit var requirementId: String
    private var jobTitle: String = "this position"
    private var applicantName: String = "the employee"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignContractBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requirementId = requireArguments().getString(ARG_REQUIREMENT_ID).orEmpty()
        jobTitle = requireArguments().getString(ARG_JOB_TITLE)?.takeIf { it.isNotBlank() } ?: jobTitle
        applicantName = requireArguments().getString(ARG_APPLICANT_NAME)?.takeIf { it.isNotBlank() } ?: applicantName

        binding.tvContractText.text = contractText()
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        binding.contractScroll.setOnScrollChangeListener { v, _, scrollY, _, _ ->
            val view = v as androidx.core.widget.NestedScrollView
            val child = view.getChildAt(0)
            val reachedBottom = scrollY >= (child.height - view.height - 16)
            if (reachedBottom) unlockSignaturePad()
        }

        binding.signaturePad.onSignatureStarted = {
            binding.btnSubmit.isEnabled = true
        }

        binding.btnClear.setOnClickListener {
            binding.signaturePad.clear()
            binding.btnSubmit.isEnabled = false
        }

        binding.btnSubmit.setOnClickListener { submit() }
    }

    private fun unlockSignaturePad() {
        binding.signaturePad.isEnabled = true
        binding.tvScrollHint.visibility = View.GONE
    }

    private fun contractText(): String = """
        EMPLOYMENT CONTRACT

        This Employment Contract is entered into between MR.DIY Philippines and:

        Employee: $applicantName
        Position: $jobTitle

        1. The Employee agrees to perform the duties of the above position, under the supervision of the branch management, for the duration stated in the official offer.

        2. Compensation, benefits, work schedule and leave entitlements follow the applicable minimum wage order and MR.DIY company policy in effect at the branch.

        3. If this is a fixed-term engagement, nothing in this contract is intended to circumvent the Employee's security of tenure or regularization rights under the Labor Code of the Philippines.

        4. Either party may raise concerns about this engagement to MR.DIY Human Resources at any time.

        5. By signing below, the Employee confirms this contract has been read and the terms stated above are accepted.

        — End of contract text —
    """.trimIndent()

    private fun submit() {
        val png = binding.signaturePad.exportPng()
        if (png == null) {
            Toast.makeText(requireContext(), "Please sign first.", Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnSubmit.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val error = RecruitmentRepository(requireContext()).uploadRequirementSignature(requirementId, png)
            if (_binding == null) return@launch
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                binding.btnSubmit.isEnabled = true
            } else {
                Toast.makeText(requireContext(), "Signed. HR will verify it.", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_REQUIREMENT_ID = "requirementId"
        const val ARG_JOB_TITLE = "jobTitle"
        const val ARG_APPLICANT_NAME = "applicantName"
    }
}
