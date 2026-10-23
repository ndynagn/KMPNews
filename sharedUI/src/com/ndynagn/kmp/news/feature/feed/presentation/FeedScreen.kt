package com.ndynagn.kmp.news.feature.feed.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.ndynagn.kmp.news.components.designsystem.AppActionButton
import com.ndynagn.kmp.news.components.designsystem.AppActionEmphasis
import com.ndynagn.kmp.news.components.designsystem.AppImagePlaceholder
import com.ndynagn.kmp.news.components.designsystem.AppLoadingIndicator
import com.ndynagn.kmp.news.components.designsystem.AppNewsPreview
import com.ndynagn.kmp.news.components.designsystem.AppScreenState
import com.ndynagn.kmp.news.components.designsystem.AppStatusView
import com.ndynagn.kmp.news.feature.feed.domain.Article
import com.ndynagn.kmp.news.resources.Res
import com.ndynagn.kmp.news.resources.feed_empty
import com.ndynagn.kmp.news.resources.feed_end
import com.ndynagn.kmp.news.resources.feed_image_error
import com.ndynagn.kmp.news.resources.feed_limit
import com.ndynagn.kmp.news.resources.feed_loading
import com.ndynagn.kmp.news.resources.feed_missing_key
import com.ndynagn.kmp.news.resources.feed_no_title
import com.ndynagn.kmp.news.resources.feed_retry
import com.ndynagn.kmp.news.resources.feed_storage_error
import com.ndynagn.kmp.news.resources.feed_update_error
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedScreen(
    state: FeedUiState,
    listState: LazyGridState,
    onEvent: (FeedEvent) -> Unit,
    formatDate: (Long) -> String,
    modifier: Modifier = Modifier,
    onOpenArticle: (Article) -> Unit = {},
) {
    val articles = state.snapshot?.articles.orEmpty()
    val currentState = rememberUpdatedState(state)
    val currentOnEvent = rememberUpdatedState(onEvent)
    val paginationConnection = remember(listState) {
        FeedPaginationConnection {
            val feed = currentState.value
            val articleCount = feed.snapshot?.articles?.size ?: 0
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1

            if (feed.canAppend && lastVisible >= 0 && lastVisible >= articleCount - 3) {
                currentOnEvent.value(FeedEvent.LOAD_MORE)
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = state.status.operation == FeedOperation.REFRESH,
        onRefresh = { if (state.status.isConfigured && state.status.operation == null) onEvent(FeedEvent.REFRESH) },
        modifier = modifier.fillMaxSize(),
    ) {
        if (articles.isEmpty()) {
            AppScreenState {
                if (state.status.isConfigured &&
                    (state.status.operation != null || state is FeedUiState.Loading || state is FeedUiState.Initial)
                ) {
                    AppLoadingIndicator(stringResource(Res.string.feed_loading))
                } else {
                    AppStatusView(
                        title = stringResource(
                            if (state.status.storageFailed) {
                                Res.string.feed_storage_error
                            } else if (!state.status.isConfigured || state.status.problem != null) {
                                Res.string.feed_update_error
                            } else {
                                Res.string.feed_empty
                            },
                        ),
                        message = if (!state.status.isConfigured) stringResource(Res.string.feed_missing_key) else null,
                    ) {
                        if (state.status.storageFailed || state.status.problem != null) {
                            AppActionButton(
                                stringResource(Res.string.feed_retry),
                                { onEvent(FeedEvent.RETRY) },
                                Modifier.testTag("feed.retryButton"),
                                emphasis = AppActionEmphasis.TEXT,
                            )
                        }
                    }
                }
            }
            return@PullToRefreshBox
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(320.dp),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().nestedScroll(paginationConnection).testTag("feed.list"),
        ) {
            items(articles, key = { it.id }) { article ->
                FeedCard(article, formatDate, onOpenArticle)
            }
            // Keep the initial loading footer positional so the first snapshot starts at article zero.
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.status.operation != null || state is FeedUiState.Loading) {
                        AppLoadingIndicator(stringResource(Res.string.feed_loading))
                    }
                    if (!state.status.isConfigured) Text(stringResource(Res.string.feed_missing_key))
                    if (state.status.storageFailed || state.status.problem != null) {
                        val errorMessage = if (state.status.storageFailed) {
                            Res.string.feed_storage_error
                        } else {
                            Res.string.feed_update_error
                        }
                        AppStatusView(title = stringResource(errorMessage), isCompact = true) {
                            AppActionButton(
                                stringResource(Res.string.feed_retry),
                                { onEvent(FeedEvent.RETRY) },
                                Modifier.testTag("feed.retryButton"),
                                emphasis = AppActionEmphasis.TEXT,
                                isBusy = state.status.operation != null,
                            )
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
private fun FeedCard(article: Article, formatDate: (Long) -> String, onOpenArticle: (Article) -> Unit) {
    val metadata = listOfNotNull(article.sourceName, article.publishedAtEpochMilliseconds?.let(formatDate))

    AppNewsPreview(
        title = article.title ?: stringResource(Res.string.feed_no_title),
        metadata = metadata.takeIf { it.isNotEmpty() }?.joinToString(" · "),
        image = article.imageUrl?.let { { FeedImage(article) } },
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenArticle(article) }
            .testTag("feed.article.${article.id}"),
    )
}

@Composable
private fun FeedImage(article: Article) {
    SubcomposeAsyncImage(
        model = article.imageUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        loading = { AppImagePlaceholder(stringResource(Res.string.feed_loading)) },
        error = { AppImagePlaceholder(stringResource(Res.string.feed_image_error)) },
    )
}
