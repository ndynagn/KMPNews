package com.ndynagn.kmp.news.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ndynagn.kmp.news.feature.auth.domain.AuthInputValidator
import com.ndynagn.kmp.news.feature.auth.domain.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Owns one form flow. Clearing its navigation owner cancels requests and the resend timer. */
internal class AuthViewModel(
    private val authRepository: AuthRepository,
    initialStep: AuthStep,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(AuthUiState(initialStep))
    val state = mutableState.asStateFlow()
    private var countdown: Job? = null

    fun onEvent(event: AuthEvent) {
        val current = state.value
        if (current.isBusy) return
        mutableState.value = when (event) {
            is AuthEvent.EmailChanged -> current.copy(email = event.value, fieldError = null, failure = null)

            is AuthEvent.PasswordChanged -> current.copy(password = event.value, fieldError = null, failure = null)

            is AuthEvent.RepeatPasswordChanged -> current.copy(repeatPassword = event.value, fieldError = null)

            is AuthEvent.CodeChanged -> current.copy(
                code = event.value.filter {
                    it in '0'..'9'
                }.take(6),
                fieldError = null,
                failure = null,
            )

            AuthEvent.Register -> AuthUiState(AuthStep.REGISTER, email = current.email)

            AuthEvent.ConfirmEmail -> AuthUiState(AuthStep.CONFIRM, email = current.email.trim())

            AuthEvent.Submit, AuthEvent.Resend -> current
        }
        if (event == AuthEvent.Submit || event == AuthEvent.Resend) submit(event == AuthEvent.Resend)
    }

    override fun onCleared() {
        mutableState.value = AuthUiState(state.value.step)
    }

    private fun submit(resend: Boolean) {
        val current = state.value
        if (resend && current.resendSeconds > 0) return
        val email = current.email.trim()
        val validator = AuthInputValidator()
        val error = when {
            resend -> validator.email(email)
            current.step == AuthStep.SIGN_IN -> validator.login(email, current.password)
            current.step == AuthStep.REGISTER -> validator.registration(email, current.password, current.repeatPassword)
            else -> validator.confirmation(email, current.code)
        }
        if (error != null) {
            mutableState.value = current.copy(fieldError = error)
            return
        }
        mutableState.value = current.copy(isBusy = true, failure = null, fieldError = null)
        viewModelScope.launch {
            val result = when {
                resend -> authRepository.resend(email)
                current.step == AuthStep.SIGN_IN -> authRepository.signIn(email, current.password)
                current.step == AuthStep.REGISTER -> authRepository.register(email, current.password)
                else -> authRepository.confirm(email, current.code)
            }
            if (result.failure != null) {
                mutableState.value = state.value.copy(isBusy = false, failure = result.failure)
            } else if (resend || current.step == AuthStep.REGISTER) {
                mutableState.value = AuthUiState(AuthStep.CONFIRM, email = email, resendSeconds = 60)
                startCountdown()
            } else {
                mutableState.value = AuthUiState(current.step, isComplete = true)
            }
        }
    }

    private fun startCountdown() {
        countdown?.cancel()
        val deadline = nowMillis() + 60_000
        countdown = viewModelScope.launch {
            while (state.value.resendSeconds > 0) {
                delay(1_000)
                val remaining = ((deadline - nowMillis() + 999) / 1_000).coerceIn(0, 60).toInt()
                mutableState.value = state.value.copy(resendSeconds = remaining)
            }
        }
    }
}
