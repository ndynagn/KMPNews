package com.ndynagn.kmp.news.feature.componentcatalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.ndynagn.kmp.news.R
import com.ndynagn.kmp.news.components.designsystem.AppActionButton
import com.ndynagn.kmp.news.components.designsystem.AppActionEmphasis
import com.ndynagn.kmp.news.components.designsystem.AppCodeField
import com.ndynagn.kmp.news.components.designsystem.AppFormMessage
import com.ndynagn.kmp.news.components.designsystem.AppIconButton
import com.ndynagn.kmp.news.components.designsystem.AppLoadingIndicator
import com.ndynagn.kmp.news.components.designsystem.AppPasswordField
import com.ndynagn.kmp.news.components.designsystem.AppSpacing
import com.ndynagn.kmp.news.components.designsystem.AppTextField
import com.ndynagn.kmp.news.resources.Res
import com.ndynagn.kmp.news.resources.kit_arrow_back
import com.ndynagn.kmp.news.resources.kit_close
import org.jetbrains.compose.resources.painterResource

internal data class CatalogAuthActions(
    val setEmail: (String) -> Unit,
    val setPassword: (String) -> Unit,
    val setRepeatedPassword: (String) -> Unit,
    val setCode: (String) -> Unit,
    val setOutcome: (CatalogOutcome) -> Unit,
    val openRecovery: () -> Unit,
    val submit: () -> Unit,
    val resend: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogAuthScreen(
    state: CatalogAuthState,
    actions: CatalogAuthActions,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val title = stringResource(state.step.titleResource())
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                AppIconButton(label = stringResource(R.string.catalog_back), onClick = onBack) {
                    Icon(painterResource(Res.drawable.kit_arrow_back), null)
                }
            },
            actions = {
                AppIconButton(label = stringResource(R.string.catalog_close), onClick = onClose) {
                    Icon(painterResource(Res.drawable.kit_close), null)
                }
            },
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(AppSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.group),
        ) {
            AppFormMessage(stringResource(R.string.catalog_demo))
            Text(stringResource(R.string.catalog_outcome))
            CatalogOutcome.entries.forEach { outcome ->
                FilterChip(
                    selected = state.outcome == outcome,
                    onClick = { actions.setOutcome(outcome) },
                    enabled = !state.isBusy,
                    modifier = Modifier.testTag("kit.auth.outcome.${outcome.name.lowercase()}"),
                    label = { Text(stringResource(outcome.titleResource())) },
                )
            }
            key(state.step) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                if (state.step == CatalogAuthStep.CONFIRM) {
                    AppCodeField(
                        state.code,
                        actions.setCode,
                        stringResource(R.string.catalog_code),
                        Modifier.fillMaxWidth().focusRequester(focus).testTag("kit.auth.code"),
                        isEnabled = !state.isBusy,
                        isError = state.message == CatalogMessage.INVALID_CODE,
                    )
                } else {
                    AppTextField(
                        state.email,
                        actions.setEmail,
                        stringResource(R.string.catalog_email),
                        Modifier.fillMaxWidth().focusRequester(focus).testTag("kit.auth.email"),
                        isEnabled = !state.isBusy,
                        keyboardType = KeyboardType.Email,
                    )
                    if (state.step != CatalogAuthStep.RECOVERY) {
                        AppPasswordField(
                            state.password,
                            actions.setPassword,
                            stringResource(R.string.catalog_password),
                            Modifier.fillMaxWidth().testTag("kit.auth.password"),
                            isEnabled = !state.isBusy,
                        )
                    }
                    if (state.step == CatalogAuthStep.REGISTER) {
                        AppPasswordField(
                            state.repeatedPassword,
                            actions.setRepeatedPassword,
                            stringResource(R.string.catalog_repeat_password),
                            Modifier.fillMaxWidth().testTag("kit.auth.repeatPassword"),
                            isEnabled = !state.isBusy,
                        )
                    }
                }
            }
            state.message?.let { message ->
                AppFormMessage(
                    stringResource(message.titleResource()),
                    isError =
                    message in
                        listOf(
                            CatalogMessage.INVALID_CODE,
                            CatalogMessage.REQUEST_ERROR,
                            CatalogMessage.TEMPORARY_ERROR,
                        ),
                )
            }
            if (state.step == CatalogAuthStep.CONFIRM && state.operation == CatalogAuthOperation.SUBMIT) {
                AppLoadingIndicator(
                    stringResource(R.string.catalog_loading),
                    Modifier.testTag("kit.auth.progress"),
                )
            }
            if (state.step != CatalogAuthStep.CONFIRM || state.canRetryCode) {
                AppActionButton(
                    if (state.canRetryCode) stringResource(R.string.catalog_retry) else title,
                    actions.submit,
                    Modifier.fillMaxWidth().testTag("kit.auth.submit"),
                    isBusy = state.isBusy,
                    isEnabled = state.canSubmit,
                )
            }
            if (state.step == CatalogAuthStep.CONFIRM) {
                AppActionButton(
                    if (state.resendSeconds == 0) {
                        stringResource(R.string.catalog_resend)
                    } else {
                        stringResource(R.string.catalog_resend_wait, state.resendSeconds)
                    },
                    actions.resend,
                    Modifier.fillMaxWidth().testTag("kit.auth.resend"),
                    emphasis = AppActionEmphasis.TEXT,
                    isBusy = state.operation == CatalogAuthOperation.RESEND,
                    isEnabled = !state.isBusy && state.resendSeconds == 0,
                )
            }
            if (state.step == CatalogAuthStep.LOGIN) {
                AppActionButton(
                    stringResource(R.string.catalog_recovery),
                    { actions.openRecovery() },
                    modifier = Modifier.testTag("kit.auth.recovery"),
                    emphasis = AppActionEmphasis.TEXT,
                    isEnabled = !state.isBusy,
                )
            }
        }
    }
}

internal fun CatalogAuthStep.titleResource(): Int = when (this) {
    CatalogAuthStep.LOGIN -> R.string.catalog_login
    CatalogAuthStep.REGISTER -> R.string.catalog_register
    CatalogAuthStep.CONFIRM -> R.string.catalog_confirm
    CatalogAuthStep.RECOVERY -> R.string.catalog_recovery
}

internal fun CatalogOutcome.titleResource(): Int = when (this) {
    CatalogOutcome.SUCCESS -> R.string.catalog_success
    CatalogOutcome.ERROR -> R.string.catalog_error
    CatalogOutcome.TEMPORARY_ERROR -> R.string.catalog_temporary_error
    CatalogOutcome.SLOW -> R.string.catalog_slow
}

private fun CatalogMessage.titleResource(): Int = when (this) {
    CatalogMessage.INVALID_CODE -> R.string.catalog_invalid_code
    CatalogMessage.REQUEST_ERROR -> R.string.catalog_request_error
    CatalogMessage.TEMPORARY_ERROR -> R.string.catalog_temporary_error
    CatalogMessage.CODE_SENT -> R.string.catalog_code_sent
    CatalogMessage.RECOVERY_SENT -> R.string.catalog_recovery_sent
}
