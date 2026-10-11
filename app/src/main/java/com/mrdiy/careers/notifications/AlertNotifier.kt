package com.mrdiy.careers.notifications

import android.content.Context
import com.mrdiy.careers.data.auth.AuthManager
import com.mrdiy.careers.data.repository.InboxRepository

/**
 * Shows the signed-in user's new notifications (rows in `alerts`) in the phone's notification tray.
 *
 * It is called when the app opens, instantly through a realtime subscription while the app is running,
 * and every ~15 minutes by [AlertCheckWorker] when the app is closed. Each alert is shown once per
 * user: shown ids are remembered. The first run only records what already exists, so installing the
 * feature does not flood the tray with old notifications.
 */
object AlertNotifier {
    private const val MAX_INDIVIDUAL = 4
    private val lock = Any()
    private const val PUSH_PREFS = "alert_push_shown"

    /** Called when a push already showed this alert in the tray. */
    fun markPushShown(context: Context, alertId: String) {
        synchronized(lock) {
            val prefs = context.applicationContext.getSharedPreferences(PUSH_PREFS, Context.MODE_PRIVATE)
            val ids = prefs.getStringSet("ids", emptySet()).orEmpty().toMutableSet()
            ids.add(alertId)
            prefs.edit().putStringSet("ids", ids.toList().takeLast(200).toSet()).apply()
        }
    }

    suspend fun checkAndNotify(context: Context) {
        val app = context.applicationContext
        val auth = AuthManager(app)
        if (!auth.isLoggedIn && !auth.restoreSession()) return
        val uid = auth.getCurrentUserId() ?: return

        val unread = InboxRepository(app).unreadAlertRows()

        synchronized(lock) {
            val prefs = app.getSharedPreferences("alert_notifier_$uid", Context.MODE_PRIVATE)
            val shown = prefs.getStringSet("shown", emptySet()).orEmpty().toMutableSet()
            shown += app.getSharedPreferences(PUSH_PREFS, Context.MODE_PRIVATE)
                .getStringSet("ids", emptySet()).orEmpty()

            if (!prefs.getBoolean("initialized", false)) {
                prefs.edit()
                    .putStringSet("shown", unread.mapNotNull { it.id }.toSet())
                    .putBoolean("initialized", true)
                    .apply()
                return
            }

            val fresh = unread.filter { it.id != null && it.id !in shown }
            if (fresh.isEmpty()) return

            fresh.take(MAX_INDIVIDUAL).forEach {
                AppNotificationManager.show(app, it.title ?: "MR.D.I.Y. Careers", it.message.orEmpty())
            }
            if (fresh.size > MAX_INDIVIDUAL) {
                AppNotificationManager.show(
                    app, "MR.D.I.Y. Careers", "You have ${fresh.size - MAX_INDIVIDUAL} more new notifications."
                )
            }

            // Keep the remembered set from growing forever; only unread ids can come back.
            val remembered = (shown + fresh.mapNotNull { it.id }).intersect(unread.mapNotNull { it.id }.toSet() + fresh.mapNotNull { it.id })
            prefs.edit().putStringSet("shown", remembered).apply()
        }
    }
}
