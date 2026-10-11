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

    /** One row of public.messages: a message from one person to another. */
    @kotlinx.serialization.Serializable
    data class MessageDbRow(
        val id: String? = null,
        val sender_id: String = "",
        val receiver_id: String = "",
        val job_id: String? = null,
        val body: String = "",
        val is_read: Boolean = false,
        val created_at: String? = null
    )

    /** A message shown as a chat bubble. */
    data class ChatMessage(
        val id: String,
        val body: String,
        val mine: Boolean,
        val time: String
    )

    private suspend fun myMessageRows(): List<MessageDbRow> {
        val me = userId ?: return emptyList()
        return SupabaseProvider.client.postgrest["messages"].select {
            filter {
                or {
                    eq("sender_id", me)
                    eq("receiver_id", me)
                }
            }
        }.decodeList<MessageDbRow>().sortedBy { it.created_at.orEmpty() }
    }

    /** The conversation list: one entry per person the user has messaged with (usually MR.D.I.Y. HR). */
    suspend fun messages(): Result<List<Message>> = withContext(Dispatchers.IO) { runCatching {
        val me = userId
        if (me.isNullOrBlank()) return@runCatching emptyList()
        myMessageRows()
            .groupBy { if (it.sender_id == me) it.receiver_id else it.sender_id }
            .entries
            .sortedByDescending { (_, rows) -> rows.last().created_at.orEmpty() } // newest conversation first
            .map { (otherId, rows) ->
                val last = rows.last()
                val unread = rows.count { it.receiver_id == me && !it.is_read }
                Message(
                    id = otherId,
                    senderName = "MR.D.I.Y. HR",
                    senderAvatar = "HR",
                    lastMessage = (if (last.sender_id == me) "You: " else "") + last.body.replace(Regex("\\s+"), " ").trim(),
                    timeAgo = last.created_at?.let { com.mrdiy.careers.ui.base.DateText.relative(it) } ?: "",
                    isRead = unread == 0,
                    unreadCount = unread,
                    jobId = last.job_id
                )
            }
    }.recoverCatching { emptyList() } }

    /** All messages between the user and [otherId], oldest first. */
    suspend fun thread(otherId: String): Result<List<ChatMessage>> = withContext(Dispatchers.IO) { runCatching {
        val me = userId
        if (me.isNullOrBlank()) return@runCatching emptyList()
        myMessageRows()
            .filter { it.sender_id == otherId || it.receiver_id == otherId }
            .map {
                ChatMessage(
                    id = it.id ?: java.util.UUID.randomUUID().toString(),
                    body = it.body,
                    mine = it.sender_id == me,
                    time = it.created_at?.let { time -> com.mrdiy.careers.ui.base.DateText.dateTime(time) }.orEmpty()
                )
            }
    } }

    /** Sends a reply to [otherId]. Returns false if it could not be sent. */
    suspend fun sendMessage(otherId: String, jobId: String?, text: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val me = userId
            if (me.isNullOrBlank() || text.isBlank()) return@runCatching false
            SupabaseProvider.client.postgrest["messages"].insert(
                kotlinx.serialization.json.buildJsonObject {
                    put("sender_id", kotlinx.serialization.json.JsonPrimitive(me))
                    put("receiver_id", kotlinx.serialization.json.JsonPrimitive(otherId))
                    if (jobId != null) put("job_id", kotlinx.serialization.json.JsonPrimitive(jobId))
                    put("body", kotlinx.serialization.json.JsonPrimitive(text.trim()))
                    put("is_read", kotlinx.serialization.json.JsonPrimitive(false))
                }
            )
            true
        }.getOrDefault(false)
    }

    /** Marks the messages [otherId] sent to the user as read. */
    suspend fun markThreadRead(otherId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val me = userId
            if (me.isNullOrBlank()) return@runCatching false
            SupabaseProvider.client.postgrest["messages"].update({ set("is_read", true) }) {
                filter {
                    eq("receiver_id", me)
                    eq("sender_id", otherId)
                    eq("is_read", false)
                }
            }
            true
        }.getOrDefault(false)
    }

    /** The job the latest message in this thread is about, for the reply. */
    suspend fun lastJobId(otherId: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            myMessageRows().filter { it.sender_id == otherId || it.receiver_id == otherId }
                .lastOrNull { it.job_id != null }?.job_id
        }.getOrNull()
    }
}
