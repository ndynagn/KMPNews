package com.ndynagn.kmp.news.feature.article

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.platform.app.InstrumentationRegistry
import com.ndynagn.kmp.news.R
import com.ndynagn.kmp.news.feature.LocaleRule
import com.ndynagn.kmp.news.feature.feed.domain.FeedReadResult
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class ArticleScreenTest {
    @get:Rule(order = 0)
    val locale = LocaleRule()

    @get:Rule(order = 1)
    val fixture = object : ExternalResource() {
        override fun before() {
            ReaderFixture.reset()
        }
    }

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<ReaderVerificationActivity>()

    @Before
    fun feedIsReady() {
        compose.onNodeWithTag("feed.article.reader-0").assertIsDisplayed()
    }

    @Test
    fun duplicateOpenUsesOneSnapshotAndBackDoesNotRefreshStaleFeed() {
        val repository = ReaderFixture.repository

        compose.onNodeWithTag("feed.article.reader-0").performSemanticsAction(SemanticsActions.OnClick) { click ->
            click()
            click()
        }

        compose.onNodeWithTag("article.screen").assertIsDisplayed()
        compose.waitUntil(5_000) { repository.observers == 0 }
        capture("reader-light-ru")

        compose.runOnUiThread {
            ReaderFixture.now = 3_600_010L

            val snapshot = (repository.snapshots.value as FeedReadResult.Snapshot).feed

            repository.snapshots.value = FeedReadResult.Snapshot(
                snapshot.copy(articles = snapshot.articles.map { it.copy(title = "Changed after opening") }),
            )
        }

        compose.onNodeWithText("Changed after opening").assertDoesNotExist()

        compose.onNodeWithContentDescription("Назад").performClick()

        compose.onNodeWithTag("feed.list").assertIsDisplayed()
        compose.waitUntil(5_000) { repository.observers == 1 }
        assertEquals(0, repository.refreshes)
    }

    @Test
    fun listAndReadingPositionsSurviveTabsRecreationRotationAndSystemBack() {
        compose.onNodeWithTag("feed.list").performScrollToIndex(5)
        compose.onNodeWithTag("feed.article.reader-5").performClick()
        compose.onNodeWithTag("article.scroll").performTouchInput { swipeUp() }

        val before = readingPosition()

        assertTrue(before > 0f)

        compose.onNodeWithText("Профиль").performClick()
        compose.onNodeWithText("Новости").performClick()

        assertEquals(before, readingPosition(), 1f)

        compose.activityRule.scenario.recreate()

        compose.onNodeWithTag("article.screen").assertIsDisplayed()
        assertEquals(before, readingPosition(), 1f)

        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(5_000) {
            compose.activity.resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
        }

        compose.onNodeWithTag("article.screen").assertIsDisplayed()
        assertTrue(readingPosition() > 0f)
        capture("reader-landscape")

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithTag("feed.article.reader-5").assertIsDisplayed()
        compose.onNodeWithTag("feed.article.reader-0").assertIsNotDisplayed()
    }

    @Test
    fun missingDescriptionAndInvalidSourceDoNotBlockReading() {
        compose.onNodeWithTag("feed.article.reader-1").performClick()

        compose.onNodeWithText("Описание отсутствует").assertIsDisplayed()
        compose.onNodeWithContentDescription("Действия с новостью").assertDoesNotExist()
        capture("reader-missing-fields")

        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithTag("feed.list").performScrollToIndex(2)
        compose.onNodeWithTag("feed.article.reader-2").performClick()

        compose.onNodeWithContentDescription("Действия с новостью").assertDoesNotExist()
    }

    @Test
    fun unavailableSourceShowsLocalizedNoticeAndLeavesReaderOpen() {
        compose.onNodeWithTag("feed.article.reader-0").performClick()
        compose.onNodeWithContentDescription("Действия с новостью").performClick()
        compose.onNodeWithText("Открыть источник").performClick()

        compose.onNodeWithText("Не удалось открыть источник").assertIsDisplayed()
        compose.onNodeWithTag("article.screen").assertIsDisplayed()
        assertEquals(1, ReaderFixture.sourceOpens)
        capture("reader-source-error")
    }

    @Test
    fun readerRendersEnglishDarkDynamicAndLargeTextWithoutLosingSelection() {
        val image = File(compose.activity.cacheDir, "reader-image.png")

        compose.activity.resources.openRawResource(R.drawable.catalog_observatory).use { input ->
            image.outputStream().use { input.copyTo(it) }
        }

        val repository = ReaderFixture.repository
        val snapshot = (repository.snapshots.value as FeedReadResult.Snapshot).feed

        repository.snapshots.value = FeedReadResult.Snapshot(
            snapshot.copy(articles = snapshot.articles.map { it.copy(imageUrl = image.toURI().toString()) }),
        )

        compose.onNodeWithTag("feed.article.reader-0").performClick()

        for ((name, dark, dynamic, scale) in listOf(
            VisualCase("reader-light-en", dark = false, dynamic = false, scale = 1f),
            VisualCase("reader-dark-en", dark = true, dynamic = false, scale = 1f),
            VisualCase("reader-system-en", dark = false, dynamic = true, scale = 1f),
            VisualCase("reader-dark-large-en", dark = true, dynamic = false, scale = 2f),
        )) {
            compose.runOnUiThread {
                ReaderFixture.language = "en"
                ReaderFixture.dark = dark
                ReaderFixture.dynamic = dynamic
                ReaderFixture.fontScale = scale
            }

            compose.activityRule.scenario.recreate()

            compose.onNodeWithContentDescription("Back").assertIsDisplayed()
            compose.onNodeWithContentDescription("Article actions").assertIsDisplayed()
            capture(name)
        }
    }

    @Test
    fun sourceAdapterRejectsNonWebUrlsAndHandlesMissingOrBlockedHandlers() {
        for (url in listOf("", "https://", "https://bad host/news", "javascript:alert(1)", "file:///news", "news")) {
            assertFalse(url, isArticleSourceUrl(url))
            assertFalse(openArticleSource(url) { error("Invalid URLs must not launch") })
        }

        val url = "https://example.com/news?q=read#article"
        var launched: Intent? = null

        assertTrue(openArticleSource(url) { launched = it })

        assertEquals(Intent.ACTION_VIEW, launched?.action)
        assertEquals(url, launched?.dataString)
        assertTrue(launched?.hasCategory(Intent.CATEGORY_BROWSABLE) == true)
        assertFalse(openArticleSource(url) { throw ActivityNotFoundException() })
        assertFalse(openArticleSource(url) { throw SecurityException() })
        assertTrue(isArticleSourceUrl("HTTP://example.com/news"))
    }

    private fun readingPosition(): Float = compose.onNodeWithTag("article.scroll")
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun capture(name: String) {
        compose.waitForIdle()

        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.uiAutomation.waitForIdle(1_000, 5_000)

        val directory = File(instrumentation.targetContext.filesDir, "reader-evidence").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()

        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

private data class VisualCase(val name: String, val dark: Boolean, val dynamic: Boolean, val scale: Float)
