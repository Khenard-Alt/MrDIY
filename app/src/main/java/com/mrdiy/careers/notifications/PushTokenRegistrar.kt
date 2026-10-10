package com.mrdiy.careers.notifications

import android.content.Context
import com.mrdiy.careers.data.SafeDiagnostics
import com.mrdiy.careers.data.auth.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object PushTokenRegistrar {

    suspend fun registerCurrentUser(context: Context, suppliedToken: String? = null) {
        try {
            val client = SupabaseProvider.client
            client.auth.awaitInitialization()
            val userId = client.auth.currentUserOrNull()?.id ?: return
            val token = suppliedToken ?: storedToken(context) ?: return
            client.postgrest["push_tokens"].upsert(
                buildJsonObject {
                    put("token", token)
                    put("user_id", userId)
                    put("platform", "android")
                }
            ) {
                onConflict = "token"
            }
        } catch (error: Exception) {
            SafeDiagnostics.record("push_token_register", error)
        }
    }

    suspend fun unregisterCurrentUser(context: Context) {
        try {
            val client = SupabaseProvider.client
            client.auth.awaitInitialization()
            val userId = client.auth.currentUserOrNull()?.id ?: return
            val token = storedToken(context) ?: return
            client.postgrest["push_tokens"].delete {
                filter {
                    eq("token", token)
                    eq("user_id", userId)
                }
            }
        } catch (error: Exception) {
            SafeDiagnostics.record("push_token_unregister", error)
        }
    }

    private fun storedToken(context: Context): String? =
        context.getSharedPreferences(
            MrDiyFirebaseMessagingService.PREFS_NAME,
            Context.MODE_PRIVATE
        ).getString(MrDiyFirebaseMessagingService.KEY_FCM_TOKEN, null)
}
