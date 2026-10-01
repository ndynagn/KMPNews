package com.ndynagn.kmp.news.feature.auth

import com.ndynagn.kmp.news.feature.auth.data.AuthClock
import com.ndynagn.kmp.news.feature.auth.data.AuthRemoteSource
import com.ndynagn.kmp.news.feature.auth.data.AuthResponse
import com.ndynagn.kmp.news.feature.auth.data.AuthSessionStorage
import com.ndynagn.kmp.news.feature.auth.data.AuthStorageRead
import com.ndynagn.kmp.news.feature.auth.data.AuthTokenDto
import com.ndynagn.kmp.news.feature.auth.data.AuthUserDto
import com.ndynagn.kmp.news.feature.auth.data.PersistentAuthRepository
import com.ndynagn.kmp.news.feature.auth.data.StoredAuthSession
import com.ndynagn.kmp.news.feature.auth.data.authJson
import com.ndynagn.kmp.news.feature.auth.domain.AuthFailure
import com.ndynagn.kmp.news.feature.auth.domain.AuthSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRepositoryTest {
    @Test
    fun registrationRequiresConfirmationAndPreservesLeadingZeroCode() = runTest {
        val remote = FakeAuthRemote()
        val storage = FakeAuthStorage()
        val repository = PersistentAuthRepository(remote, storage, AuthClock { 100 })
        repository.restore()

        assertNull(repository.register("reader@example.test", " spaced password ").failure)
        assertEquals(AuthSession.Guest, repository.session.value)
        assertNull(storage.value)
        assertNull(repository.confirm("reader@example.test", "012345").failure)

        assertEquals("012345", remote.code)
        assertIs<AuthSession.Authenticated>(repository.session.value)
        assertNotNull(storage.value)
        assertTrue(storage.value?.contains("spaced password") == false)
    }

    @Test
    fun restartedRepositoryRestoresAndConcurrentRefreshRotatesOnlyOnce() = runTest {
        val remote = FakeAuthRemote()
        val storage = FakeAuthStorage(expiredSession())
        val repository = PersistentAuthRepository(remote, storage, AuthClock { 100 })

        val first = async { repository.restore() }
        val second = async { repository.restore() }
        first.await()
        second.await()

        assertEquals(1, remote.refreshes)
        assertIs<AuthSession.Authenticated>(repository.session.value)
        val restarted = PersistentAuthRepository(remote, storage, AuthClock { 110 })
        restarted.restore()
        assertIs<AuthSession.Authenticated>(restarted.session.value)
        assertEquals(1, remote.refreshes)
    }

    @Test
    fun transientRefreshFailurePreservesCredentialsAndRetryRecovers() = runTest {
        val remote = FakeAuthRemote().apply { refreshFailure = AuthFailure.NETWORK }
        val original = expiredSession()
        val storage = FakeAuthStorage(original)
        val repository = PersistentAuthRepository(remote, storage, AuthClock { 100 })

        assertEquals(AuthFailure.NETWORK, repository.restore().failure)
        assertEquals(original, storage.value)
        assertIs<AuthSession.Unavailable>(repository.session.value)
        remote.refreshFailure = null
        assertNull(repository.restore().failure)
        assertIs<AuthSession.Authenticated>(repository.session.value)
    }

    @Test
    fun invalidRefreshRemovesSessionButStorageFailureIsRetryable() = runTest {
        val remote = FakeAuthRemote().apply { refreshFailure = AuthFailure.SESSION_EXPIRED }
        val storage = FakeAuthStorage(expiredSession())
        val repository = PersistentAuthRepository(remote, storage, AuthClock { 100 })
        storage.failWrite = true

        assertEquals(AuthFailure.STORAGE, repository.restore().failure)
        assertNotNull(storage.value)
        storage.failWrite = false
        assertEquals(AuthFailure.SESSION_EXPIRED, repository.restore().failure)
        assertNull(storage.value)
        assertEquals(AuthSession.Guest, repository.session.value)
    }

    @Test
    fun logoutPreventsDelayedRefreshFromRestoringAccount() = runTest {
        val remote = FakeAuthRemote().apply { refreshGate = CompletableDeferred() }
        val storage = FakeAuthStorage(expiredSession())
        val repository = PersistentAuthRepository(remote, storage, AuthClock { 100 })
        val restore = async { repository.restore() }
        remote.refreshStarted.await()

        repository.signOut()
        remote.refreshGate?.complete(Unit)
        restore.await()

        assertNull(storage.value)
        assertEquals(AuthSession.Guest, repository.session.value)
    }

    @Test
    fun closingLoginCancelsWithoutPublishingSession() = runTest {
        val remote = FakeAuthRemote().apply { loginGate = CompletableDeferred() }
        val storage = FakeAuthStorage()
        val repository = PersistentAuthRepository(remote, storage, AuthClock { 100 })
        repository.restore()
        val login = launch { repository.signIn("reader@example.test", "password") }
        remote.loginStarted.await()

        login.cancelAndJoin()

        assertNull(storage.value)
        assertEquals(AuthSession.Guest, repository.session.value)
    }

    @Test
    fun offlineLogoutClearsLocalSessionAndReportsRemoteFailure() = runTest {
        val remote = FakeAuthRemote().apply { logoutFailure = AuthFailure.NETWORK }
        val storage = FakeAuthStorage()
        val repository = PersistentAuthRepository(remote, storage, AuthClock { 100 })
        repository.signIn("reader@example.test", "password")

        assertEquals(AuthFailure.NETWORK, repository.signOut().failure)
        assertNull(storage.value)
        assertEquals(AuthSession.Guest, repository.session.value)
    }

    @Test
    fun rejectedAccessTokenRefreshesBeforeDiscardingSession() = runTest {
        val remote = FakeAuthRemote().apply { rejectOldAccess = true }
        val storage = FakeAuthStorage(
            authJson.encodeToString(
                StoredAuthSession("old-access", "old-refresh", 5000, AuthUserDto("reader", "reader@example.test")),
            ),
        )
        val repository = PersistentAuthRepository(remote, storage, AuthClock { 100 })

        assertNull(repository.restore().failure)
        assertEquals(1, remote.refreshes)
        assertIs<AuthSession.Authenticated>(repository.session.value)
    }

    @Test
    fun foregroundRestoreDuringLoginObservesTheCommittedAccount() = runTest {
        val remote = FakeAuthRemote().apply { loginGate = CompletableDeferred() }
        val storage = FakeAuthStorage()
        val repository = PersistentAuthRepository(remote, storage, AuthClock { 100 })
        repository.restore()
        val login = async { repository.signIn("reader@example.test", "password") }
        remote.loginStarted.await()
        val restore = async { repository.restore() }
        remote.loginGate?.complete(Unit)

        assertNull(login.await().failure)
        assertNull(restore.await().failure)
        assertIs<AuthSession.Authenticated>(repository.session.value)
        assertNotNull(storage.value)
    }

    @Test
    fun unreadableStorageDoesNotBecomeGuestAndFailedSaveDoesNotAuthorize() = runTest {
        val storage = FakeAuthStorage().apply {
            failRead = true
            failWrite = true
        }
        val repository = PersistentAuthRepository(FakeAuthRemote(), storage, AuthClock { 100 })

        assertEquals(AuthFailure.STORAGE, repository.restore().failure)
        assertIs<AuthSession.Unavailable>(repository.session.value)
        assertEquals(AuthFailure.STORAGE, repository.signIn("reader@example.test", "password").failure)
        assertIs<AuthSession.Unavailable>(repository.session.value)
    }

    @Test
    fun recoveryRemainsGuestUntilPasswordAndSessionAreSaved() = runTest {
        val remote = FakeAuthRemote()
        val storage = FakeAuthStorage()
        val repository = PersistentAuthRepository(remote, storage, AuthClock { 100 })
        repository.restore()
        val recovery = repository.passwordRecovery()

        assertNull(recovery.requestCode("reader@example.test").failure)
        assertNull(recovery.verifyCode("reader@example.test", "012345").failure)
        assertEquals("012345", remote.code)
        assertNull(storage.value)
        assertEquals(AuthSession.Guest, repository.session.value)
        repository.restore()
        assertEquals(AuthSession.Guest, repository.session.value)
        val result = recovery.resetPassword(" new password ")

        assertNull(result.failure)
        assertTrue(result.passwordChanged)
        assertEquals(" new password ", remote.password)
        assertIs<AuthSession.Authenticated>(repository.session.value)
        assertNotNull(storage.value)
        recovery.cancel()
        assertEquals(0, remote.logouts)
    }

    @Test
    fun failedRecoveryStorageReportsThatPasswordAlreadyChanged() = runTest {
        val remote = FakeAuthRemote()
        val storage = FakeAuthStorage().apply { failWrite = true }
        val repository = PersistentAuthRepository(remote, storage, AuthClock { 100 })
        val recovery = repository.passwordRecovery()
        recovery.verifyCode("reader@example.test", "012345")

        val result = recovery.resetPassword("new password")

        assertTrue(result.passwordChanged)
        assertEquals(AuthFailure.STORAGE, result.failure)
        assertNull(storage.value)
        assertEquals(1, remote.logouts)
        assertEquals(AuthFailure.SESSION_EXPIRED, recovery.resetPassword("another password").failure)
    }

    @Test
    fun cancellationRevokesLateRecoveryAndCannotAuthorize() = runTest {
        val remote = FakeAuthRemote().apply { recoveryGate = CompletableDeferred() }
        val storage = FakeAuthStorage()
        val repository = PersistentAuthRepository(remote, storage, AuthClock { 100 })
        repository.restore()
        val recovery = repository.passwordRecovery()
        val pending = async { recovery.verifyCode("reader@example.test", "012345") }
        remote.recoveryStarted.await()

        recovery.cancel()
        remote.recoveryGate?.complete(Unit)
        assertEquals(AuthFailure.SESSION_EXPIRED, pending.await().failure)
        assertEquals(1, remote.logouts)
        assertNull(storage.value)
        assertEquals(AuthSession.Guest, repository.session.value)
    }

    @Test
    fun cancelledVerificationRevokesNonCooperativeResponse() = runTest {
        val remote = FakeAuthRemote().apply {
            recoveryGate = CompletableDeferred()
            ignoreRecoveryCancellation = true
        }
        val storage = FakeAuthStorage()
        val repository = PersistentAuthRepository(remote, storage, AuthClock { 100 })
        repository.restore()
        val recovery = repository.passwordRecovery()
        val pending = launch { recovery.verifyCode("reader@example.test", "012345") }
        remote.recoveryStarted.await()

        pending.cancel()
        remote.recoveryGate?.complete(Unit)
        pending.join()

        assertEquals(1, remote.logouts)
        assertNull(storage.value)
        assertEquals(AuthSession.Guest, repository.session.value)
        assertEquals(AuthFailure.SESSION_EXPIRED, recovery.resetPassword("new password").failure)
        recovery.cancel()
    }

    @Test
    fun logoutDuringPasswordUpdatePreventsSessionCommit() = runTest {
        val remote = FakeAuthRemote().apply { resetGate = CompletableDeferred() }
        val storage = FakeAuthStorage()
        val repository = PersistentAuthRepository(remote, storage, AuthClock { 100 })
        repository.restore()
        val recovery = repository.passwordRecovery()
        recovery.verifyCode("reader@example.test", "012345")
        val pending = async { recovery.resetPassword("new password") }
        remote.resetStarted.await()

        repository.signOut()
        remote.resetGate?.complete(Unit)
        assertEquals(AuthFailure.SESSION_EXPIRED, pending.await().failure)
        assertNull(storage.value)
        assertEquals(AuthSession.Guest, repository.session.value)
    }

    @Test
    fun failedPasswordUpdateCanRetryWithoutReenteringCode() = runTest {
        val remote = FakeAuthRemote().apply { resetFailure = AuthFailure.NETWORK }
        val repository = PersistentAuthRepository(remote, FakeAuthStorage(), AuthClock { 100 })
        repository.restore()
        val recovery = repository.passwordRecovery()
        recovery.verifyCode("reader@example.test", "012345")

        val failed = recovery.resetPassword("new password")
        assertEquals(AuthFailure.NETWORK, failed.failure)
        assertEquals(false, failed.passwordChanged)
        assertEquals(AuthSession.Guest, repository.session.value)
        remote.resetFailure = null
        assertNull(recovery.resetPassword("new password").failure)
        assertIs<AuthSession.Authenticated>(repository.session.value)
    }

    private fun expiredSession() = authJson.encodeToString(
        StoredAuthSession("old-access", "old-refresh", 1, AuthUserDto("reader", "reader@example.test")),
    )
}

private class FakeAuthStorage(var value: String? = null) : AuthSessionStorage {
    var failRead = false
    var failWrite = false
    override fun read() = AuthStorageRead(value, failRead)
    override fun write(value: String?): Boolean {
        if (failWrite) return false
        this.value = value
        return true
    }
}

private class FakeAuthRemote : AuthRemoteSource {
    val user = AuthUserDto("reader", "reader@example.test")
    var refreshes = 0
    var logouts = 0
    var password: String? = null
    var ignoreRecoveryCancellation = false
    var recoveryGate: CompletableDeferred<Unit>? = null
    var resetGate: CompletableDeferred<Unit>? = null
    var resetFailure: AuthFailure? = null
    val recoveryStarted = CompletableDeferred<Unit>()
    val resetStarted = CompletableDeferred<Unit>()
    var rejectOldAccess = false
    var code: String? = null
    var refreshFailure: AuthFailure? = null
    var logoutFailure: AuthFailure? = null
    var refreshGate: CompletableDeferred<Unit>? = null
    var loginGate: CompletableDeferred<Unit>? = null
    val refreshStarted = CompletableDeferred<Unit>()
    val loginStarted = CompletableDeferred<Unit>()
    private fun token() = AuthResponse.Success(AuthTokenDto("new-access", "new-refresh", 3600, user))
    override suspend fun signIn(email: String, password: String): AuthResponse<AuthTokenDto> {
        loginStarted.complete(Unit)
        loginGate?.await()
        return token()
    }
    override suspend fun register(email: String, password: String) = AuthResponse.Success(Unit)
    override suspend fun confirm(email: String, code: String): AuthResponse<AuthTokenDto> {
        this.code = code
        return token()
    }
    override suspend fun resend(email: String) = AuthResponse.Success(Unit)
    override suspend fun requestRecovery(email: String) = AuthResponse.Success(Unit)
    override suspend fun verifyRecovery(email: String, code: String): AuthResponse<AuthTokenDto> {
        this.code = code
        recoveryStarted.complete(Unit)
        if (ignoreRecoveryCancellation) withContext(NonCancellable) { recoveryGate?.await() } else recoveryGate?.await()
        return token()
    }
    override suspend fun resetPassword(token: String, password: String): AuthResponse<AuthUserDto> {
        this.password = password
        resetStarted.complete(Unit)
        resetGate?.await()
        return resetFailure?.let { AuthResponse.Failed(it) } ?: AuthResponse.Success(user)
    }
    override suspend fun refresh(token: String): AuthResponse<AuthTokenDto> {
        refreshes++
        refreshStarted.complete(Unit)
        refreshGate?.await()
        return refreshFailure?.let { AuthResponse.Failed(it) } ?: token()
    }
    override suspend fun user(token: String): AuthResponse<AuthUserDto> = if (rejectOldAccess &&
        token == "old-access"
    ) {
        AuthResponse.Failed(AuthFailure.SESSION_EXPIRED)
    } else {
        AuthResponse.Success(user)
    }
    override suspend fun logout(token: String): AuthResponse<Unit> {
        logouts++
        return logoutFailure?.let { AuthResponse.Failed(it) } ?: AuthResponse.Success(Unit)
    }
}
