package com.mrdiy.careers.data.auth

import android.content.Context
import android.content.SharedPreferences
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Phone
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlinx.coroutines.CancellationException
import com.mrdiy.careers.data.SafeDiagnostics

object SupabaseProvider {
    private const val SUPABASE_URL      = "https://xbsrnnuhepljpvzewvsp.supabase.co"
    private const val SUPABASE_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inhic3JubnVoZXBsanB2emV3dnNwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE2NTU4NDcsImV4cCI6MjEwNzIzMTg0N30." +
                "-TrtGsQDfh9x3HZIQkSMjvrm5id-Dwn8VFx4j4NkWOw"

    val supabaseJson: Json = Json {
        ignoreUnknownKeys  = true
        coerceInputValues  = true
        isLenient          = true
    }

    val client: SupabaseClient by lazy {
        createSupabaseClient(SUPABASE_URL, SUPABASE_ANON_KEY) {
            // SDK debug logs include callback fragments containing access/refresh tokens.
            defaultLogLevel = io.github.jan.supabase.logging.LogLevel.NONE
            install(Auth) {
                scheme = "mrdiy"
                host = "login-callback"
            }
            install(Postgrest)
            install(Storage)
            install(Realtime)
            defaultSerializer = KotlinXSerializer(supabaseJson)
        }
    }
}


class AuthManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val client = SupabaseProvider.client

    companion object {
        private val registrationGate = RequestGate()
        private val loginGate = RequestGate()
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_NAME    = "user_name"
        private const val KEY_USER_ID      = "user_id"
    }

    val isLoggedIn: Boolean get() {
        val session = client.auth.currentSessionOrNull()
        return AccountRules.sessionUsable(session?.user?.id, session?.expiresAt?.epochSeconds, System.currentTimeMillis() / 1000)
    }
    val currentUser: String get() = getCurrentUserName()
    fun getCurrentUserId(): String? = if (isLoggedIn) client.auth.currentUserOrNull()?.id else null
    fun getCurrentUserName(): String = client.auth.currentUserOrNull()?.userMetadata
        ?.get("full_name")?.jsonPrimitive?.contentOrNull.orEmpty()
    fun saveLoginState(isLoggedIn: Boolean) { /* Supabase session is the only authority. */ }

    suspend fun restoreSession(): Boolean {
        client.auth.awaitInitialization()
        val session = client.auth.currentSessionOrNull() ?: return false
        try {
            if (!isLoggedIn) client.auth.refreshCurrentSession()
            client.auth.retrieveUserForCurrentSession(updateSession = true)
            return saveCurrentSupabaseUser()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            SafeDiagnostics.record("restore_session", e)
            // Keep refresh credentials on transport failure, but do not enter protected screens.
            return false
        }
    }

    suspend fun signOut() {
        client.auth.awaitInitialization()
        try {
            com.mrdiy.careers.notifications.PushTokenRegistrar.unregisterCurrentUser(context)
            client.auth.signOut()
        }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { SafeDiagnostics.record("sign_out", e) }
        finally {
            client.auth.clearSession()
            prefs.edit().clear().apply()
        }
    }

    fun login(email: String, password: String, callback: (Boolean, String) -> Unit) {
        if (!loginGate.begin()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.auth.awaitInitialization()
                client.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                val user = client.auth.currentUserOrNull()
                if (user != null) {
                    val userId = user.id
                    val userName = user.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull.orEmpty()
                    prefs.edit()
                        .putString(KEY_USER_ID, userId)
                        .putString(KEY_USER_NAME, userName)
                        .putBoolean(KEY_IS_LOGGED_IN, true)
                        .apply()
                    withContext(Dispatchers.Main) {
                        callback(true, "Login successful")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        callback(false, "Login failed: No user returned")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(false, readableAuthError(e))
                }
            } finally { loginGate.end() }
        }
    }

    fun register(fullName: String, email: String, password: String, termsAccepted: Boolean,
                 phone: String = "", callback: (Boolean, String) -> Unit) {
        val validation = AccountRules.registrationError(fullName, email, password, termsAccepted)
        if (validation != null) { callback(false, validation); return }
        if (!registrationGate.begin()) { callback(false, "Registration is already in progress."); return }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.auth.awaitInitialization()
                client.auth.signUpWith(Email, redirectUrl = "mrdiy://login-callback") {
                    this.email = email.trim()
                    this.password = password
                    data = buildJsonObject {
                        put("full_name", fullName.trim())
                        put("phone", phone.trim())
                        put("terms_accepted", true)
                        put("terms_accepted_at", kotlinx.datetime.Clock.System.now().toString())
                    }
                }
                // Signup itself triggers Supabase Auth's confirmation email. Do not send twice.
                prefs.edit().putString("pending_email", email.trim()).apply()
                withContext(Dispatchers.Main) {
                    callback(true, "Check your email, including spam, for a verification link. If you already have an account, sign in.")
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                SafeDiagnostics.record("registration", e)
                withContext(Dispatchers.Main) { callback(false, readableRegistrationError(e)) }
            } finally { registrationGate.end() }
        }
    }

    fun signInWithGoogle(callback: (Boolean, String) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                client.auth.signInWith(Google)
                callback(true, "Opening Google sign-in...")
            } catch (e: Exception) {
                callback(false, "Google sign-in could not start. Please try again.")
            }
        }
    }

    fun requestPhoneOtp(phone: String, callback: (Boolean, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.auth.signInWith(Phone) { this.phone = phone }
                withContext(Dispatchers.Main) { callback(true, "Verification code sent.") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(false, "Phone sign-in is unavailable. Check the phone provider settings.")
                }
            }
        }
    }

    fun verifyPhoneOtp(phone: String, token: String, callback: (Boolean, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.auth.verifyPhoneOtp(
                    type = OtpType.Phone.SMS,
                    phone = phone,
                    token = token
                )
                val user = client.auth.currentUserOrNull()
                if (user == null) {
                    withContext(Dispatchers.Main) { callback(false, "Verification failed. Please request a new code.") }
                } else {
                    prefs.edit()
                        .putString(KEY_USER_ID, user.id)
                        .putString(KEY_USER_NAME, user.phone ?: phone)
                        .putBoolean(KEY_IS_LOGGED_IN, true)
                        .apply()
                    withContext(Dispatchers.Main) { callback(true, "Phone verified.") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback(false, "The code is invalid or expired.") }
            }
        }
    }

    fun saveCurrentSupabaseUser(): Boolean {
        if (!isLoggedIn) return false
        val user = client.auth.currentUserOrNull() ?: return false
        prefs.edit()
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_USER_NAME, getCurrentUserName())
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply()
        return true
    }

    fun resetPassword(email: String, callback: (Boolean, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.auth.resetPasswordForEmail(email)
                withContext(Dispatchers.Main) {
                    callback(true, "Password reset email sent")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(false, "We could not send the reset email. Please try again.")
                }
            }
        }
    }

    fun updatePassword(password: String, callback: (Boolean, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.auth.updateUser { this.password = password }
                withContext(Dispatchers.Main) { callback(true, "Password updated.") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback(false, "Could not update the password. Please try again.") }
            }
        }
    }

    private fun readableAuthError(error: Exception): String {
        val message = error.message.orEmpty().lowercase()
        return when {
            "invalid login" in message || "invalid credentials" in message ->
                "Email or password is incorrect."
            "email not confirmed" in message ->
                "Please confirm your email before signing in."
            "network" in message || "timeout" in message ->
                "Connection problem. Check your internet and try again."
            else -> "Login failed. Check your details and try again."
        }
    }

    private fun readableRegistrationError(error: Exception): String {
        val message = error.message.orEmpty().lowercase()
        return when {
            "already registered" in message || "already exists" in message ->
                "That email is already registered. Try signing in instead."
            "rate limit" in message || "too many" in message ->
                "Too many email attempts. Please wait and try again later."
            "email" in message && "send" in message ->
                "We could not send the verification email. Please try again later."
            "network" in message || "timeout" in message ->
                "Connection problem. Check your internet and try again."
            else -> "Registration failed. Check your details and try again."
        }
    }
}
