package com.ndynagn.kmp.news.feature.home.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.ndynagn.kmp.news.feature.auth.domain.AuthRepository
import com.ndynagn.kmp.news.feature.auth.presentation.AuthFlowOwner
import com.ndynagn.kmp.news.feature.auth.presentation.AuthScreen
import com.ndynagn.kmp.news.feature.auth.presentation.AuthStep
import com.ndynagn.kmp.news.feature.auth.presentation.AuthViewModel
import com.ndynagn.kmp.news.feature.feed.domain.NewsRepository
import com.ndynagn.kmp.news.feature.feed.domain.RefreshFeedIfNeeded
import com.ndynagn.kmp.news.feature.feed.presentation.FeedScreen
import com.ndynagn.kmp.news.feature.feed.presentation.FeedViewModel
import com.ndynagn.kmp.news.feature.profile.presentation.ProfileScreen
import com.ndynagn.kmp.news.feature.profile.presentation.ProfileViewModel
import kmpnews.sharedui.generated.resources.Res
import kmpnews.sharedui.generated.resources.feed_retry
import kmpnews.sharedui.generated.resources.feed_storage_error
import kmpnews.sharedui.generated.resources.home_favorites
import kmpnews.sharedui.generated.resources.home_news
import kmpnews.sharedui.generated.resources.home_placeholder
import kmpnews.sharedui.generated.resources.home_profile
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Mobile shell with host-owned Auth/feed graphs and navigation-owned form state.
 * Absent feed services show a retryable storage failure without disabling Profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileApp(
    newsRepository: NewsRepository?,
    refreshFeedIfNeeded: RefreshFeedIfNeeded?,
    isConfigured: Boolean,
    formatDate: (Long) -> String,
    authRepository: AuthRepository,
    onRetryFeed: () -> Unit = {},
) {
    val feedViewModel = if (newsRepository != null && refreshFeedIfNeeded != null) {
        viewModel { FeedViewModel(newsRepository, refreshFeedIfNeeded, isConfigured) }
    } else {
        null
    }
    val profileViewModel = viewModel { ProfileViewModel(authRepository) }
    val session by profileViewModel.session.collectAsStateWithLifecycle()
    val notice by profileViewModel.notice.collectAsStateWithLifecycle()
    val busy by profileViewModel.isBusy.collectAsStateWithLifecycle()
    val authFlowOwner = viewModel { AuthFlowOwner() }
    var authStep by rememberSaveable { mutableStateOf<AuthStep?>(null) }
    var selected by rememberSaveable { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(selected, lifecycle, feedViewModel) {
        if (selected == 0 && feedViewModel != null) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { feedViewModel.activate() }
        }
    }

    LaunchedEffect(lifecycle, profileViewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { profileViewModel.restore() }
    }

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        val form = authStep
        if (form != null) {
            val closeAuth = {
                authFlowOwner.closeFlow()
                authStep = null
            }
            NavDisplay(
                backStack = listOf("profile", "auth"),
                onBack = closeAuth,
                entryProvider = { key ->
                    NavEntry(key) {
                        if (key == "auth") {
                            AuthRoute(authRepository, form, authFlowOwner, onClose = closeAuth)
                        } else {
                            ProfileScreen(
                                session = session,
                                notice = notice,
                                isBusy = busy,
                                onLogin = {},
                                onRegister = {},
                                onRetry = profileViewModel::restore,
                                onLogout = profileViewModel::signOut,
                            )
                        }
                    }
                },
            )
            return@MaterialTheme
        }
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
                        if (tab == 0 && feedViewModel != null) {
                            val state by feedViewModel.state.collectAsStateWithLifecycle()
                            FeedScreen(
                                state,
                                listState,
                                onEvent = feedViewModel::onEvent,
                                formatDate = formatDate,
                            )
                        } else if (tab == 2) {
                            ProfileScreen(
                                session,
                                notice,
                                busy,
                                { authStep = AuthStep.SIGN_IN },
                                { authStep = AuthStep.REGISTER },
                                profileViewModel::restore,
                                profileViewModel::signOut,
                            )
                        } else if (tab == 0) {
                            Column {
                                Text(stringResource(Res.string.feed_storage_error))
                                TextButton(onClick = onRetryFeed) {
                                    Text(stringResource(Res.string.feed_retry))
                                }
                            }
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

@Composable
private fun AuthRoute(
    authRepository: AuthRepository,
    initialStep: AuthStep,
    owner: ViewModelStoreOwner,
    onClose: () -> Unit,
) {
    val authViewModel = viewModel(viewModelStoreOwner = owner) { AuthViewModel(authRepository, initialStep) }
    val state by authViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isComplete) { if (state.isComplete) onClose() }
    AuthScreen(state, authViewModel::onEvent, onClose)
}
