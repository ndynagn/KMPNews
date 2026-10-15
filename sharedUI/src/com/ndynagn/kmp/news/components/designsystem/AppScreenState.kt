package com.ndynagn.kmp.news.components.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign

/** Centers feedback within the available viewport, scrolling when large text exceeds it. */
@Composable
fun AppScreenState(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize().imePadding()) {
        val viewportHeight = maxHeight
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = viewportHeight)
                .padding(AppSpacing.screen),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

/** Empty/error feedback with caller-owned recovery actions; compact mode is for retained content. */
@Composable
fun AppStatusView(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    isCompact: Boolean = false,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (isCompact) AppSpacing.item else AppSpacing.group),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            textAlign = TextAlign.Center,
            style = if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
        )
        if (message != null) {
            Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

/** Expressive standalone loading state; compact button progress is intentionally separate. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppLoadingIndicator(label: String, modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.item),
    ) {
        LoadingIndicator(Modifier.semantics { contentDescription = label })
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
