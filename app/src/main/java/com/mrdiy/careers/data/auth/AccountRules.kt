package com.mrdiy.careers.data.auth

import java.util.concurrent.atomic.AtomicBoolean

object AccountRules {
    fun email(value: String): Boolean = Regex("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$").matches(value.trim())
    fun password(value: String): Boolean = value.length >= 6
    fun phone(value: String): Boolean = Regex("^\\+?[0-9 ()-]+$").matches(value) && value.count(Char::isDigit) in 10..15
    fun registrationError(name: String, email: String, password: String, terms: Boolean): String? = when {
        !terms -> "Please agree to the Terms of Service and Privacy Policy."
        name.isBlank() -> "Full name is required."
        !email(email) -> "Enter a valid email address."
        !password(password) -> "Password must be at least 6 characters."
        else -> null
    }
    fun greeting(name: String): String = name.trim().split(Regex("\\s+")).firstOrNull()
        ?.takeIf { it.isNotBlank() }?.let { "Hello, $it" } ?: "Hello"
    fun experience(value: String): Int? = value.removeSuffix("+").toIntOrNull()?.takeIf { it in 0..10 }
    fun sessionUsable(id: String?, expiresAtSeconds: Long?, nowSeconds: Long): Boolean =
        !id.isNullOrBlank() && expiresAtSeconds != null && expiresAtSeconds > nowSeconds
}

/** Shared across fragment recreation and separate AuthManager instances. */
class RequestGate {
    private val active = AtomicBoolean(false)
    fun begin(): Boolean = active.compareAndSet(false, true)
    fun end() { active.set(false) }
}
