package com.mrdiy.careers.model

data class Alert(
    val id: String,
    val type: AlertType,
    val title: String,
    val message: String,
    val timeAgo: String,
    val isRead: Boolean = false,
    val jobId: String? = null,
    val applicationId: String? = null
)

enum class AlertType {
    INTERVIEW_INVITE,
    APPLICATION_UPDATE,
    JOB_MATCH,
    NEW_JOB,
    NEARBY_JOB,
    MESSAGE,
    SYSTEM
}
