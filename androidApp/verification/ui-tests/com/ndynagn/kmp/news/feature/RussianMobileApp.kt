package com.ndynagn.kmp.news.feature

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import com.ndynagn.kmp.news.feature.auth.domain.AuthRepository
import com.ndynagn.kmp.news.feature.feed.domain.NewsRepository
import com.ndynagn.kmp.news.feature.feed.domain.RefreshFeedIfNeeded
import com.ndynagn.kmp.news.feature.home.presentation.MobileApp

/** Activity creation can restore the device locale; pin it before composing regression fixtures. */
@Composable
internal fun RussianMobileApp(
    newsRepository: NewsRepository?,
    refreshFeedIfNeeded: RefreshFeedIfNeeded?,
    isConfigured: Boolean,
    formatDate: (Long) -> String,
    authRepository: AuthRepository,
    colorScheme: ColorScheme? = null,
) {
    setTestLocale("ru")
    MobileApp(newsRepository, refreshFeedIfNeeded, isConfigured, formatDate, authRepository, colorScheme = colorScheme)
}
