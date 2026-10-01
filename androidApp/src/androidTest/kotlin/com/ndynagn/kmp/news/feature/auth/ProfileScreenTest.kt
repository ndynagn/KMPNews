package com.ndynagn.kmp.news.feature.auth

import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.ndynagn.kmp.news.feature.home.presentation.MobileApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class ProfileScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun guestButtonsNavigateLoginAndLogoutWithoutFeedStorage() {
        val auth = FakeAuthRepository()
        compose.setContent { MobileApp(null, null, true, { "" }, auth) }
        compose.onNodeWithText("Профиль").performClick()
        compose.onNodeWithText("Добро пожаловать").assertIsDisplayed()
        compose.onNodeWithTag("profile.login").assertIsDisplayed()
        compose.onNodeWithTag("profile.register").assertIsDisplayed()
        capture("android-profile-guest")
        compose.onNodeWithTag("profile.login").performClick()
        compose.onNodeWithText("Новости").assertDoesNotExist()
        compose.onNodeWithTag("auth.email").performTextInput("reader@example.test")
        compose.onNodeWithTag("auth.password").performTextInput("test-password")
        compose.onNodeWithTag("auth.submit").performScrollTo().performClick()
        compose.onNodeWithText("reader@example.test").assertIsDisplayed()
        compose.onNodeWithText("Выйти").performClick()
        compose.onNodeWithText("Добро пожаловать").assertIsDisplayed()
    }

    @Test
    fun registrationShowsValidationThenCodeAndResendCountdown() {
        val auth = FakeAuthRepository()
        compose.setContent { MobileApp(null, null, true, { "" }, auth) }
        compose.onNodeWithText("Профиль").performClick()
        compose.onNodeWithTag("profile.register").performClick()
        compose.onNodeWithTag("auth.submit").performClick()
        compose.onNodeWithText("Введите корректный email").assertIsDisplayed()
        compose.onNodeWithTag("auth.email").performTextInput("reader@example.test")
        compose.onNodeWithTag("auth.password").performTextInput("test-password")
        compose.onNodeWithTag("auth.repeatPassword").performScrollTo().performTextInput("test-password")
        compose.onNodeWithTag("auth.submit").performScrollTo().performClick()
        compose.onNodeWithTag("auth.code").assertIsDisplayed()
        capture("android-auth-code")
        compose.onNodeWithTag("auth.code").performTextInput("012345")
        compose.onNodeWithTag("auth.submit").performScrollTo().performClick()
        compose.onNodeWithText("reader@example.test").assertIsDisplayed()
    }

    @Test
    fun largeTextKeepsGuestActionsReachable() {
        val auth = FakeAuthRepository()
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode =
                    Configuration.UI_MODE_NIGHT_YES
            }
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f), LocalConfiguration provides configuration) {
                Box(Modifier.width(320.dp)) { MobileApp(null, null, true, { "" }, auth) }
            }
        }
        compose.onNodeWithText("Профиль").performClick()
        compose.onNodeWithTag("profile.register").performScrollTo().assertIsDisplayed()
        capture("android-profile-large-text")
    }

    @Test
    fun restoredFormRetainsRouteAndBackClearsSecrets() {
        val auth = FakeAuthRepository()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MobileApp(null, null, true, { "" }, auth) }
        compose.onNodeWithText("Профиль").performClick()
        compose.onNodeWithTag("profile.login").performClick()
        compose.onNodeWithTag("auth.email").performTextInput("reader@example.test")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("auth.email").assertTextContains("reader@example.test")
        compose.onNodeWithText("Назад").performClick()
        compose.onNodeWithTag("profile.login").performClick()
        compose.onNodeWithTag("auth.email").assertTextContains("Email")
    }

    @Test
    fun androidKeystorePersistsCiphertextAndDeletesSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "auth-storage-test").apply { mkdirs() }
        val testContext = object : ContextWrapper(context) {
            override fun getNoBackupFilesDir(): File = directory
        }
        val storage = AndroidAuthStorage(testContext)
        try {
            assertTrue(storage.write("fixture-session-secret"))
            val recreated = AndroidAuthStorage(testContext)
            assertEquals("fixture-session-secret", recreated.read().value)
            assertFalse(
                File(
                    directory,
                    "auth-session.bin",
                ).readBytes().toString(Charsets.UTF_8).contains("fixture-session-secret"),
            )
            assertTrue(recreated.write(null))
            assertEquals(null, storage.read().value)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        file.outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
