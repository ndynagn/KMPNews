package com.ndynagn.kmp.news.feature.feed.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.ndynagn.kmp.news.feature.feed.domain.Article
import kmpnews.sharedui.generated.resources.Res
import kmpnews.sharedui.generated.resources.feed_collapse
import kmpnews.sharedui.generated.resources.feed_empty
import kmpnews.sharedui.generated.resources.feed_end
import kmpnews.sharedui.generated.resources.feed_expand
import kmpnews.sharedui.generated.resources.feed_image_error
import kmpnews.sharedui.generated.resources.feed_limit
import kmpnews.sharedui.generated.resources.feed_loading
import kmpnews.sharedui.generated.resources.feed_missing_key
import kmpnews.sharedui.generated.resources.feed_no_summary
import kmpnews.sharedui.generated.resources.feed_no_title
import kmpnews.sharedui.generated.resources.feed_retry
import kmpnews.sharedui.generated.resources.feed_storage_error
import kmpnews.sharedui.generated.resources.feed_update_error
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedScreen(
    state: FeedUiState,
    listState: LazyListState,
    expanded: List<String>,
    onToggle: (String) -> Unit,
    onEvent: (FeedEvent) -> Unit,
    formatDate: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    val articles = state.snapshot?.articles.orEmpty()
    LaunchedEffect(listState, articles.size, state.canAppend) {
        if (state.canAppend) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                .distinctUntilChanged().collect { last ->
                    if (last >= 0 && last >= articles.size - 3) onEvent(FeedEvent.LOAD_MORE)
                }
        }
    }
    PullToRefreshBox(
        isRefreshing = state.status.operation == FeedOperation.REFRESH,
        onRefresh = { onEvent(FeedEvent.REFRESH) },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().testTag("feed.list"),
        ) {
            items(articles, key = { it.id }) { article ->
                FeedCard(article, article.id in expanded, { onToggle(article.id) }, formatDate)
            }
            // Keep the initial loading footer positional so the first snapshot starts at article zero.
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.status.operation != null || state is FeedUiState.Loading) {
                        val loading = stringResource(Res.string.feed_loading)
                        CircularProgressIndicator(Modifier.semantics { contentDescription = loading })
                    }
                    if (!state.status.isConfigured) Text(stringResource(Res.string.feed_missing_key))
                    if (state.status.storageFailed || state.status.problem != null) {
                        val errorMessage = if (state.status.storageFailed) {
                            Res.string.feed_storage_error
                        } else {
                            Res.string.feed_update_error
                        }
                        Text(stringResource(errorMessage))
                        Button(onClick = {
                            onEvent(FeedEvent.RETRY)
                        }, modifier = Modifier.testTag("feed.retryButton")) {
                            Text(stringResource(Res.string.feed_retry))
                        }
                    } else if (state.status.operation == null) {
                        val message = when {
                            articles.isEmpty() -> Res.string.feed_empty
                            state.snapshot?.isCacheLimitReached == true -> Res.string.feed_limit
                            state.snapshot?.hasMore == false -> Res.string.feed_end
                            else -> null
                        }
                        if (message != null) Text(stringResource(message))
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedCard(article: Article, expanded: Boolean, onToggle: () -> Unit, formatDate: (Long) -> String) {
    Card(Modifier.fillMaxWidth().testTag("feed.article.${article.id}")) {
        if (article.imageUrl != null) {
            SubcomposeAsyncImage(
                model = article.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(150.dp),
                loading = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) },
                error = {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(Res.string.feed_image_error))
                    }
                },
            )
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val actionLabel = stringResource(if (expanded) Res.string.feed_collapse else Res.string.feed_expand)
            Text(
                article.title ?: stringResource(Res.string.feed_no_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().clickable(
                    role = Role.Button,
                    onClickLabel = actionLabel,
                    onClick = onToggle,
                ),
            )
            if (expanded) Text(article.summary ?: stringResource(Res.string.feed_no_summary))
            val metadata = listOfNotNull(article.sourceName, article.publishedAtEpochMilliseconds?.let(formatDate))
            if (metadata.isNotEmpty()) Text(metadata.joinToString(" · "), style = MaterialTheme.typography.labelMedium)
        }
    }
}
