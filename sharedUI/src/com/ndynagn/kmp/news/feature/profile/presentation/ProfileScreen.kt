package com.ndynagn.kmp.news.feature.profile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ndynagn.kmp.news.components.AuthFailureText
import com.ndynagn.kmp.news.components.designsystem.AppActionButton
import com.ndynagn.kmp.news.components.designsystem.AppActionEmphasis
import com.ndynagn.kmp.news.components.designsystem.AppLoadingIndicator
import com.ndynagn.kmp.news.feature.auth.domain.AuthFailure
import com.ndynagn.kmp.news.feature.auth.domain.AuthSession
import com.ndynagn.kmp.news.resources.Res
import com.ndynagn.kmp.news.resources.auth_login
import com.ndynagn.kmp.news.resources.auth_logout
import com.ndynagn.kmp.news.resources.auth_logout_warning
import com.ndynagn.kmp.news.resources.auth_register
import com.ndynagn.kmp.news.resources.auth_retry
import com.ndynagn.kmp.news.resources.auth_storage
import com.ndynagn.kmp.news.resources.kit_loading
import com.ndynagn.kmp.news.resources.profile_guest_hint
import com.ndynagn.kmp.news.resources.profile_welcome
import org.jetbrains.compose.resources.stringResource

/** State-only profile; the scrollable minimum-height layout keeps actions reachable with large text. */
@Composable
internal fun ProfileScreen(
    session: AuthSession,
    notice: AuthFailure?,
    isBusy: Boolean,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val availableHeight = maxHeight
        Column(
            Modifier.fillMaxWidth().verticalScroll(
                rememberScrollState(),
            ).heightIn(min = availableHeight).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (session) {
                        AuthSession.Restoring -> AppLoadingIndicator(stringResource(Res.string.kit_loading))

                        AuthSession.Guest -> {
                            Text(
                                stringResource(Res.string.profile_welcome),
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center,
                            )
                            Text(stringResource(Res.string.profile_guest_hint), textAlign = TextAlign.Center)
                        }

                        is AuthSession.Authenticated -> Text(
                            session.user.email,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )

                        is AuthSession.Unavailable -> AuthFailureText(session.failure)
                    }
                }
            }
            if (notice != null) {
                Text(
                    stringResource(
                        if (notice == AuthFailure.STORAGE) {
                            Res.string.auth_storage
                        } else {
                            Res.string.auth_logout_warning
                        },
                    ),
                )
            }
            when (session) {
                AuthSession.Guest -> {
                    AppActionButton(
                        title = stringResource(Res.string.auth_login),
                        onClick = onLogin,
                        isBusy = isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("profile.login"),
                    )
                    AppActionButton(
                        title = stringResource(Res.string.auth_register),
                        emphasis = AppActionEmphasis.SECONDARY,
                        onClick = onRegister,
                        isEnabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("profile.register"),
                    )
                }

                is AuthSession.Authenticated -> AppActionButton(
                    title = stringResource(Res.string.auth_logout),
                    emphasis = AppActionEmphasis.DESTRUCTIVE,
                    onClick = onLogout,
                    isBusy = isBusy,
                    modifier = Modifier.fillMaxWidth(),
                )

                is AuthSession.Unavailable -> {
                    AppActionButton(stringResource(Res.string.auth_retry), onRetry, isBusy = isBusy)
                    AppActionButton(
                        stringResource(Res.string.auth_logout),
                        onLogout,
                        isEnabled = !isBusy,
                        emphasis = AppActionEmphasis.DESTRUCTIVE,
                    )
                }

                AuthSession.Restoring -> Unit
            }
        }
    }
}
