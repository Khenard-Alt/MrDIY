package com.mrdiy.careers.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MrDiyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "MR.D.I.Y. Careers"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: message.data["message"]
            ?: return

        AppNotificationManager.show(this, title, body)
        // Remember it so AlertNotifier does not show the same alert a second time from its own check.
        message.data["alert_id"]?.let { AlertNotifier.markPushShown(this, it) }
    }

    override fun onNewToken(token: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_FCM_TOKEN, token)
            .apply()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            PushTokenRegistrar.registerCurrentUser(this@MrDiyFirebaseMessagingService, token)
        }
    }

    companion object {
        const val PREFS_NAME = "firebase_messaging"
        const val KEY_FCM_TOKEN = "fcm_token"
    }
}
