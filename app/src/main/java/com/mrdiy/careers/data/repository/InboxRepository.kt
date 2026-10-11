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
        val job_id: String? = null,
        val created_at: String? = null
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
            .decodeList<AlertRow>()
            // Newest first; rows without a time (created before the column existed) go last.
            .sortedByDescending { it.created_at.orEmpty() }
            .map {
                Alert(
                    id = it.id ?: java.util.UUID.randomUUID().toString(),
                    type = runCatching { AlertType.valueOf(it.type ?: "JOB_MATCH") }.getOrDefault(AlertType.SYSTEM),
                    title = it.title ?: "Notification",
                    message = it.message ?: "",
                    timeAgo = it.created_at?.let { time -> com.mrdiy.careers.ui.base.DateText.relative(time) } ?: it.time_ago ?: "Just now",
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

    /** The signed-in user's unread notification rows (empty on any error). */
    suspend fun unreadAlertRows(): List<AlertRow> = withContext(Dispatchers.IO) {
        runCatching {
            if (userId.isNullOrBlank()) return@runCatching emptyList()
            SupabaseProvider.client.postgrest["alerts"].select {
                filter {
                    eq("user_id", userId!!)
                    eq("is_read", false)
                }
            }.decodeList<AlertRow>()
        }.getOrDefault(emptyList())
    }

    /** Number of unread notifications for the signed-in user (0 on any error). */
    suspend fun unreadAlertCount(): Int = unreadAlertRows().size

    /** Marks all of the signed-in user's notifications as read (the user may only change is_read). */
    suspend fun markAllAlertsRead(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (userId.isNullOrBlank()) return@runCatching false
            SupabaseProvider.client.postgrest["alerts"].update({ set("is_read", true) }) {
                filter {
                    eq("user_id", userId!!)
                    eq("is_read", false)
                }
            }
            true
        }.getOrDefault(false)
    }

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
