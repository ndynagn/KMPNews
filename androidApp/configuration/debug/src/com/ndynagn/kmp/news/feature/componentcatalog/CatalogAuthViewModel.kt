package com.ndynagn.kmp.news.feature.componentcatalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class CatalogAuthStep { LOGIN, REGISTER, CONFIRM, RECOVERY }

internal enum class CatalogOutcome { SUCCESS, ERROR, TEMPORARY_ERROR, SLOW }

internal enum class CatalogMessage { INVALID_CODE, REQUEST_ERROR, TEMPORARY_ERROR, CODE_SENT, RECOVERY_SENT }

internal enum class CatalogAuthOperation { SUBMIT, RESEND }

internal data class CatalogAuthState(
    val path: List<CatalogAuthStep>,
    val email: String = "",
    val password: String = "",
    val repeatedPassword: String = "",
    val code: String = "",
    val outcome: CatalogOutcome = CatalogOutcome.SUCCESS,
    val operation: CatalogAuthOperation? = null,
    val message: CatalogMessage? = null,
    val canRetryCode: Boolean = false,
    val resendSeconds: Int = 0,
    val isComplete: Boolean = false,
) {
    val isBusy: Boolean get() = operation != null
    val step: CatalogAuthStep get() = path.last()
    val canSubmit: Boolean
        get() = !isBusy && !isComplete && when (step) {
            CatalogAuthStep.LOGIN -> email.isNotBlank() && password.isNotEmpty()
            CatalogAuthStep.REGISTER -> email.isNotBlank() && password.isNotEmpty() && repeatedPassword == password
            CatalogAuthStep.CONFIRM -> code.length == 6
            CatalogAuthStep.RECOVERY -> email.isNotBlank()
        }
}

/** Offline fixture. All requests use an injected delay; no repository or persistence is reachable. */
internal class CatalogAuthViewModel(
    initialStep: CatalogAuthStep,
    private val nowMilliseconds: () -> Long = { System.nanoTime() / 1_000_000 },
    private val pause: suspend (Long) -> Unit = { delay(it) },
) : ViewModel() {
    private val initial = CatalogAuthState(
        path = listOf(initialStep),
        email = if (initialStep == CatalogAuthStep.CONFIRM) "reader@example.test" else "",
    )
    private val mutableState = MutableStateFlow(initial)
    val state = mutableState.asStateFlow()
    private var request: Job? = null
    private var ticker: Job? = null
    private var generation = 0
    private val deadlines = mutableMapOf<String, Long>()

    fun setEmail(value: String) {
        if (state.value.isBusy) return

        mutableState.value = state.value.copy(email = value, message = null)
        updateCountdown()
        ensureCountdownTicker()
    }

    fun setPassword(value: String) {
        if (!state.value.isBusy) mutableState.value = state.value.copy(password = value, message = null)
    }

    fun setRepeatedPassword(value: String) {
        if (!state.value.isBusy) mutableState.value = state.value.copy(repeatedPassword = value, message = null)
    }

    fun setOutcome(value: CatalogOutcome) {
        if (!state.value.isBusy) mutableState.value = state.value.copy(outcome = value)
    }

    fun setCode(value: String) {
        val current = state.value
        if (current.isBusy || current.isComplete) return

        val code = value.filter { it in '0'..'9' }.take(6)
        if (code == current.code) return

        mutableState.value = current.copy(code = code, message = null, canRetryCode = false)
        if (current.step == CatalogAuthStep.CONFIRM && code.length == 6) submit()
    }

    fun navigate(step: CatalogAuthStep) {
        if (state.value.isBusy) return

        mutableState.value = clearSecrets(state.value).copy(path = state.value.path + step, message = null)
        updateCountdown()
        ensureCountdownTicker()
    }

    /** Returns false at the flow root; the host then closes the entire demonstration. */
    fun back(): Boolean {
        cancelRequest()
        if (state.value.path.size == 1) return false

        mutableState.value = clearSecrets(state.value).copy(path = state.value.path.dropLast(1), message = null)
        updateCountdown()
        ensureCountdownTicker()
        return true
    }

    fun submit() {
        if (state.value.canSubmit) perform(resending = false)
    }

    fun resend() {
        updateCountdown()
        val current = state.value
        if (!current.isBusy && !current.isComplete && current.step == CatalogAuthStep.CONFIRM &&
            current.resendSeconds == 0
        ) {
            perform(resending = true)
        }
    }

    fun close() {
        cancelRequest()
        ticker?.cancel()
        ticker = null
        deadlines.clear()
        mutableState.value = initial.copy(email = "")
    }

    private fun perform(resending: Boolean) {
        val submitted = state.value
        val revision = generation
        mutableState.value = submitted.copy(
            operation = if (resending) CatalogAuthOperation.RESEND else CatalogAuthOperation.SUBMIT,
            message = null,
            canRetryCode = false,
        )
        request = viewModelScope.launch {
            pause(if (submitted.outcome == CatalogOutcome.SLOW) 8_000 else 700)
            if (revision != generation) return@launch

            mutableState.value = state.value.copy(operation = null)
            when (submitted.outcome) {
                CatalogOutcome.ERROR -> mutableState.value = state.value.copy(
                    message = if (submitted.step == CatalogAuthStep.CONFIRM && !resending) {
                        CatalogMessage.INVALID_CODE
                    } else {
                        CatalogMessage.REQUEST_ERROR
                    },
                )

                CatalogOutcome.TEMPORARY_ERROR -> mutableState.value = state.value.copy(
                    message = CatalogMessage.TEMPORARY_ERROR,
                    canRetryCode = submitted.step == CatalogAuthStep.CONFIRM && !resending,
                )

                CatalogOutcome.SUCCESS, CatalogOutcome.SLOW -> {
                    if (resending) {
                        mutableState.value = state.value.copy(code = "", message = CatalogMessage.CODE_SENT)
                        startCountdown()
                    } else {
                        when (submitted.step) {
                            CatalogAuthStep.LOGIN, CatalogAuthStep.CONFIRM -> {
                                mutableState.value = clearSecrets(state.value).copy(isComplete = true)
                            }

                            CatalogAuthStep.REGISTER -> {
                                navigate(CatalogAuthStep.CONFIRM)
                                startCountdown()
                            }

                            CatalogAuthStep.RECOVERY -> {
                                mutableState.value = state.value.copy(message = CatalogMessage.RECOVERY_SENT)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startCountdown() {
        deadlines[state.value.email] = nowMilliseconds() + 60_000
        updateCountdown()
        ensureCountdownTicker()
    }

    private fun ensureCountdownTicker() {
        if (state.value.resendSeconds == 0 || ticker?.isActive == true) return

        ticker = viewModelScope.launch {
            while (state.value.resendSeconds > 0) {
                delay(1_000)
                updateCountdown()
            }
        }
    }

    private fun updateCountdown() {
        val remaining = ((deadlines[state.value.email] ?: 0) - nowMilliseconds()).coerceAtLeast(0)
        mutableState.value = state.value.copy(resendSeconds = ((remaining + 999) / 1_000).toInt())
    }

    private fun cancelRequest() {
        generation += 1
        request?.cancel()
        request = null
        mutableState.value = state.value.copy(operation = null)
    }

    private fun clearSecrets(state: CatalogAuthState) = state.copy(
        password = "",
        repeatedPassword = "",
        code = "",
        canRetryCode = false,
    )

    override fun onCleared() {
        close()
        super.onCleared()
    }
}
