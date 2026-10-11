package com.mrdiy.careers.ui.base

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Turns database timestamps such as 2026-10-11T14:45:05.508503+00:00 into readable local time.
 * Uses java.util only, because the app supports Android 7 (API 24) where java.time is unavailable.
 */
object DateText {

    private val ISO = Regex(
        """^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(Z|[+-]\d{2}(?::?\d{2})?)?$"""
    )

    /** "Oct 11, 2026 · 10:45 PM" in the phone's time zone; returns the input unchanged if it can't be parsed. */
    fun dateTime(iso: String): String = format(iso, "MMM d, yyyy · h:mm a")

    /** "Oct 11, 2026" in the phone's time zone. */
    fun date(iso: String): String = format(iso, "MMM d, yyyy")

    /** "Wed, Oct 14, 2026 · 2:00 PM", for interview schedules. */
    fun dayAndTime(iso: String): String = format(iso, "EEE, MMM d, yyyy · h:mm a")

    /** "Just now", "5m ago", "3h ago", "2d ago", otherwise the date ("Oct 11, 2026"). */
    fun relative(iso: String): String {
        val millis = parseMillis(iso) ?: return iso
        val minutes = (System.currentTimeMillis() - millis) / 60_000
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 60 * 24 -> "${minutes / 60}h ago"
            minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d ago"
            else -> date(iso)
        }
    }

    private fun format(iso: String, pattern: String): String {
        val millis = parseMillis(iso) ?: return iso
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
    }

    /** Epoch milliseconds for an ISO-8601 timestamp with an optional offset (no offset means UTC). */
    private fun parseMillis(iso: String): Long? {
        val m = ISO.matchEntire(iso.trim()) ?: return null
        val (year, month, day, hour, minute, second) = m.destructured
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year.toInt(), month.toInt() - 1, day.toInt(), hour.toInt(), minute.toInt(), second.toInt())
        }
        val zone = m.groupValues[7]
        val offsetMinutes = if (zone.isEmpty() || zone == "Z") {
            0
        } else {
            val sign = if (zone[0] == '-') -1 else 1
            val digits = zone.substring(1).replace(":", "")
            sign * (digits.substring(0, 2).toInt() * 60 + (digits.getOrNull(2)?.let { digits.substring(2).toInt() } ?: 0))
        }
        return calendar.timeInMillis - offsetMinutes * 60_000L
    }

    /** The pipeline step a stored status means; old names are mapped, unknown values count as Pending. */
    fun normalizeStatus(status: String): String = when (status.trim().lowercase()) {
        "reviewed", "under review" -> "Reviewed"
        "shortlisted" -> "Shortlisted"
        "interview scheduled", "interview" -> "Interview Scheduled"
        "hired", "approved", "accepted" -> "Hired"
        "rejected" -> "Rejected"
        "on hold", "on-hold", "onhold" -> "On Hold"
        else -> "Pending"
    }

    /** The status names applicants see. */
    fun statusLabel(status: String): String = when (normalizeStatus(status)) {
        "Reviewed" -> "Under review"
        else -> normalizeStatus(status)
    }
}
