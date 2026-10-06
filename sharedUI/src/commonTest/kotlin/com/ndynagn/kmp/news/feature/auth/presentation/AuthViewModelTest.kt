package com.ndynagn.kmp.news.feature.auth.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ndynagn.kmp.news.feature.auth.domain.AuthFailure
import com.ndynagn.kmp.news.feature.auth.domain.AuthInputIssue
import com.ndynagn.kmp.news.feature.auth.domain.AuthRepository
import com.ndynagn.kmp.news.feature.auth.domain.AuthResult
import com.ndynagn.kmp.news.feature.auth.domain.AuthSession
import com.ndynagn.kmp.news.feature.auth.domain.PasswordRecovery
import com.ndynagn.kmp.news.feature.profile.domain.ProfileDetails
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @Test
    fun registrationValidatesAndMovesToConfirmationWithCooldown() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val auth = FakeFormAuth()
            val model = ViewModelProvider.create(
                store,
                viewModelFactory {
                    initializer { AuthViewModel(auth, AuthStep.REGISTER) { testScheduler.currentTime } }
                },
            )[AuthViewModel::class]
            model.onEvent(AuthEvent.Submit)
            assertEquals(AuthInputIssue.EMAIL, model.state.value.fieldError)
            model.onEvent(AuthEvent.EmailChanged("reader@example.test"))
            model.onEvent(AuthEvent.PasswordChanged("password"))
            model.onEvent(AuthEvent.RepeatPasswordChanged("mismatch"))
            model.onEvent(AuthEvent.Submit)
            assertEquals(AuthInputIssue.PASSWORD_MISMATCH, model.state.value.fieldError)
            model.onEvent(AuthEvent.RepeatPasswordChanged("password"))
            model.onEvent(AuthEvent.Submit)
            runCurrent()
            assertEquals(AuthStep.CONFIRM, model.state.value.step)
            assertEquals("", model.state.value.password)
            model.onEvent(AuthEvent.Resend)
            runCurrent()
            assertEquals(0, auth.resends)
            advanceTimeBy(60_000)
            runCurrent()
            model.onEvent(AuthEvent.Resend)
            runCurrent()
            assertEquals(1, auth.resends)
            assertEquals(60, model.state.value.resendSeconds)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun pendingLoginRejectsDuplicateAndClosingCancelsIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val auth = FakeFormAuth().apply { gate = CompletableDeferred() }
            val model = ViewModelProvider.create(
                store,
                viewModelFactory {
                    initializer { AuthViewModel(auth, AuthStep.SIGN_IN) }
                },
            )[AuthViewModel::class]
            model.onEvent(AuthEvent.EmailChanged("reader@example.test"))
            model.onEvent(AuthEvent.PasswordChanged("password"))
            model.onEvent(AuthEvent.Submit)
            model.onEvent(AuthEvent.Submit)
            runCurrent()
            assertEquals(1, auth.logins)
            assertTrue(model.state.value.isBusy)
            store.clear()
            runCurrent()
            assertTrue(auth.cancelled)
            assertFalse(model.state.value.isComplete)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}

private class FakeFormAuth : AuthRepository {
    override val session = MutableStateFlow<AuthSession>(AuthSession.Guest)
    var resends = 0
    var logins = 0
    var cancelled = false
    var gate: CompletableDeferred<Unit>? = null

    override fun passwordRecovery(): PasswordRecovery = error("Recovery is not used by these Compose form tests")

    override suspend fun restore() = AuthResult()

    override suspend fun signIn(email: String, password: String): AuthResult {
        logins++
        try {
            gate?.await()
        } finally {
            cancelled = gate?.isCompleted == false
        }
        return AuthResult(AuthFailure.EMAIL_UNCONFIRMED)
    }

    override suspend fun register(email: String, password: String) = AuthResult()

    override suspend fun registerWithProfile(email: String, password: String, details: ProfileDetails) =
        register(email, password)

    override suspend fun confirm(email: String, code: String) = AuthResult()

    override suspend fun resend(email: String): AuthResult {
        resends++
        return AuthResult()
    }

    override suspend fun signOut() = AuthResult()
}
