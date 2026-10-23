package com.ndynagn.kmp.news.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.ndynagn.kmp.news.feature.auth.domain.AuthFailure
import com.ndynagn.kmp.news.resources.Res
import com.ndynagn.kmp.news.resources.auth_invalid_code
import com.ndynagn.kmp.news.resources.auth_invalid_credentials
import com.ndynagn.kmp.news.resources.auth_network
import com.ndynagn.kmp.news.resources.auth_not_configured
import com.ndynagn.kmp.news.resources.auth_password_hint
import com.ndynagn.kmp.news.resources.auth_rate_limited
import com.ndynagn.kmp.news.resources.auth_service
import com.ndynagn.kmp.news.resources.auth_session_expired
import com.ndynagn.kmp.news.resources.auth_storage
import com.ndynagn.kmp.news.resources.auth_unconfirmed
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AuthFailureText(failure: AuthFailure) {
    val message = when (failure) {
        AuthFailure.INVALID_CREDENTIALS -> Res.string.auth_invalid_credentials
        AuthFailure.EMAIL_UNCONFIRMED -> Res.string.auth_unconfirmed
        AuthFailure.WEAK_PASSWORD -> Res.string.auth_password_hint
        AuthFailure.INVALID_CODE -> Res.string.auth_invalid_code
        AuthFailure.RATE_LIMITED -> Res.string.auth_rate_limited
        AuthFailure.NETWORK -> Res.string.auth_network
        AuthFailure.SERVICE -> Res.string.auth_service
        AuthFailure.STORAGE -> Res.string.auth_storage
        AuthFailure.NOT_CONFIGURED -> Res.string.auth_not_configured
        AuthFailure.SESSION_EXPIRED -> Res.string.auth_session_expired
    }

    Text(stringResource(message), color = MaterialTheme.colorScheme.error)
}
