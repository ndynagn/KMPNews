package com.ndynagn.kmp.news.feature.feed

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.platform.app.InstrumentationRegistry
import com.ndynagn.kmp.news.MainActivity
import com.ndynagn.kmp.news.NewsApplication
import com.ndynagn.kmp.news.feature.feed.domain.FeedReadResult
import com.ndynagn.kmp.news.feature.feed.domain.FeedUpdateResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import java.io.File

/** Opt-in integration smoke: explicitly consumes real server budget; never part of default fixture checks. */
class LiveFeedSmokeTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule val rules: TestRule = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            assumeTrue(InstrumentationRegistry.getArguments().getString("liveFeedSmoke") == "true")
        }
    }).around(compose)

    @Test
    fun refreshAndAppendThroughMediator() {
        val repository = (compose.activity.application as NewsApplication).feedDependencies.newsRepository
        runBlocking {
            var result: FeedUpdateResult = FeedUpdateResult.AlreadyRunning
            repeat(60) {
                if (result == FeedUpdateResult.AlreadyRunning) {
                    delay(500)
                    result = repository.refresh()
                }
            }
            assertEquals(FeedUpdateResult.Updated, result)
            assertEquals(FeedUpdateResult.Updated, repository.loadNextPage())
        }
        val snapshot = runBlocking { (repository.observeFeed().first() as FeedReadResult.Snapshot).feed }
        compose.waitForIdle()
        compose.onNodeWithTag("feed.list").performScrollToIndex(0)
        compose.onNodeWithTag("feed.article.${snapshot.articles.first().id}").assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.getExternalFilesDir(null), "android-live-mediator.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
