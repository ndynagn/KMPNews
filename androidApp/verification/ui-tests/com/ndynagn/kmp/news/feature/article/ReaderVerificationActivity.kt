package com.ndynagn.kmp.news.feature.article

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.ndynagn.kmp.news.androidColorScheme
import com.ndynagn.kmp.news.feature.auth.FakeAuthRepository
import com.ndynagn.kmp.news.feature.feed.domain.Article
import com.ndynagn.kmp.news.feature.feed.domain.FeedClock
import com.ndynagn.kmp.news.feature.feed.domain.FeedReadResult
import com.ndynagn.kmp.news.feature.feed.domain.FeedSnapshot
import com.ndynagn.kmp.news.feature.feed.domain.FeedUpdateResult
import com.ndynagn.kmp.news.feature.feed.domain.NewsRepository
import com.ndynagn.kmp.news.feature.feed.domain.RefreshFeedIfNeeded
import com.ndynagn.kmp.news.feature.home.presentation.MobileApp
import com.ndynagn.kmp.news.feature.setTestLocale
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Test-only host exercises actual Activity restoration; never staged into ordinary applications. */
class ReaderVerificationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        actionBar?.hide()

        val repository = ReaderFixture.repository
        val auth = FakeAuthRepository()

        setContent {
            setTestLocale(ReaderFixture.language)
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !ReaderFixture.dark
                controller.isAppearanceLightNavigationBars = !ReaderFixture.dark
            }
            val configuration = Configuration(LocalConfiguration.current).apply {
                setLocale(Locale.forLanguageTag(ReaderFixture.language))
                uiMode = if (ReaderFixture.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val context = LocalContext.current.createConfigurationContext(configuration)

            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, ReaderFixture.fontScale),
            ) {
                MobileApp(
                    newsRepository = repository,
                    refreshFeedIfNeeded = RefreshFeedIfNeeded(repository, FeedClock { ReaderFixture.now }),
                    isConfigured = true,
                    formatDate = { "21.10.2026" },
                    authRepository = auth,
                    colorScheme = androidColorScheme(useDynamicColor = ReaderFixture.dynamic),
                    canOpenArticleSource = ::isArticleSourceUrl,
                    onOpenArticleSource = {
                        ReaderFixture.sourceOpens++
                        false
                    },
                )
            }
        }
    }
}

internal object ReaderFixture {
    var repository = StubReaderNewsRepository()
    var now = 10L
    var sourceOpens = 0
    var language by mutableStateOf("ru")
    var dark by mutableStateOf(false)
    var dynamic by mutableStateOf(false)
    var fontScale by mutableStateOf(1f)

    fun reset() {
        repository = StubReaderNewsRepository()
        now = 10L
        sourceOpens = 0
        language = "ru"
        dark = false
        dynamic = false
        fontScale = 1f
    }
}

internal class StubReaderNewsRepository : NewsRepository {
    val snapshots = MutableStateFlow<FeedReadResult>(
        FeedReadResult.Snapshot(
            FeedSnapshot(
                articles = (0..12).map { index ->
                    Article(
                        id = "reader-$index",
                        title = "News $index: a complete headline that stays readable at larger text sizes",
                        url = when (index) {
                            0 -> "https://example.com/news"
                            2 -> "javascript:alert(1)"
                            else -> null
                        },
                        summary = if (index == 1) null else "Description $index. " + "Reading text. ".repeat(800),
                        sourceName = if (index == 1) null else "Fixture News",
                        publishedAtEpochMilliseconds = if (index == 1) null else 42L,
                        imageUrl = if (index == 0) "file:///missing-reader-image.png" else null,
                    )
                },
                lastRefreshedAtEpochMilliseconds = 1L,
                hasMore = false,
                isCacheLimitReached = false,
            ),
        ),
    )

    @Volatile var refreshes = 0

    @Volatile var observers = 0

    override fun observeFeed() = flow {
        observers++
        try {
            emitAll(snapshots)
        } finally {
            observers--
        }
    }

    override suspend fun refresh(): FeedUpdateResult {
        refreshes++
        return FeedUpdateResult.Fresh
    }

    override suspend fun loadNextPage() = FeedUpdateResult.EndReached
}
