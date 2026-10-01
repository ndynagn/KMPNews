package com.ndynagn.kmp.news.feature.auth.data

import com.ndynagn.kmp.news.feature.auth.domain.AuthFailure
import com.ndynagn.kmp.news.feature.auth.domain.AuthRepository
import com.ndynagn.kmp.news.feature.auth.domain.AuthResult
import com.ndynagn.kmp.news.feature.auth.domain.AuthSession
import com.ndynagn.kmp.news.feature.auth.domain.AuthUser
import com.ndynagn.kmp.news.feature.auth.domain.PasswordRecovery
import com.ndynagn.kmp.news.feature.auth.domain.PasswordResetResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal fun interface AuthClock {
    fun nowSeconds(): Long
}

internal class PersistentAuthRepository(
    private val remote: AuthRemoteSource,
    private val storage: AuthSessionStorage,
    private val clock: AuthClock,
) : AuthRepository {
    private val mutableSession = MutableStateFlow<AuthSession>(AuthSession.Restoring)
    override val session = mutableSession.asStateFlow()
    private val sessionLock = Mutex()
    private val restoreLock = Mutex()
    private var generation = 0L
    private var stored: StoredAuthSession? = null
    private val recoveries = mutableSetOf<Recovery>()

    override suspend fun restore(): AuthResult = restoreLock.withLock {
        val version = sessionLock.withLock { generation }
        val read = withContext(Dispatchers.Default) { storage.read() }
        if (read.failed) return@withLock unavailable(version, AuthFailure.STORAGE)
        val cached = try {
            read.value?.let { authJson.decodeFromString<StoredAuthSession>(it) }
        } catch (_: Exception) {
            return@withLock unavailable(version, AuthFailure.STORAGE)
        }
        if (cached == null) {
            sessionLock.withLock {
                if (generation == version) mutableSession.value = AuthSession.Guest
            }
            return@withLock AuthResult()
        }
        sessionLock.withLock { if (generation == version) stored = cached }
        var active = cached
        if (cached.expiresAt <= clock.nowSeconds() + 30) {
            when (val result = remote.refresh(cached.refreshToken)) {
                is AuthResponse.Failed -> return@withLock restorationFailure(version, result.failure)

                is AuthResponse.Success -> {
                    active = result.value.toStored()
                    val saved = commit(version, active, publish = false)
                    if (saved.failure != null) return@withLock saved
                }
            }
        }
        var user = remote.user(active.accessToken)
        if (user is AuthResponse.Failed && user.failure == AuthFailure.SESSION_EXPIRED && active == cached) {
            // Server expiry/revocation can precede the local clock; try the refresh credential once.
            when (val refresh = remote.refresh(active.refreshToken)) {
                is AuthResponse.Failed -> return@withLock restorationFailure(version, refresh.failure)

                is AuthResponse.Success -> {
                    active = refresh.value.toStored()
                    val saved = commit(version, active, publish = false)
                    if (saved.failure != null) return@withLock saved
                    user = remote.user(active.accessToken)
                }
            }
        }
        when (val verified = user) {
            is AuthResponse.Failed -> restorationFailure(version, verified.failure)
            is AuthResponse.Success -> commit(version, active.copy(user = verified.value))
        }
    }

    override suspend fun signIn(email: String, password: String): AuthResult = authenticate {
        remote.signIn(email, password)
    }

    override suspend fun register(email: String, password: String): AuthResult =
        remote.register(email, password).result()

    override suspend fun confirm(email: String, code: String): AuthResult = authenticate { remote.confirm(email, code) }

    override suspend fun resend(email: String): AuthResult = remote.resend(email).result()

    override fun passwordRecovery(): PasswordRecovery = Recovery()

    override suspend fun signOut(): AuthResult {
        // The generation changes before HTTP, so a delayed restore cannot resurrect this account.
        val (previous, recoveryTokens, cleared) = sessionLock.withLock {
            generation++
            val tokens = recoveries.mapNotNull { it.invalidate() }
            recoveries.clear()
            val value = stored
            val cleared = withContext(NonCancellable + Dispatchers.Default) { storage.write(null) }
            if (!cleared) {
                mutableSession.value = AuthSession.Unavailable(AuthFailure.STORAGE)
            } else {
                stored = null
                mutableSession.value = AuthSession.Guest
            }
            Triple(value, tokens, cleared)
        }
        recoveryTokens.forEach { revokeRecovery(it) }
        if (!cleared) return AuthResult(AuthFailure.STORAGE)
        return previous?.let { remote.logout(it.accessToken).result() } ?: AuthResult()
    }

    private suspend fun authenticate(request: suspend () -> AuthResponse<AuthTokenDto>): AuthResult {
        val version = sessionLock.withLock { ++generation }
        // A foreground restore must not read an old/empty snapshot while login is committing.
        return restoreLock.withLock {
            if (sessionLock.withLock { generation != version }) {
                return@withLock AuthResult(AuthFailure.SESSION_EXPIRED)
            }
            when (val response = request()) {
                is AuthResponse.Failed -> AuthResult(response.failure)
                is AuthResponse.Success -> commit(version, response.value.toStored())
            }
        }
    }

    private suspend fun commit(
        version: Long,
        value: StoredAuthSession,
        publish: Boolean = true,
        isCurrent: () -> Boolean = { true },
        onSaved: () -> Unit = {},
    ): AuthResult {
        currentCoroutineContext().ensureActive()
        return sessionLock.withLock {
            if (generation != version || !isCurrent()) return@withLock AuthResult(AuthFailure.SESSION_EXPIRED)
            withContext(NonCancellable + Dispatchers.Default) {
                if (!storage.write(authJson.encodeToString(value))) {
                    mutableSession.value = AuthSession.Unavailable(AuthFailure.STORAGE)
                    AuthResult(AuthFailure.STORAGE)
                } else {
                    stored = value
                    onSaved()
                    if (publish) {
                        mutableSession.value =
                            AuthSession.Authenticated(AuthUser(value.user.id, value.user.email))
                    }
                    AuthResult()
                }
            }
        }
    }

    /** Recovery fields share sessionLock so logout, cancellation and session publication have one ordering. */
    private inner class Recovery : PasswordRecovery {
        private var closed = false
        private var operation = 0L
        private var accountVersion = 0L
        private var credentials: StoredAuthSession? = null

        override suspend fun requestCode(email: String): AuthResult {
            if (sessionLock.withLock { closed }) return AuthResult(AuthFailure.SESSION_EXPIRED)
            return remote.requestRecovery(email).result()
        }

        override suspend fun verifyCode(email: String, code: String): AuthResult {
            val (version, attempt) = sessionLock.withLock {
                if (closed) return AuthResult(AuthFailure.SESSION_EXPIRED)
                recoveries.add(this)
                generation to ++operation
            }
            var received: StoredAuthSession? = null
            try {
                when (val response = remote.verifyRecovery(email, code)) {
                    is AuthResponse.Failed -> return AuthResult(response.failure)
                    is AuthResponse.Success -> received = response.value.toStored()
                }
                currentCoroutineContext().ensureActive()
                val old = sessionLock.withLock {
                    if (closed || generation != version || operation != attempt) {
                        return AuthResult(AuthFailure.SESSION_EXPIRED)
                    }
                    val previous = credentials
                    credentials = received
                    received = null
                    accountVersion = version
                    previous
                }
                old?.let { revokeRecovery(it) }
                return AuthResult()
            } finally {
                received?.let { revokeRecovery(it) }
            }
        }

        override suspend fun resetPassword(password: String): PasswordResetResult = restoreLock.withLock {
            val (value, attempt, version) = sessionLock.withLock snapshot@{
                val value = credentials
                if (closed || value == null || accountVersion != generation) {
                    return@snapshot null
                }
                Triple(value, operation, accountVersion)
            } ?: return@withLock PasswordResetResult(AuthFailure.SESSION_EXPIRED)
            when (val updated = remote.resetPassword(value.accessToken, password)) {
                is AuthResponse.Failed -> {
                    if (updated.failure == AuthFailure.SESSION_EXPIRED) cancel()
                    PasswordResetResult(updated.failure)
                }

                is AuthResponse.Success -> {
                    // Server mutation has completed. A local persistence failure must not invite another reset.
                    val result = commit(
                        version,
                        value.copy(user = updated.value),
                        isCurrent = { !closed && operation == attempt && credentials == value },
                        onSaved = {
                            invalidate()
                            recoveries.remove(this)
                        },
                    )
                    if (result.failure != null) cancel()
                    PasswordResetResult(result.failure, passwordChanged = true)
                }
            }
        }

        override suspend fun cancel(): AuthResult {
            val token = sessionLock.withLock {
                recoveries.remove(this)
                invalidate()
            }
            return token?.let { revokeRecovery(it) } ?: AuthResult()
        }

        // Called only under sessionLock; returning a token transfers revocation ownership to the caller.
        fun invalidate(): StoredAuthSession? {
            closed = true
            operation++
            return credentials.also { credentials = null }
        }
    }

    private suspend fun revokeRecovery(value: StoredAuthSession): AuthResult = withContext(NonCancellable) {
        withTimeoutOrNull(5_000) { remote.logout(value.accessToken).result() } ?: AuthResult(AuthFailure.NETWORK)
    }

    private suspend fun restorationFailure(version: Long, failure: AuthFailure): AuthResult {
        if (failure != AuthFailure.SESSION_EXPIRED) return unavailable(version, failure)
        return sessionLock.withLock {
            if (generation == version) {
                if (!withContext(NonCancellable + Dispatchers.Default) { storage.write(null) }) {
                    mutableSession.value = AuthSession.Unavailable(AuthFailure.STORAGE)
                    return@withLock AuthResult(AuthFailure.STORAGE)
                }
                stored = null
                mutableSession.value = AuthSession.Guest
            }
            AuthResult(failure)
        }
    }

    private suspend fun unavailable(version: Long, failure: AuthFailure): AuthResult = sessionLock.withLock {
        if (generation == version) mutableSession.value = AuthSession.Unavailable(failure)
        AuthResult(failure)
    }

    private fun AuthTokenDto.toStored() =
        StoredAuthSession(accessToken, refreshToken, clock.nowSeconds() + expiresIn, user)

    private fun AuthResponse<*>.result() = AuthResult((this as? AuthResponse.Failed)?.failure)
}
