package com.ndynagn.kmp.news.network

import com.ndynagn.kmp.news.feature.auth.domain.AuthFailure
import kotlinx.coroutines.flow.StateFlow

/** A login generation, not just a user ID: logout/login invalidates previously started work. */
internal data class AccountIdentity(val userId: String, val generation: Long)

/** Deliberately not a data class: diagnostic string output must not contain credentials. */
internal class AccountCredentials(val identity: AccountIdentity, val accessToken: String)

internal sealed interface CredentialsResult {
    class Ready(val credentials: AccountCredentials) : CredentialsResult
    data class Failed(val failure: AuthFailure) : CredentialsResult
}

/** Shared data infrastructure; credentials and recovery sessions never enter domain/presentation APIs. */
internal interface AccountSessionAccess {
    /** Cached identity may remain available during transient network failure for offline reads. */
    val account: StateFlow<AccountIdentity?>

    /** Refreshes serially when expired or when the server rejected this exact access token. */
    suspend fun credentials(identity: AccountIdentity, rejectedToken: String? = null): CredentialsResult

    /** Runs a short local transaction only while this login is current. Never perform network I/O in [commit]. */
    suspend fun commitIfCurrent(identity: AccountIdentity, commit: suspend () -> Unit): Boolean

    /** Invalidates only the rejected credentials, preserving any newer session. */
    suspend fun reject(identity: AccountIdentity, accessToken: String)
}
