package com.mrdiy.careers.ui.applications

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.mrdiy.careers.R
import com.mrdiy.careers.data.ml.HiringVelocity
import com.mrdiy.careers.data.repository.ApplicationsRepository
import com.mrdiy.careers.data.repository.ProfileRepository
import com.mrdiy.careers.data.repository.RecruitmentRepository
import com.mrdiy.careers.databinding.FragmentApplicationDetailBinding
import com.mrdiy.careers.ui.base.DateText
import kotlinx.coroutines.launch

/**
 * One application from the applicant's side: the steps from applying until employment (timeline),
 * the interview HR scheduled (with Confirm / Can't attend), how soon they may start, and, once hired,
 * the pre-employment documents to upload.
 */
/**
 * Days to follow up each pre-employment document within, counted from when the checklist is created
 * (the applicant was Hired) — mirrors REQUIREMENT_DUE_DAYS in the admin's lib/pipeline.ts. Common-practice
 * ranges from research (ADMIN_MAJOR_PLAN.md §T), not a single statute: SSS/PhilHealth/Pag-IBIG are the
 * employer's own 30-day registration window; BIR Form 1902/TIN has a stricter 10-day deadline; the rest
 * have no statutory deadline found, so 7 days is used as a reasonable follow-up window.
 */
private val REQUIREMENT_DUE_DAYS = mapOf(
    "SSS Number / E-1 Form" to 30,
    "PhilHealth Member Data Record (MDR)" to 30,
    "Pag-IBIG MID Number / Member Data Form" to 30,
    "NBI Clearance / Police Clearance" to 7,
    "Pre-Employment Medical Exam / Health Permit" to 7,
    "BIR Form 1902 / TIN" to 10,
    "Signed Employment Contract" to 7
)

class ApplicationDetailFragment : Fragment() {

    private var _binding: FragmentApplicationDetailBinding? = null
    private val binding get() = _binding!!

    private val applicationId: String by lazy { requireArguments().getString(ARG_APPLICATION_ID).orEmpty() }
    private var pendingRequirementId: String? = null
    private var currentJobTitle: String = ""
    private var currentApplicantName: String = ""

    private val pickDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val requirementId = pendingRequirementId
        pendingRequirementId = null
        if (uri != null && requirementId != null) uploadDocument(requirementId, uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentApplicationDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            val context = requireContext()
            val application = ApplicationsRepository(context).getApplications().getOrNull()
                ?.firstOrNull { it.id == applicationId }
            if (_binding == null) return@launch
            binding.progress.visibility = View.GONE
            if (application == null) {
                binding.content.removeAllViews()
                binding.content.addView(body("We could not load this application. Please go back and try again."))
                return@launch
            }

            val recruitment = RecruitmentRepository(context)
            val interview = recruitment.interviewFor(applicationId).getOrNull()
            val requirements = recruitment.requirementsFor(applicationId).getOrNull().orEmpty()
            val profile = ProfileRepository(context).loadFromPrefs()
            if (_binding == null) return@launch

            currentJobTitle = application.job_title
            currentApplicantName = profile.fullName

            val profileComplete = profile.fullName.isNotBlank() && profile.phone.isNotBlank() &&
                profile.location.isNotBlank() && profile.desiredPosition.isNotBlank() &&
                profile.skills.isNotEmpty() && profile.resumeUrl.isNotBlank()
            val velocity = HiringVelocity.estimate(
                matchScore = application.match_score,
                profileComplete = profileComplete,
                documentsVerified = requirements.count { it.status == "Verified" },
                documentsTotal = requirements.size
            )
            render(application, interview, requirements, velocity)
        }
    }

    // ------------------------------------------------------------------ screen

    private fun render(
        application: ApplicationsRepository.ApplicationRow,
        interview: RecruitmentRepository.InterviewRow?,
        requirements: List<RecruitmentRepository.RequirementRow>,
        velocity: HiringVelocity.Estimate
    ) {
        val content = binding.content
        content.removeAllViews()
        val status = DateText.normalizeStatus(application.status)

        content.addView(title(application.job_title))
        if (application.applied_at.isNotBlank()) {
            content.addView(caption("Applied ${DateText.dateTime(application.applied_at)}"))
        }
        content.addView(statusChip(status))

        if (status == "Rejected" || status == "On Hold") {
            content.addView(
                card(
                    if (status == "Rejected") "Application not selected" else "Application on hold",
                    if (status == "Rejected") {
                        "Thank you for applying. We decided to proceed with other candidates this time. You can apply for other jobs anytime."
                    } else {
                        "Your application is on hold for now. This does not mean it was declined. We will update you when there is progress."
                    }
                )
            )
        }

        if (DateText.isEndedEmployment(status)) {
            content.addView(
                card(
                    when (status) {
                        "Resigned" -> "Resignation recorded"
                        "Terminated" -> "Employment ended"
                        else -> "Contract ended"
                    },
                    when (status) {
                        "Resigned" -> "Your resignation has been recorded. See HR for your last working day and the exit checklist. Thank you for your service."
                        "Terminated" -> "Your employment has ended. If you have questions about this decision, please contact MR.DIY Human Resources."
                        else -> "Your contract has ended. Thank you for your service — you're welcome to apply again for future openings."
                    }
                )
            )
        }

        if (application.transfer_status == "pending") {
            content.addView(transferOfferCard(application))
        }

        content.addView(timelineCard(status))

        if (interview != null) content.addView(interviewCard(interview))

        if (status != "Rejected" && !DateText.isEndedEmployment(status)) {
            content.addView(
                card(
                    "How soon can I start?",
                    velocity.label + "\n\n" + velocity.factors.joinToString("\n") { "• $it" }
                )
            )
        }

        if (status == "Hired") content.addView(documentsCard(requirements))
    }

    private fun timelineCard(status: String): View {
        val steps = listOf("Submitted", "Under review", "Shortlisted", "Interview", "Hired")
        val keys = listOf("Pending", "Reviewed", "Shortlisted", "Interview Scheduled", "Hired")
        // Resigned/Terminated/Contract Ended all passed through Hired already — the hiring
        // timeline is fully done, there's just nothing further to track here (see the status
        // chip and the card below for what actually happened).
        val current = if (DateText.isEndedEmployment(status)) {
            keys.lastIndex
        } else {
            // For Rejected / On Hold we only know the application was submitted.
            keys.indexOf(status).takeIf { it >= 0 } ?: 0
        }

        val card = cardContainer()
        card.addView(heading("Where is my application?"))
        steps.forEachIndexed { index, label ->
            val done = index < current || ((status == "Hired" || DateText.isEndedEmployment(status)) && index == current)
            val isCurrent = index == current && !done && status != "Rejected" && status != "On Hold"

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val dot = TextView(requireContext()).apply {
                text = if (done) "✓" else (index + 1).toString()
                gravity = Gravity.CENTER
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (done) Color.WHITE else if (isCurrent) color(R.color.text_primary) else color(R.color.text_muted))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (done) color(R.color.orange_primary) else if (isCurrent) color(R.color.brand_yellow) else Color.parseColor("#ECE9DD"))
                }
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            }
            val text = TextView(requireContext()).apply {
                this.text = label + if (isCurrent) "  ← you are here" else ""
                textSize = 14f
                setTypeface(typeface, if (done || isCurrent) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (done || isCurrent) color(R.color.text_primary) else color(R.color.text_muted))
                setPadding(dp(12), 0, 0, 0)
            }
            row.addView(dot)
            row.addView(text)
            card.addView(row)
        }
        return card
    }

    private fun interviewCard(interview: RecruitmentRepository.InterviewRow): View {
        val card = cardContainer()
        card.addView(heading("Your interview"))

        when (interview.status) {
            "Cancelled" -> {
                card.addView(body("This interview was cancelled. We will contact you with a new schedule."))
                return card
            }
            "Completed" -> {
                card.addView(body("This interview is done: ${DateText.dayAndTime(interview.scheduled_at)}."))
                return card
            }
        }

        card.addView(emphasis(DateText.dayAndTime(interview.scheduled_at)))
        if (interview.isOnline) {
            card.addView(body("Online interview"))
            interview.meeting_link?.takeIf { it.isNotBlank() }?.let { link ->
                card.addView(actionButton("Open meeting link", filled = false) {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                })
            }
        } else {
            card.addView(body("Venue: ${interview.venue}"))
        }
        interview.notes?.takeIf { it.isNotBlank() }?.let { card.addView(body("Notes: $it")) }

        val answer = interview.applicant_response
        card.addView(
            body(
                when (answer) {
                    "Confirmed" -> "You confirmed that you can attend."
                    "Declined" -> "You said you can't attend. HR will contact you for another time."
                    else -> "Please tell HR if you can attend."
                }
            ).apply { setTypeface(typeface, Typeface.BOLD) }
        )

        val buttons = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(actionButton("Confirm", filled = answer != "Confirmed") { respond(interview.id, "Confirmed") }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        })
        buttons.addView(actionButton("Can't attend", filled = false) { respond(interview.id, "Declined") }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        card.addView(buttons)
        return card
    }

    private fun transferOfferCard(application: ApplicationsRepository.ApplicationRow): View {
        val card = cardContainer()
        card.addView(heading("Branch transfer offer"))
        val branchName = application.transfer_offer_branch_name?.takeIf { it.isNotBlank() } ?: "another branch"
        card.addView(body("MR.DIY $branchName would like to consider you for this job. Do you want to proceed?"))

        val buttons = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(actionButton("Accept", filled = true) { respondToTransfer(application.id, true) }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        })
        buttons.addView(actionButton("Decline", filled = false) { respondToTransfer(application.id, false) }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        card.addView(buttons)
        return card
    }

    private fun respondToTransfer(applicationId: String, accept: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = ApplicationsRepository(requireContext()).respondToTransfer(applicationId, accept)
            if (_binding == null) return@launch
            Toast.makeText(
                requireContext(),
                if (ok) "Thank you. HR was told." else "Could not send your answer. Please try again.",
                Toast.LENGTH_SHORT
            ).show()
            load()
        }
    }

    private fun documentsCard(requirements: List<RecruitmentRepository.RequirementRow>): View {
        val card = cardContainer()
        card.addView(heading("Pre-employment documents"))
        if (requirements.isEmpty()) {
            card.addView(body("Your document checklist will appear here shortly. Please check again in a moment."))
            return card
        }
        val verified = requirements.count { it.status == "Verified" }
        card.addView(body("$verified of ${requirements.size} verified. Upload a clear PDF, JPG or PNG (max 5 MB) for each."))

        requirements.forEach { requirement ->
            val box = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }
            box.addView(TextView(requireContext()).apply {
                text = requirement.requirement_name
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(color(R.color.text_primary))
            })
            val (label, tint) = when (requirement.status) {
                "Verified" -> "Verified" to "#157A3D"
                "Uploaded" -> "Uploaded, waiting for HR" to "#1D4ED8"
                "Rejected" -> "Returned: please upload again" to "#B4141B"
                else -> "Not uploaded yet" to "#6B6B63"
            }
            box.addView(TextView(requireContext()).apply {
                text = label
                textSize = 12f
                setTextColor(Color.parseColor(tint))
            })
            if (requirement.status == "Rejected" && !requirement.remarks.isNullOrBlank()) {
                box.addView(caption("HR note: ${requirement.remarks}"))
            }
            if (requirement.status != "Verified") {
                val dueDays = REQUIREMENT_DUE_DAYS[requirement.requirement_name]
                val daysLeft = dueDays?.let {
                    com.mrdiy.careers.ui.base.DateText.daysUntil(requirement.created_at, it)
                }
                if (daysLeft != null) {
                    box.addView(TextView(requireContext()).apply {
                        text = if (daysLeft < 0) {
                            "Overdue by ${-daysLeft} day${if (daysLeft == -1) "" else "s"} — please submit as soon as possible"
                        } else {
                            "Due in $daysLeft day${if (daysLeft == 1) "" else "s"}"
                        }
                        textSize = 11f
                        setTextColor(Color.parseColor(if (daysLeft < 0) "#B4141B" else "#A15C00"))
                    })
                }
            }
            if (requirement.status != "Verified") {
                val isSignature = requirement.requirement_name == "Signed Employment Contract"
                box.addView(
                    actionButton(
                        if (isSignature) "Sign" else if (requirement.document_url == null) "Upload" else "Replace",
                        filled = false
                    ) {
                        if (isSignature) {
                            val args = Bundle().apply {
                                putString(SignContractFragment.ARG_REQUIREMENT_ID, requirement.id)
                                putString(SignContractFragment.ARG_JOB_TITLE, currentJobTitle)
                                putString(SignContractFragment.ARG_APPLICANT_NAME, currentApplicantName)
                            }
                            findNavController().navigate(R.id.action_applicationDetail_to_signContract, args)
                        } else {
                            pendingRequirementId = requirement.id
                            pickDocument.launch(arrayOf("application/pdf", "image/jpeg", "image/png"))
                        }
                    }
                )
            }
            card.addView(box)
        }
        return card
    }

    // ----------------------------------------------------------------- actions

    private fun respond(interviewId: String, answer: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = RecruitmentRepository(requireContext()).respondToInterview(interviewId, answer)
            if (_binding == null) return@launch
            Toast.makeText(
                requireContext(),
                if (ok) "Thank you. HR was told." else "Could not send your answer. Please try again.",
                Toast.LENGTH_SHORT
            ).show()
            load()
        }
    }

    private fun uploadDocument(requirementId: String, uri: Uri) {
        Toast.makeText(requireContext(), "Uploading…", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val problem = RecruitmentRepository(requireContext()).uploadRequirement(requirementId, uri)
            if (_binding == null) return@launch
            Toast.makeText(requireContext(), problem ?: "Uploaded. HR will check it.", Toast.LENGTH_SHORT).show()
            load()
        }
    }

    // ------------------------------------------------------------ view helpers

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun color(id: Int) = ContextCompat.getColor(requireContext(), id)

    private fun cardContainer(): LinearLayout = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(Color.WHITE)
            setStroke(dp(1), color(R.color.border_light))
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(14) }
    }

    private fun card(titleText: String, bodyText: String): View = cardContainer().apply {
        addView(heading(titleText))
        addView(body(bodyText))
    }

    private fun title(text: String) = TextView(requireContext()).apply {
        this.text = text
        textSize = 20f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(color(R.color.text_primary))
    }

    private fun heading(text: String) = TextView(requireContext()).apply {
        this.text = text
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(color(R.color.text_primary))
        setPadding(0, 0, 0, dp(6))
    }

    private fun emphasis(text: String) = TextView(requireContext()).apply {
        this.text = text
        textSize = 17f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(color(R.color.orange_primary))
        setPadding(0, 0, 0, dp(4))
    }

    private fun body(text: String) = TextView(requireContext()).apply {
        this.text = text
        textSize = 13f
        setLineSpacing(0f, 1.15f)
        setTextColor(color(R.color.text_secondary))
        setPadding(0, dp(2), 0, dp(6))
    }

    private fun caption(text: String) = TextView(requireContext()).apply {
        this.text = text
        textSize = 12f
        setTextColor(color(R.color.text_muted))
    }

    private fun statusChip(status: String) = TextView(requireContext()).apply {
        text = DateText.statusLabel(status)
        textSize = 12f
        setTypeface(typeface, Typeface.BOLD)
        val (bg, fg) = when (status) {
            "Hired" -> "#D9F5E3" to "#157A3D"
            "Rejected" -> "#FBE0E1" to "#B4141B"
            "Reviewed" -> "#DCE9FF" to "#1D4ED8"
            "Shortlisted" -> "#EADCFF" to "#6D28D9"
            "Interview Scheduled" -> "#E0E7FF" to "#3730A3"
            "On Hold" -> "#E5E7EB" to "#374151"
            "Resigned" -> "#E0F2FE" to "#075985"
            "Terminated" -> "#FFE4E6" to "#9F1239"
            "Contract Ended" -> "#FEF3C7" to "#92400E"
            else -> "#FFF3BF" to "#7A5B00"
        }
        setTextColor(Color.parseColor(fg))
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.parseColor(bg)) }
        setPadding(dp(12), dp(5), dp(12), dp(5))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(10) }
    }

    private fun actionButton(text: String, filled: Boolean, onClick: () -> Unit) = Button(requireContext()).apply {
        this.text = text
        isAllCaps = false
        textSize = 13f
        setTextColor(if (filled) Color.WHITE else color(R.color.orange_primary))
        background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(if (filled) color(R.color.orange_primary) else Color.WHITE)
            setStroke(dp(1), color(R.color.orange_primary))
        }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)).apply { topMargin = dp(6) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_APPLICATION_ID = "applicationId"
    }
}
