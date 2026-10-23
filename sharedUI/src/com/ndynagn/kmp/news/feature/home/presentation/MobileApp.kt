package com.ndynagn.kmp.news.feature.home.presentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.ndynagn.kmp.news.components.designsystem.AppActionButton
import com.ndynagn.kmp.news.components.designsystem.AppScreenState
import com.ndynagn.kmp.news.components.designsystem.AppStatusView
import com.ndynagn.kmp.news.components.designsystem.AppTheme
import com.ndynagn.kmp.news.components.designsystem.appColorScheme
import com.ndynagn.kmp.news.feature.article.presentation.ArticleScreen
import com.ndynagn.kmp.news.feature.article.presentation.articleSnapshotSaver
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
import com.ndynagn.kmp.news.resources.Res
import com.ndynagn.kmp.news.resources.feed_retry
import com.ndynagn.kmp.news.resources.feed_storage_error
import com.ndynagn.kmp.news.resources.home_favorites
import com.ndynagn.kmp.news.resources.home_news
import com.ndynagn.kmp.news.resources.home_placeholder
import com.ndynagn.kmp.news.resources.home_profile
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Mobile shell with host-owned Auth/feed graphs and navigation-owned form state.
 * Absent feed services show a retryable storage failure without disabling Profile.
 * Hosts validate original source URLs and open them synchronously on user action;
 * [onOpenArticleSource] returns false when no handler is available or access fails.
 */
@Composable
fun MobileApp(
    newsRepository: NewsRepository?,
    refreshFeedIfNeeded: RefreshFeedIfNeeded?,
    isConfigured: Boolean,
    formatDate: (Long) -> String,
    authRepository: AuthRepository,
    onRetryFeed: () -> Unit = {},
    colorScheme: ColorScheme? = null,
    profileActions: @Composable () -> Unit = {},
    canOpenArticleSource: (String) -> Boolean = { false },
    onOpenArticleSource: (String) -> Boolean = { false },
) {
    val feedViewModel = if (newsRepository != null && refreshFeedIfNeeded != null) {
        viewModel { FeedViewModel(newsRepository, refreshFeedIfNeeded, isConfigured) }
    } else {
        null
    }
    val profileViewModel = viewModel { ProfileViewModel(authRepository) }
    val authFlowOwner = viewModel { AuthFlowOwner() }
    var authStep by rememberSaveable { mutableStateOf<AuthStep?>(null) }
    var selected by rememberSaveable { mutableStateOf(0) }
    var selectedArticle by rememberSaveable(stateSaver = articleSnapshotSaver) { mutableStateOf(null) }
    var resumesFromReader by rememberSaveable { mutableStateOf(false) }
    val readingScroll = rememberSaveable(selectedArticle?.id, saver = ScrollState.Saver) { ScrollState(0) }
    val listState = rememberLazyGridState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(selected, selectedArticle, authStep, lifecycle, feedViewModel) {
        if (selected == 0 && selectedArticle == null && authStep == null && feedViewModel != null) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val refresh = !resumesFromReader

                resumesFromReader = false
                feedViewModel.activate(refreshIfNeeded = refresh)
            }
        }
    }

    LaunchedEffect(lifecycle, profileViewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { profileViewModel.restore() }
    }

    val resolvedColorScheme = colorScheme ?: appColorScheme(isDark = isSystemInDarkTheme())

    AppTheme(colorScheme = resolvedColorScheme) {
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
                            val session by profileViewModel.session.collectAsStateWithLifecycle()
                            val notice by profileViewModel.notice.collectAsStateWithLifecycle()
                            val busy by profileViewModel.isBusy.collectAsStateWithLifecycle()

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
            return@AppTheme
        }
        val titles =
            listOf(
                stringResource(Res.string.home_news),
                stringResource(Res.string.home_favorites),
                stringResource(Res.string.home_profile),
            )
        Scaffold(
            topBar = {
                if (selected != 0 || selectedArticle == null) {
                    TopAppBar(title = { Text(titles[selected]) }, actions = { if (selected == 2) profileActions() })
                }
            },
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
                backStack = when {
                    selected != 0 -> listOf(0, selected)
                    selectedArticle != null -> listOf(0, "article")
                    else -> listOf(0)
                },
                onBack = { if (selected != 0) selected = 0 else selectedArticle = null },
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
                entryProvider = { tab ->
                    NavEntry(tab) {
                        if (tab == "article") {
                            val article = selectedArticle
                            if (article != null) {
                                ArticleScreen(
                                    article = article,
                                    scrollState = readingScroll,
                                    formatDate = formatDate,
                                    onBack = { selectedArticle = null },
                                    canOpenSource = article.url?.let(canOpenArticleSource) == true,
                                    onOpenSource = { article.url?.let(onOpenArticleSource) == true },
                                )
                            }
                        } else if (tab == 0 && feedViewModel != null) {
                            val state by feedViewModel.state.collectAsStateWithLifecycle()

                            FeedScreen(
                                state,
                                listState,
                                onEvent = feedViewModel::onEvent,
                                formatDate = formatDate,
                                onOpenArticle = { article ->
                                    if (selectedArticle == null) {
                                        resumesFromReader = true
                                        selectedArticle = article
                                    }
                                },
                            )
                        } else if (tab == 2) {
                            val session by profileViewModel.session.collectAsStateWithLifecycle()
                            val notice by profileViewModel.notice.collectAsStateWithLifecycle()
                            val busy by profileViewModel.isBusy.collectAsStateWithLifecycle()

                            ProfileScreen(
                                session = session,
                                notice = notice,
                                isBusy = busy,
                                onLogin = { authStep = AuthStep.SIGN_IN },
                                onRegister = { authStep = AuthStep.REGISTER },
                                onRetry = profileViewModel::restore,
                                onLogout = profileViewModel::signOut,
                            )
                        } else if (tab == 0) {
                            AppScreenState {
                                AppStatusView(title = stringResource(Res.string.feed_storage_error)) {
                                    AppActionButton(stringResource(Res.string.feed_retry), onRetryFeed)
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
