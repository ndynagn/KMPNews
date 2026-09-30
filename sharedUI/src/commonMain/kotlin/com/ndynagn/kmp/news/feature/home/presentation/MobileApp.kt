package com.ndynagn.kmp.news.feature.home.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.ndynagn.kmp.news.feature.feed.domain.NewsRepository
import com.ndynagn.kmp.news.feature.feed.domain.RefreshFeedIfNeeded
import com.ndynagn.kmp.news.feature.feed.presentation.FeedScreen
import com.ndynagn.kmp.news.feature.feed.presentation.FeedViewModel
import kmpnews.sharedui.generated.resources.Res
import kmpnews.sharedui.generated.resources.home_favorites
import kmpnews.sharedui.generated.resources.home_news
import kmpnews.sharedui.generated.resources.home_placeholder
import kmpnews.sharedui.generated.resources.home_profile
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Mobile shell. The host supplies app-owned domain services; the lifecycle owner owns its feed ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileApp(
    newsRepository: NewsRepository,
    refreshFeedIfNeeded: RefreshFeedIfNeeded,
    isConfigured: Boolean,
    formatDate: (Long) -> String,
) {
    val feedViewModel = viewModel { FeedViewModel(newsRepository, refreshFeedIfNeeded, isConfigured) }
    val state by feedViewModel.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(selected, lifecycle, feedViewModel) {
        if (selected == 0) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { feedViewModel.activate() }
    }
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        val titles =
            listOf(
                stringResource(Res.string.home_news),
                stringResource(Res.string.home_favorites),
                stringResource(Res.string.home_profile),
            )
        Scaffold(
            topBar = { TopAppBar(title = { Text(titles[selected]) }) },
            bottomBar = {
                NavigationBar {
                    titles.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = {
                                Icon(
                                    painterResource(
                                        listOf(
                                            Res.drawable.home_news,
                                            Res.drawable.home_favorites,
                                            Res.drawable.home_profile,
                                        )[index],
                                    ),
                                    contentDescription = null,
                                )
                            },
                            label = { Text(title) },
                        )
                    }
                }
            },
        ) { padding ->
            NavDisplay(
                backStack = listOf(selected),
                onBack = { selected = 0 },
                modifier = Modifier.padding(padding),
                entryProvider = { tab ->
                    NavEntry(tab) {
                        if (tab == 0) {
                            FeedScreen(
                                state,
                                listState,
                                onEvent = feedViewModel::onEvent,
                                formatDate = formatDate,
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(Res.string.home_placeholder))
                            }
                        }
                    }
                },
            )
        }
    }
}
