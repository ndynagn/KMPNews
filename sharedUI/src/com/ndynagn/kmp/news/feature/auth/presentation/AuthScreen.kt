package com.ndynagn.kmp.news.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ndynagn.kmp.news.components.AuthFailureText
import com.ndynagn.kmp.news.components.designsystem.AppActionButton
import com.ndynagn.kmp.news.components.designsystem.AppActionEmphasis
import com.ndynagn.kmp.news.components.designsystem.AppCodeField
import com.ndynagn.kmp.news.components.designsystem.AppFormMessage
import com.ndynagn.kmp.news.components.designsystem.AppPasswordField
import com.ndynagn.kmp.news.components.designsystem.AppTextField
import com.ndynagn.kmp.news.feature.auth.domain.AuthFailure
import com.ndynagn.kmp.news.feature.auth.domain.AuthInputIssue
import com.ndynagn.kmp.news.resources.Res
import com.ndynagn.kmp.news.resources.auth_back
import com.ndynagn.kmp.news.resources.auth_code
import com.ndynagn.kmp.news.resources.auth_code_hint
import com.ndynagn.kmp.news.resources.auth_confirm
import com.ndynagn.kmp.news.resources.auth_email
import com.ndynagn.kmp.news.resources.auth_invalid_code
import com.ndynagn.kmp.news.resources.auth_invalid_email
import com.ndynagn.kmp.news.resources.auth_login
import com.ndynagn.kmp.news.resources.auth_password
import com.ndynagn.kmp.news.resources.auth_password_hint
import com.ndynagn.kmp.news.resources.auth_password_match
import com.ndynagn.kmp.news.resources.auth_password_required
import com.ndynagn.kmp.news.resources.auth_register
import com.ndynagn.kmp.news.resources.auth_repeat_password
import com.ndynagn.kmp.news.resources.auth_resend
import com.ndynagn.kmp.news.resources.auth_resend_wait
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AuthScreen(
    state: AuthUiState,
    onEvent: (AuthEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (state.step) {
        AuthStep.SIGN_IN -> Res.string.auth_login
        AuthStep.REGISTER -> Res.string.auth_register
        AuthStep.CONFIRM -> Res.string.auth_confirm
    }
    Scaffold(modifier = modifier, topBar = {
        TopAppBar(title = { Text(stringResource(title)) }, navigationIcon = {
            TextButton(onClick = onBack) { Text(stringResource(Res.string.auth_back)) }
        })
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.step == AuthStep.CONFIRM) {
                Text(stringResource(Res.string.auth_code_hint, state.email))
                AppCodeField(
                    value = state.code,
                    onValueChange = { onEvent(AuthEvent.CodeChanged(it)) },
                    label = stringResource(Res.string.auth_code),
                    isEnabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth().testTag("auth.code"),
                )
            } else {
                AppTextField(
                    value = state.email,
                    onValueChange = { onEvent(AuthEvent.EmailChanged(it)) },
                    label = stringResource(Res.string.auth_email),
                    keyboardType = KeyboardType.Email,
                    isEnabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth().testTag("auth.email"),
                )
                PasswordField(state.password, { onEvent(AuthEvent.PasswordChanged(it)) }, false, !state.isBusy)
                if (state.step == AuthStep.REGISTER) {
                    PasswordField(state.repeatPassword, {
                        onEvent(AuthEvent.RepeatPasswordChanged(it))
                    }, true, !state.isBusy)
                    Text(stringResource(Res.string.auth_password_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
            state.fieldError?.let {
                val message = when (it) {
                    AuthInputIssue.EMAIL -> Res.string.auth_invalid_email
                    AuthInputIssue.PASSWORD_TOO_SHORT -> Res.string.auth_password_hint
                    AuthInputIssue.PASSWORD_REQUIRED -> Res.string.auth_password_required
                    AuthInputIssue.PASSWORD_MISMATCH -> Res.string.auth_password_match
                    AuthInputIssue.CODE -> Res.string.auth_invalid_code
                }
                AppFormMessage(stringResource(message), isError = true)
            }
            state.failure?.let { AuthFailureText(it) }
            if (state.failure == AuthFailure.EMAIL_UNCONFIRMED) {
                AppActionButton(
                    title = stringResource(Res.string.auth_confirm),
                    onClick = { onEvent(AuthEvent.ConfirmEmail) },
                    emphasis = AppActionEmphasis.TEXT,
                )
            }
            AppActionButton(
                title = stringResource(title),
                onClick = { onEvent(AuthEvent.Submit) },
                isBusy = state.isBusy,
                modifier = Modifier.fillMaxWidth().testTag("auth.submit"),
            )
            if (state.step == AuthStep.SIGN_IN) {
                AppActionButton(
                    title = stringResource(Res.string.auth_register),
                    onClick = { onEvent(AuthEvent.Register) },
                    isEnabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    emphasis = AppActionEmphasis.SECONDARY,
                )
            }
            if (state.step == AuthStep.CONFIRM) {
                AppActionButton(
                    title = if (state.resendSeconds == 0) {
                        stringResource(Res.string.auth_resend)
                    } else {
                        stringResource(Res.string.auth_resend_wait, state.resendSeconds)
                    },
                    onClick = { onEvent(AuthEvent.Resend) },
                    isEnabled = !state.isBusy && state.resendSeconds == 0,
                    modifier = Modifier.fillMaxWidth(),
                    emphasis = AppActionEmphasis.TEXT,
                )
            }
        }
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, repeated: Boolean, enabled: Boolean) {
    AppPasswordField(
        value = value,
        onValueChange = onChange,
        isEnabled = enabled,
        label = stringResource(if (repeated) Res.string.auth_repeat_password else Res.string.auth_password),
        modifier = Modifier.fillMaxWidth().testTag(if (repeated) "auth.repeatPassword" else "auth.password"),
    )
}
