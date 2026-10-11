package com.mrdiy.careers.ui.resume

import android.app.Application
import android.net.Uri
import androidx.lifecycle.*
import com.mrdiy.careers.data.SafeDiagnostics
import com.mrdiy.careers.data.auth.SupabaseProvider
import com.mrdiy.careers.data.repository.*
import com.mrdiy.careers.model.ResumeParseResult
import com.mrdiy.careers.model.UserProfile
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.*

sealed class ResumeUploadState {
    object Idle : ResumeUploadState()
    data class FileSelected(val fileName: String) : ResumeUploadState()
    object ExtractingText : ResumeUploadState()
    object UploadingFile : ResumeUploadState()
    object ParsingWithAI : ResumeUploadState()
    data class ParseSuccess(val profile: UserProfile, val result: ResumeParseResult) : ResumeUploadState()
    data class Error(val message: String) : ResumeUploadState()
}

class ResumeUploadViewModel(application: Application) : AndroidViewModel(application) {
    private val resumeRepo = ResumeRepository(application)
    private val profileRepo = ProfileRepository(application)
    private val context = application.applicationContext
    private val _state = MutableLiveData<ResumeUploadState>(ResumeUploadState.Idle)
    val state: LiveData<ResumeUploadState> = _state
    private var upload: kotlinx.coroutines.Job? = null
    val busy get() = upload?.isActive == true
    fun cancelUpload() { upload?.cancel() }

    fun processResume(uri: Uri, fileName: String) {
        if (busy) return
        upload = viewModelScope.launch {
            var uploadedPath: String? = null
            var committed = false
            var commitStarted = false
            var uid = ""
            var parsed: UserProfile? = null
            var extractedText = ""
            try {
                uid = profileRepo.getCurrentUserId()
                if (uid.isBlank()) { _state.value = ResumeUploadState.Error("Please sign in to upload a resume."); return@launch }
                _state.value = ResumeUploadState.FileSelected(fileName)
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use(ResumeFiles::readBounded)
                        ?: throw IllegalArgumentException("Unreadable file")
                }
                val extension = ResumeFiles.extension(fileName, context.contentResolver.getType(uri), bytes)
                _state.value = ResumeUploadState.ExtractingText
                val text = resumeRepo.extractText(bytes, extension)
                extractedText = text
                ensureActive()
                parsed = resumeRepo.parseResume(text)

                // The resume must be the applicant's own: compare the name on it with the account name.
                val accountName = profileRepo.loadFromPrefs(uid).fullName
                    .ifBlank { com.mrdiy.careers.data.auth.AuthManager(context).getCurrentUserName() }
                val identity = com.mrdiy.careers.data.repository.ResumeIdentity.check(accountName, text)
                if (identity.result == com.mrdiy.careers.data.repository.ResumeIdentity.Result.MISMATCH) {
                    throw IllegalArgumentException(identity.message)
                }
                val identityNotice = identity.message
                check(uid == profileRepo.getCurrentUserId())
                val path = "$uid/resume-${java.util.UUID.randomUUID()}.$extension"
                _state.value = ResumeUploadState.UploadingFile
                uploadedPath = path
                SupabaseProvider.client.storage.from(ResumeFiles.BUCKET).upload(path, bytes) {
                    upsert = false
                    contentType = when (extension) {
                        "pdf" -> ContentType.Application.Pdf
                        "docx" -> ContentType.parse("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        else -> ContentType.Text.Plain
                    }
                }
                ensureActive()
                // Finish a started DB commit before cancellation cleanup can delete the object.
                withContext(NonCancellable) {
                    commitStarted = true
                    profileRepo.persistResume(uid, fileName, path, parsed!!, text)
                    committed = true
                }
                _state.value = ResumeUploadState.ParseSuccess(
                    profileRepo.loadFromPrefs(uid),
                    ResumeParseResult(success = true, skills = parsed!!.skills,
                        summary = (if (text.isBlank()) "Resume uploaded. Add your skills in your profile to improve matches."
                        else "Resume uploaded. Review the detected skills in your profile.") +
                            (if (identityNotice.isNotBlank()) "\n\n$identityNotice" else ""))
                )
            } catch (cancelled: CancellationException) {
                _state.value = ResumeUploadState.Idle
                throw cancelled
            } catch (invalid: IllegalArgumentException) {
                SafeDiagnostics.record("resume_upload", invalid)
                val errorMessage = invalid.message?.takeIf { it.isNotBlank() && "Unreadable" !in it }
                    ?: "Could not upload this file. Choose a valid PDF, DOCX, or TXT smaller than 10 MB, then retry."
                _state.value = ResumeUploadState.Error(errorMessage)
            } catch (error: Exception) {
                SafeDiagnostics.record("resume_upload", error)
                // The PDF has already been parsed at this point. Preserve the scan
                // locally so matching still works while storage/RLS sync is retried.
                if (uid.isNotBlank() && parsed != null && extractedText.isNotBlank()) {
                    val local = profileRepo.saveResumeLocally(uid, fileName, parsed!!, extractedText)
                    _state.value = ResumeUploadState.ParseSuccess(
                        local,
                        ResumeParseResult(
                            success = true,
                            skills = parsed!!.skills,
                            summary = "Resume scanned successfully. Cloud upload will retry when available."
                        )
                    )
                } else {
                    _state.value = ResumeUploadState.Error("Resume upload failed. Please try again.")
                }
            } finally {
                // If a DB response is lost, preserve the object: the row may already reference it.
                if (!committed && !commitStarted && uploadedPath != null) withContext(NonCancellable) {
                    try { SupabaseProvider.client.storage.from(ResumeFiles.BUCKET).delete(uploadedPath!!) }
                    catch (error: Exception) { SafeDiagnostics.record("resume_cleanup", error) }
                }
            }
        }
    }
}
