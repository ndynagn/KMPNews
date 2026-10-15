package com.ndynagn.kmp.news.feature.auth.domain

/** Password may have changed even if local session persistence failed; prompt for login in that case. */
data class PasswordResetResult(val failure: AuthFailure? = null, val passwordChanged: Boolean = false)

/**
 * One form-owned recovery flow. Verified credentials are kept only in memory until reset succeeds.
 * Close on dismissal or when returning from the new-password step. A closed flow cannot be reused.
 * Logout invalidates this flow. Expected failures are values; caller cancellation propagates.
 */
interface PasswordRecovery {
    /** Neutral acknowledgement for known and unknown addresses; also used for resending after cooldown. */
    suspend fun requestCode(email: String): AuthResult

    /** Verifies a recovery code without publishing or persisting an authenticated application session. */
    suspend fun verifyCode(email: String, code: String): AuthResult

    /** Changes the password, then saves the session before publishing the authenticated account. */
    suspend fun resetPassword(password: String): PasswordResetResult

    /** Erases temporary credentials immediately, then attempts bounded remote revocation. Idempotent. */
    suspend fun cancel(): AuthResult
}
