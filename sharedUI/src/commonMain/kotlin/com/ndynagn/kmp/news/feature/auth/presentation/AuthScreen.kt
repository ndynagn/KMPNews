package com.ndynagn.kmp.news.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ndynagn.kmp.news.components.AuthFailureText
import com.ndynagn.kmp.news.feature.auth.domain.AuthFailure
import com.ndynagn.kmp.news.feature.auth.domain.AuthInputIssue
import kmpnews.sharedui.generated.resources.Res
import kmpnews.sharedui.generated.resources.auth_back
import kmpnews.sharedui.generated.resources.auth_code
import kmpnews.sharedui.generated.resources.auth_code_hint
import kmpnews.sharedui.generated.resources.auth_confirm
import kmpnews.sharedui.generated.resources.auth_email
import kmpnews.sharedui.generated.resources.auth_hide
import kmpnews.sharedui.generated.resources.auth_invalid_code
import kmpnews.sharedui.generated.resources.auth_invalid_email
import kmpnews.sharedui.generated.resources.auth_login
import kmpnews.sharedui.generated.resources.auth_password
import kmpnews.sharedui.generated.resources.auth_password_hint
import kmpnews.sharedui.generated.resources.auth_password_match
import kmpnews.sharedui.generated.resources.auth_password_required
import kmpnews.sharedui.generated.resources.auth_register
import kmpnews.sharedui.generated.resources.auth_repeat_password
import kmpnews.sharedui.generated.resources.auth_resend
import kmpnews.sharedui.generated.resources.auth_resend_wait
import kmpnews.sharedui.generated.resources.auth_show
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
                OutlinedTextField(
                    value = state.code,
                    onValueChange = { onEvent(AuthEvent.CodeChanged(it)) },
                    label = { Text(stringResource(Res.string.auth_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth().testTag("auth.code"),
                )
            } else {
                OutlinedTextField(
                    value = state.email,
                    onValueChange = { onEvent(AuthEvent.EmailChanged(it)) },
                    label = { Text(stringResource(Res.string.auth_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !state.isBusy,
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
                Text(stringResource(message), color = MaterialTheme.colorScheme.error)
            }
            state.failure?.let { AuthFailureText(it) }
            if (state.failure == AuthFailure.EMAIL_UNCONFIRMED) {
                TextButton(onClick = {
                    onEvent(AuthEvent.ConfirmEmail)
                }) { Text(stringResource(Res.string.auth_confirm)) }
            }
            Button(onClick = {
                onEvent(AuthEvent.Submit)
            }, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth().testTag("auth.submit")) {
                Text(stringResource(title))
            }
            if (state.isBusy) CircularProgressIndicator()
            if (state.step == AuthStep.SIGN_IN) {
                TextButton(onClick = {
                    onEvent(AuthEvent.Register)
                }, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.auth_register))
                }
            }
            if (state.step == AuthStep.CONFIRM) {
                TextButton(onClick = {
                    onEvent(AuthEvent.Resend)
                }, enabled = !state.isBusy && state.resendSeconds == 0, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (state.resendSeconds == 0) {
                            stringResource(Res.string.auth_resend)
                        } else {
                            stringResource(Res.string.auth_resend_wait, state.resendSeconds)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, repeated: Boolean, enabled: Boolean) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, enabled = enabled, singleLine = true,
        label = { Text(stringResource(if (repeated) Res.string.auth_repeat_password else Res.string.auth_password)) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(onClick = {
                visible = !visible
            }) { Text(stringResource(if (visible) Res.string.auth_hide else Res.string.auth_show)) }
        },
        modifier = Modifier.fillMaxWidth().testTag(if (repeated) "auth.repeatPassword" else "auth.password"),
    )
}
