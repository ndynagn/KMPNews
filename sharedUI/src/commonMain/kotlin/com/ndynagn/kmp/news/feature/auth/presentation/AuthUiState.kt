package com.ndynagn.kmp.news.feature.auth.presentation

import com.ndynagn.kmp.news.feature.auth.domain.AuthFailure
import com.ndynagn.kmp.news.feature.auth.domain.AuthInputIssue

internal enum class AuthStep { SIGN_IN, REGISTER, CONFIRM }
internal data class AuthUiState(
    val step: AuthStep,
    val email: String = "",
    val password: String = "",
    val repeatPassword: String = "",
    val code: String = "",
    val isBusy: Boolean = false,
    val failure: AuthFailure? = null,
    val fieldError: AuthInputIssue? = null,
    val resendSeconds: Int = 0,
    val isComplete: Boolean = false,
)
