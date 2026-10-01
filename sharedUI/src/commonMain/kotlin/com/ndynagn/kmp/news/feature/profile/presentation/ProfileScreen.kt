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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ndynagn.kmp.news.components.AuthFailureText
import com.ndynagn.kmp.news.feature.auth.domain.AuthFailure
import com.ndynagn.kmp.news.feature.auth.domain.AuthSession
import kmpnews.sharedui.generated.resources.Res
import kmpnews.sharedui.generated.resources.auth_login
import kmpnews.sharedui.generated.resources.auth_logout
import kmpnews.sharedui.generated.resources.auth_logout_warning
import kmpnews.sharedui.generated.resources.auth_register
import kmpnews.sharedui.generated.resources.auth_retry
import kmpnews.sharedui.generated.resources.auth_storage
import kmpnews.sharedui.generated.resources.profile_guest_hint
import kmpnews.sharedui.generated.resources.profile_welcome
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
                        AuthSession.Restoring -> CircularProgressIndicator()

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
                    Button(
                        onClick = onLogin,
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("profile.login"),
                    ) {
                        Text(stringResource(Res.string.auth_login))
                    }
                    TextButton(
                        onClick = onRegister,
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("profile.register"),
                    ) {
                        Text(stringResource(Res.string.auth_register))
                    }
                }

                is AuthSession.Authenticated -> Button(
                    onClick = onLogout,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.auth_logout))
                }

                is AuthSession.Unavailable -> {
                    Button(onClick = onRetry, enabled = !isBusy) { Text(stringResource(Res.string.auth_retry)) }
                    TextButton(onClick = onLogout, enabled = !isBusy) { Text(stringResource(Res.string.auth_logout)) }
                }

                AuthSession.Restoring -> Unit
            }
        }
    }
}
