package com.ndynagn.kmp.news.feature.auth.domain

/** Shared input policy; presentation maps issues to localized messages without changing credentials. */
enum class AuthInputIssue { EMAIL, PASSWORD_REQUIRED, PASSWORD_TOO_SHORT, PASSWORD_MISMATCH, CODE }

/** Minimal account input checks. Supabase remains authoritative for account and code validity. */
class AuthInputValidator {
    fun email(email: String): AuthInputIssue? {
        val parts = email.trim().split('@')
        return if (parts.size != 2 || parts.any { it.isBlank() } ||
            email.trim().any(Char::isWhitespace)
        ) {
            AuthInputIssue.EMAIL
        } else {
            null
        }
    }

    fun login(email: String, password: String): AuthInputIssue? =
        email(email) ?: if (password.isEmpty()) AuthInputIssue.PASSWORD_REQUIRED else null

    /** Requires eight Unicode code points and an exact repeat; never trims or normalizes passwords. */
    fun registration(email: String, password: String, repeatedPassword: String): AuthInputIssue? =
        email(email) ?: when {
            password.codePointsCount() < 8 -> AuthInputIssue.PASSWORD_TOO_SHORT
            password != repeatedPassword -> AuthInputIssue.PASSWORD_MISMATCH
            else -> null
        }

    /** Accepts exactly six ASCII digits, including leading zeroes. */
    fun confirmation(email: String, code: String): AuthInputIssue? =
        email(email) ?: if (code.length != 6 || code.any { it !in '0'..'9' }) AuthInputIssue.CODE else null

    // Count Unicode code points consistently across Kotlin and Swift clients, preserving the password bytes.
    private fun String.codePointsCount(): Int = indices.count { index ->
        !(this[index].isLowSurrogate() && index > 0 && this[index - 1].isHighSurrogate())
    }
}
