package com.ndynagn.kmp.news.feature.auth.domain

import kotlinx.coroutines.flow.StateFlow

/** Account identity; tokens never enter presentation models. */
data class AuthUser(val id: String, val email: String)

/** Expected failures, safe to expose through the Swift bridge without provider messages. */
enum class AuthFailure {
    INVALID_CREDENTIALS,
    EMAIL_UNCONFIRMED,
    WEAK_PASSWORD,
    INVALID_CODE,
    RATE_LIMITED,
    NETWORK,
    SERVICE,
    STORAGE,
    NOT_CONFIGURED,
    SESSION_EXPIRED,
}

/** Session availability, including retryable restoration errors that retain stored credentials. */
sealed interface AuthSession {
    data object Restoring : AuthSession
    data object Guest : AuthSession
    data class Authenticated(val user: AuthUser) : AuthSession
    data class Unavailable(val failure: AuthFailure) : AuthSession
}

/** Null failure denotes success; logout can succeed locally with a remote failure. */
data class AuthResult(val failure: AuthFailure? = null)

/**
 * Application-owned account boundary. Calls belong to caller coroutines; cancellation propagates.
 * Restore/refresh serialize token rotation. Logout invalidates pending session commits immediately.
 * State observation performs no I/O and emits expected failures as values.
 */
interface AuthRepository {
    /** Current account availability; collection never starts a request. */
    val session: StateFlow<AuthSession>

    /** Reads saved credentials, refreshes if needed and validates the current user; transient failures retain storage. */
    suspend fun restore(): AuthResult

    /** Exchanges email/password for a session and saves it before publishing authenticated state. */
    suspend fun signIn(email: String, password: String): AuthResult

    /** Requests signup confirmation without creating a local session or revealing account existence. */
    suspend fun register(email: String, password: String): AuthResult

    /** Verifies a signup code exactly as entered, preserving leading zeroes, then persists its session. */
    suspend fun confirm(email: String, code: String): AuthResult

    /** Explicitly requests another signup code; success is not proof of mailbox delivery. */
    suspend fun resend(email: String): AuthResult

    /** Creates an isolated, caller-owned recovery flow; its credentials never enter presentation. */
    fun passwordRecovery(): PasswordRecovery

    /** Clears this device first, then attempts remote local-scope logout; a failure can describe remote revocation only. */
    suspend fun signOut(): AuthResult
}
