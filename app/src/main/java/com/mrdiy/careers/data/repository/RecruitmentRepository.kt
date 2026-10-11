package com.mrdiy.careers.data.repository

import android.content.Context
import android.net.Uri
import com.mrdiy.careers.data.auth.AuthManager
import com.mrdiy.careers.data.auth.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * Everything after "applied": the interview HR scheduled, and the pre-employment documents of a hired
 * applicant. Rows are written by HR through the admin; the applicant can only answer an interview and
 * upload their own documents (enforced by row level security).
 */
class RecruitmentRepository(private val context: Context) {

    @Serializable
    data class InterviewRow(
        val id: String,
        val application_id: String,
        val scheduled_at: String,
        val venue: String = "",
        val mode: String = "in_person",
        val meeting_link: String? = null,
        val notes: String? = null,
        val status: String = "Scheduled",
        val applicant_response: String = "Pending"
    ) {
        val isActive: Boolean get() = status == "Scheduled" || status == "Rescheduled"
        val isOnline: Boolean get() = mode == "online"
    }

    @Serializable
    data class RequirementRow(
        val id: String,
        val application_id: String,
        val requirement_name: String,
        val status: String = "Pending",
        val document_url: String? = null,
        val remarks: String? = null
    )

    private val userId: String? get() = AuthManager(context).getCurrentUserId()

    /** The newest interview of this application (active ones first), or null if none. */
    suspend fun interviewFor(applicationId: String): Result<InterviewRow?> = withContext(Dispatchers.IO) {
        runCatching {
            val rows = SupabaseProvider.client.postgrest["interviews"].select {
                filter { eq("application_id", applicationId) }
            }.decodeList<InterviewRow>()
            rows.filter { it.isActive }.maxByOrNull { it.scheduled_at } ?: rows.maxByOrNull { it.scheduled_at }
        }
    }

    /** Tells HR whether the applicant can attend. [response] is "Confirmed" or "Declined". */
    suspend fun respondToInterview(interviewId: String, response: String): Boolean = withContext(Dispatchers.IO) {
        require(response == "Confirmed" || response == "Declined")
        runCatching {
            SupabaseProvider.client.postgrest["interviews"].update({
                set("applicant_response", response)
                set("responded_at", com.mrdiy.careers.ui.base.DateText.nowIso())
            }) { filter { eq("id", interviewId) } }
            true
        }.getOrDefault(false)
    }

    /** The document checklist of an application (empty until the applicant is Hired). */
    suspend fun requirementsFor(applicationId: String): Result<List<RequirementRow>> = withContext(Dispatchers.IO) {
        runCatching {
            SupabaseProvider.client.postgrest["pre_employment_requirements"].select {
                filter { eq("application_id", applicationId) }
            }.decodeList<RequirementRow>().sortedBy { it.requirement_name }
        }
    }

    /**
     * Uploads a PDF/JPG/PNG (max 5 MB) for one requirement and marks it Uploaded.
     * Returns null on success, or a message the applicant can read.
     */
    suspend fun uploadRequirement(requirementId: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        val uid = userId ?: return@withContext "Please sign in first."
        try {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri).orEmpty()
            val (extension, contentType) = when {
                mime == "application/pdf" -> "pdf" to ContentType.Application.Pdf
                mime == "image/jpeg" -> "jpg" to ContentType.Image.JPEG
                mime == "image/png" -> "png" to ContentType.Image.PNG
                else -> return@withContext "Please choose a PDF, JPG or PNG file."
            }
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext "Could not read the file."
            if (bytes.isEmpty() || bytes.size > MAX_BYTES) return@withContext "The file must be 5 MB or smaller."

            val path = "$uid/$requirementId.$extension"
            SupabaseProvider.client.storage.from(BUCKET).upload(path, bytes) {
                upsert = true
                this.contentType = contentType
            }
            SupabaseProvider.client.postgrest["pre_employment_requirements"].update({
                set("document_url", path)
                set("status", "Uploaded")
                set("updated_at", com.mrdiy.careers.ui.base.DateText.nowIso())
            }) { filter { eq("id", requirementId) } }
            null
        } catch (e: Exception) {
            com.mrdiy.careers.data.SafeDiagnostics.record("requirement_upload", e)
            "Could not upload the document. Please try again."
        }
    }

    companion object {
        const val BUCKET = "requirements"
        const val MAX_BYTES = 5 * 1024 * 1024
    }
}
