package com.ndynagn.kmp.news.feature.auth.presentation

internal sealed interface AuthEvent {
    data class EmailChanged(val value: String) : AuthEvent
    data class PasswordChanged(val value: String) : AuthEvent
    data class RepeatPasswordChanged(val value: String) : AuthEvent
    data class CodeChanged(val value: String) : AuthEvent
    data object Submit : AuthEvent
    data object Resend : AuthEvent
    data object Register : AuthEvent
    data object ConfirmEmail : AuthEvent
}
