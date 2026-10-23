package com.ndynagn.kmp.news.feature.article.presentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.ndynagn.kmp.news.components.designsystem.AppIconButton
import com.ndynagn.kmp.news.components.designsystem.AppImagePlaceholder
import com.ndynagn.kmp.news.feature.feed.domain.Article
import com.ndynagn.kmp.news.resources.Res
import com.ndynagn.kmp.news.resources.article_back
import com.ndynagn.kmp.news.resources.article_menu
import com.ndynagn.kmp.news.resources.article_open_source
import com.ndynagn.kmp.news.resources.article_source_error
import com.ndynagn.kmp.news.resources.article_title
import com.ndynagn.kmp.news.resources.feed_image_error
import com.ndynagn.kmp.news.resources.feed_loading
import com.ndynagn.kmp.news.resources.feed_no_summary
import com.ndynagn.kmp.news.resources.feed_no_title
import com.ndynagn.kmp.news.resources.kit_arrow_back
import com.ndynagn.kmp.news.resources.kit_more_vert
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ArticleScreen(
    article: Article,
    scrollState: ScrollState,
    formatDate: (Long) -> String,
    onBack: () -> Unit,
    canOpenSource: Boolean,
    onOpenSource: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by rememberSaveable(article.id) { mutableStateOf(false) }
    val snackbar = remember(article.id) { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val sourceError = stringResource(Res.string.article_source_error)
    val metadata = listOfNotNull(article.sourceName, article.publishedAtEpochMilliseconds?.let(formatDate))

    Scaffold(
        modifier = modifier.testTag("article.screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.article_title)) },
                navigationIcon = {
                    AppIconButton(stringResource(Res.string.article_back), onBack) {
                        Icon(painterResource(Res.drawable.kit_arrow_back), null)
                    }
                },
                actions = {
                    if (canOpenSource) {
                        AppIconButton(stringResource(Res.string.article_menu), { menuExpanded = true }) {
                            Icon(painterResource(Res.drawable.kit_more_vert), null)
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.article_open_source)) },
                                onClick = {
                                    menuExpanded = false
                                    if (!onOpenSource()) scope.launch { snackbar.showSnackbar(sourceError) }
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(scrollState).testTag("article.scroll"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (article.imageUrl != null) {
                SubcomposeAsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2.1f),
                    loading = { AppImagePlaceholder(stringResource(Res.string.feed_loading)) },
                    error = { AppImagePlaceholder(stringResource(Res.string.feed_image_error)) },
                )
            }
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    article.title ?: stringResource(Res.string.feed_no_title),
                    style = MaterialTheme.typography.headlineLarge,
                )
                if (metadata.isNotEmpty()) {
                    Text(
                        metadata.joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    article.summary?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.feed_no_summary),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
