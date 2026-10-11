package com.mrdiy.careers.data.repository

import android.content.Context
import com.mrdiy.careers.data.auth.AuthManager
import com.mrdiy.careers.data.auth.SupabaseProvider
import com.mrdiy.careers.model.Alert
import com.mrdiy.careers.model.AlertType
import com.mrdiy.careers.model.Message
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InboxRepository(private val context: Context) {
    private val userId = AuthManager(context).getCurrentUserId()

    @kotlinx.serialization.Serializable
    data class AlertRow(
        val id: String? = null,
        val user_id: String? = null,
        val type: String? = "JOB_MATCH",
        val title: String? = null,
        val message: String? = null,
        val time_ago: String? = null,
        val is_read: Boolean = false,
        val job_id: String? = null
    )

    @kotlinx.serialization.Serializable
    data class MessageRow(
        val id: String? = null,
        val user_id: String? = null,
        val sender_name: String? = null,
        val sender_avatar: String? = null,
        val last_message: String? = null,
        val time_ago: String? = null,
        val is_read: Boolean = false,
        val unread_count: Int = 0,
        val job_id: String? = null
    )

    suspend fun alerts(): Result<List<Alert>> = withContext(Dispatchers.IO) { runCatching {
        if (userId.isNullOrBlank()) return@runCatching emptyList()
        SupabaseProvider.client.postgrest["alerts"].select { filter { eq("user_id", userId!!) } }
            .decodeList<AlertRow>().map {
                Alert(
                    id = it.id ?: java.util.UUID.randomUUID().toString(),
                    type = runCatching { AlertType.valueOf(it.type ?: "JOB_MATCH") }.getOrDefault(AlertType.JOB_MATCH),
                    title = it.title ?: "Notification",
                    message = it.message ?: "",
                    timeAgo = it.time_ago ?: "Just now",
                    isRead = it.is_read,
                    jobId = it.job_id
                )
            }
    }.recoverCatching { error ->
        if (!error.message.orEmpty().contains("PGRST205") && !error.message.orEmpty().contains("Could not find the table")) throw error
        ApplicationsRepository(context).getApplications().getOrThrow()
            .filter { !it.status.equals("Pending", true) && !it.status.equals("submitted", true) }
            .map { Alert("application-${it.id}-${it.status}", AlertType.APPLICATION_UPDATE,
                "Application ${it.status}", it.job_title, it.applied_at, jobId = it.job_id) }
    } }

    suspend fun messages(): Result<List<Message>> = withContext(Dispatchers.IO) { runCatching {
        if (userId.isNullOrBlank()) return@runCatching emptyList()
        SupabaseProvider.client.postgrest["messages"].select { filter { eq("user_id", userId!!) } }
            .decodeList<MessageRow>().map {
                Message(
                    id = it.id ?: java.util.UUID.randomUUID().toString(),
                    senderName = it.sender_name ?: "MR.D.I.Y. HR",
                    senderAvatar = it.sender_avatar ?: "HR",
                    lastMessage = it.last_message ?: "Your application status has been updated.",
                    timeAgo = it.time_ago ?: "Just now",
                    isRead = it.is_read,
                    unreadCount = it.unread_count,
                    jobId = it.job_id
                )
            }
    }.recoverCatching {
        emptyList()
    } }
}
