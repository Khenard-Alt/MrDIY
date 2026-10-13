package com.mrdiy.careers.data.repository

import android.content.Context
import android.net.Uri
import com.mrdiy.careers.data.SafeDiagnostics
import com.mrdiy.careers.data.auth.AuthManager
import com.mrdiy.careers.data.auth.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class ApplicantDocumentRow(
    val id: String? = null,
    val user_id: String,
    val document_name: String,
    val status: String = "Pending",
    val document_url: String? = null,
    val remarks: String? = null
)

/**
 * The applicant's own profile-level documents (Valid ID + pre-employment requirements):
 * optional, uploadable any time, not tied to one job application (ADMIN_MAJOR_PLAN.md §O).
 * Mirrors the per-application `pre_employment_requirements` flow in RecruitmentRepository,
 * but against the `applicant_documents` table and `applicant-documents` bucket.
 */
class ApplicantDocumentsRepository(private val context: Context) {

    private val userId: String? get() = AuthManager(context).getCurrentUserId()

    companion object {
        const val BUCKET = "applicant-documents"
        const val MAX_BYTES = 5 * 1024 * 1024

        /**
         * Must match lib/pipeline.ts PROFILE_DOCUMENTS on the admin web exactly.
         * No "Signed Employment Contract" here on purpose — there's no contract to
         * sign until the applicant is actually Hired, so that one only lives in the
         * post-hire checklist (ApplicationDetailFragment's documentsCard), never in
         * this always-open profile list.
         */
        val DOCUMENT_NAMES = listOf(
            "Valid ID (with signature)",
            "SSS Number / E-1 Form",
            "PhilHealth Member Data Record (MDR)",
            "Pag-IBIG MID Number / Member Data Form",
            "NBI Clearance / Police Clearance",
            "Pre-Employment Medical Exam / Health Permit",
            "BIR Form 1902 / TIN",
        )
    }

    /** The full checklist — every expected document, even ones never uploaded (shown as Pending). */
    suspend fun myDocuments(): Result<List<ApplicantDocumentRow>> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = userId ?: error("Please sign in first.")
            val rows = SupabaseProvider.client.postgrest["applicant_documents"].select {
                filter { eq("user_id", uid) }
            }.decodeList<ApplicantDocumentRow>()
            val byName = rows.associateBy { it.document_name }
            DOCUMENT_NAMES.map { name -> byName[name] ?: ApplicantDocumentRow(user_id = uid, document_name = name) }
        }
    }

    suspend fun uploadDocument(documentName: String, uri: Uri): String? = withContext(Dispatchers.IO) {
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

            val safeName = documentName.replace(Regex("[^A-Za-z0-9]+"), "_")
            val path = "$uid/$safeName.$extension"
            SupabaseProvider.client.storage.from(BUCKET).upload(path, bytes) {
                upsert = true
                this.contentType = contentType
            }

            val existing = SupabaseProvider.client.postgrest["applicant_documents"].select {
                filter {
                    eq("user_id", uid)
                    eq("document_name", documentName)
                }
            }.decodeList<ApplicantDocumentRow>().firstOrNull()

            if (existing?.id != null) {
                SupabaseProvider.client.postgrest["applicant_documents"].update({
                    set("document_url", path)
                    set("status", "Uploaded")
                    set("updated_at", com.mrdiy.careers.ui.base.DateText.nowIso())
                }) { filter { eq("id", existing.id) } }
            } else {
                SupabaseProvider.client.postgrest["applicant_documents"].insert(
                    ApplicantDocumentRow(
                        user_id = uid,
                        document_name = documentName,
                        status = "Uploaded",
                        document_url = path
                    )
                )
            }
            null
        } catch (e: Exception) {
            SafeDiagnostics.record("applicant_document_upload", e)
            "Could not upload the document. Please try again."
        }
    }
}
