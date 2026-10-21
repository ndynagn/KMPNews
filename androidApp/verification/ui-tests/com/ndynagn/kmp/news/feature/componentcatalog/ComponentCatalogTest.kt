package com.ndynagn.kmp.news.feature.componentcatalog

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.ndynagn.kmp.news.R
import java.io.File
import org.junit.Rule
import org.junit.Test

class ComponentCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentCatalogActivity>()

    @Test
    fun pullToRefreshChangesSampleContentAndLeavesWithoutPendingWork() {
        open("navigation")
        compose.onNodeWithTag("kit.refresh").performScrollTo()

        compose.onNodeWithTag("kit.refresh.list").performTouchInput { swipeDown() }
        compose.waitUntil(5_000) {
            compose.onAllNodes(
                androidx.compose.ui.test.hasText(compose.activity.getString(R.string.catalog_refresh_count, 1)),
            ).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText(compose.activity.getString(R.string.catalog_refresh_count, 1)).assertIsDisplayed()
        capture("selected-kit-pull-refresh")
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.catalog_back)).performClick()
        open("news")
        compose.onNodeWithTag("kit.news.noImage").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun catalogSectionsRenderAndReturnThroughNativeBack() {
        capture("catalog-root")
        for (section in listOf(
            "foundations",
            "actions",
            "fields",
            "navigation",
            "news",
            "states",
            "profile",
            "forms",
        )) {
            compose.onNodeWithTag("kit.section.$section").performScrollTo().performClick()
            compose.waitForIdle()
            capture("catalog-$section")
            if (section == "foundations") {
                compose.onNodeWithContentDescription(
                    compose.activity.getString(R.string.catalog_dynamic_colors),
                ).performClick()
                capture("catalog-foundations-fallback")
            }
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.catalog_back)).performClick()
        }
    }

    @Test
    fun registrationRetainsCodeFlowOnRecreationAndClearsItOnClose() {
        compose.onNodeWithTag("kit.section.forms").performScrollTo().performClick()
        compose.onNodeWithTag("kit.open.register").performClick()
        compose.onNodeWithTag("kit.auth.email").performTextInput("reader@example.test")
        compose.onNodeWithTag("kit.auth.password").performScrollTo().performTextInput("demo-password")
        compose.onNodeWithTag("kit.auth.repeatPassword").performScrollTo().performTextInput("demo-password")
        compose.onNodeWithTag("kit.auth.submit").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("kit.auth.code")).fetchSemanticsNodes().isNotEmpty()
        }
        capture("catalog-confirm")

        compose.activityRule.scenario.recreate()

        compose.onNodeWithTag("kit.auth.code").assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.catalog_close)).performClick()
        compose.onNodeWithTag("kit.open.register").performClick()
        compose.onNodeWithTag("kit.auth.email").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")),
        )
        capture("catalog-fresh-registration")
    }

    @Test
    fun actionSwitchesUpdateValueAndButtonsImmediatelyInBothDirections() {
        open("actions")
        val loading = compose.onNodeWithContentDescription(compose.activity.getString(R.string.catalog_loading))
        val disabled = compose.onNodeWithContentDescription(compose.activity.getString(R.string.catalog_disabled))
        val primary = compose.onNodeWithTag("kit.action.primary")
        loading.assertIsOff().performClick().assertIsOn()
        primary.assertIsNotEnabled()
        capture("actions-busy")
        loading.performClick().assertIsOff()
        primary.assertIsEnabled()
        disabled.performClick().assertIsOn()
        primary.assertIsNotEnabled()
        disabled.performClick().assertIsOff()
        primary.assertIsEnabled().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.catalog_feedback)).assertIsDisplayed()
    }

    @Test
    fun paletteAndImageSwitchesAreReversibleWithoutReopeningSection() {
        open("foundations")
        val palette = compose.onNodeWithContentDescription(compose.activity.getString(R.string.catalog_dynamic_colors))
        palette.assertIsOn().performClick().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.catalog_back)).performClick()
        open("news")
        val loaded = compose.onNodeWithContentDescription(compose.activity.getString(R.string.catalog_image_loaded))
        loaded.performClick().assertIsOn()
        compose.onNodeWithTag("kit.news.image.false").assertIsDisplayed()
        val unavailable = compose.onNodeWithContentDescription(
            compose.activity.getString(R.string.catalog_image_unavailable),
        )
        unavailable.performClick().assertIsOn()
        compose.onNodeWithTag("kit.news.image.false").assertDoesNotExist()
        unavailable.performClick().assertIsOff()
        compose.onNodeWithTag("kit.news.image.false").assertIsDisplayed()
        capture("news-loaded")
        loaded.performClick().assertIsOff()
        compose.onNodeWithTag("kit.news.image.false").assertDoesNotExist()
    }

    @Test
    fun fieldsAreEditableRetainInputOnRotationAndPasswordFocusOnVisibilityChange() {
        open("fields")
        compose.onNodeWithTag("kit.fields.email").performTextInput("reader@example.test")
        val password = compose.onNodeWithTag("kit.fields.password")
        password.performTextInput("demo-password")
        password.performTextInputSelection(TextRange(2, 5))
        val selection = password.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        compose.onNodeWithContentDescription("Show").performClick()
        password.assertIsFocused().assertTextContains("demo-password")
        password.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, selection))
        compose.onNodeWithTag("kit.fields.code").performScrollTo().performTextInput("001234")
        compose.onNodeWithTag("kit.fields.error").performScrollTo().performTextReplacement("edited@example.test")
        compose.onNodeWithTag("kit.fields.disabled").performScrollTo().assertIsNotEnabled()

        compose.activityRule.scenario.recreate()

        compose.onNodeWithTag("kit.fields.email").performScrollTo().assertTextContains("reader@example.test")
        password.performScrollTo().assertTextContains("demo-password")
        compose.onNodeWithTag("kit.fields.code").performScrollTo().assertTextContains("001234")
        compose.onNodeWithTag("kit.fields.error").performScrollTo().assertTextContains("edited@example.test")
        capture("fields-retained")
    }

    @Test
    fun profileLoginSaveCancelAndLogoutUpdateCurrentEntry() {
        signInProfile()
        compose.onNodeWithTag("kit.profile.savedEmail").assertTextContains("reader@example.test")
        compose.onNodeWithTag("kit.profile.edit").performClick()
        compose.onNodeWithTag("kit.profile.name").performTextReplacement("Updated Reader")
        compose.onNodeWithTag("kit.profile.email").performTextReplacement("updated@example.test")
        closeSoftKeyboard()
        compose.onNodeWithTag("kit.profile.save").performScrollTo().performClick()
        waitFor("kit.profile.savedEmail")
        compose.onNodeWithTag("kit.profile.savedName").assertTextContains("Updated Reader")
        compose.onNodeWithTag("kit.profile.savedEmail").assertTextContains("updated@example.test")
        capture("profile-saved")
        compose.onNodeWithTag("kit.profile.edit").performClick()
        compose.onNodeWithTag("kit.profile.name").performTextReplacement("Discard me")
        closeSoftKeyboard()
        compose.onNodeWithTag("kit.profile.cancel").performScrollTo().performClick()
        capture("profile-after-cancel")
        compose.onNodeWithTag("kit.profile.savedName").assertTextContains("Updated Reader")
        compose.onNodeWithTag("kit.profile.logout").performScrollTo().performClick()
        compose.onNodeWithTag("kit.open.login").assertIsDisplayed()
        compose.onNodeWithTag("kit.profile.savedEmail").assertDoesNotExist()
    }

    @Test
    fun profileErrorRetryAndBackDuringSavePreservePublishedProfile() {
        signInProfile()
        compose.onNodeWithTag("kit.profile.edit").performClick()
        compose.onNodeWithTag("kit.profile.name").performTextReplacement("Retry Reader")
        compose.onNodeWithTag("kit.profile.outcome.temporary_error").performScrollTo().performClick()
        compose.onNodeWithTag("kit.profile.save").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(
                androidx.compose.ui.test.hasText(compose.activity.getString(R.string.catalog_temporary_error)),
            )
                .fetchSemanticsNodes().size == 2
        }
        compose.onNodeWithTag("kit.profile.name").performScrollTo().assertTextContains("Retry Reader")
        compose.onNodeWithTag("kit.profile.outcome.success").performScrollTo().performClick()
        compose.onNodeWithTag("kit.profile.save").performScrollTo().performClick()
        waitFor("kit.profile.savedName")
        compose.onNodeWithTag("kit.profile.savedName").assertTextContains("Retry Reader")
        compose.onNodeWithTag("kit.profile.edit").performClick()
        compose.onNodeWithTag("kit.profile.name").performTextReplacement("Late Reader")
        compose.onNodeWithTag("kit.profile.outcome.slow").performScrollTo().performClick()
        compose.onNodeWithTag("kit.profile.save").performScrollTo().performClick().assertIsNotEnabled()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("kit.profile.savedName").assertTextContains("Retry Reader")
    }

    @Test
    fun confirmationRetryAndResendCooldownRespondToRealActions() {
        open("forms")
        compose.onNodeWithTag("kit.open.confirm").performScrollTo().performClick()
        compose.onNodeWithTag("kit.auth.resend").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText(compose.activity.getString(R.string.catalog_code_sent)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("kit.auth.resend").assertIsNotEnabled()
        compose.onNodeWithTag("kit.auth.outcome.temporary_error").performScrollTo().performClick()
        compose.onNodeWithTag("kit.auth.code").performScrollTo().performTextInput("001234")
        waitFor("kit.auth.submit")
        compose.onNodeWithTag("kit.auth.code").assertTextContains("001234")
        compose.onNodeWithTag("kit.auth.outcome.success").performScrollTo().performClick()
        compose.onNodeWithTag("kit.auth.submit").performScrollTo().performClick()
        waitFor("kit.open.confirm")
    }

    @Test
    fun systemBackCancelsPendingRecoveryRetainsEmailAndClearsPassword() {
        open("forms")
        compose.onNodeWithTag("kit.open.login").performClick()
        compose.onNodeWithTag("kit.auth.email").performTextInput("reader@example.test")
        compose.onNodeWithTag("kit.auth.password").performTextInput("demo-password")
        compose.onNodeWithTag("kit.auth.outcome.slow").performScrollTo().performClick()
        compose.onNodeWithTag("kit.auth.recovery").performScrollTo().performClick()
        compose.onNodeWithTag("kit.auth.submit").performScrollTo().performClick().assertIsNotEnabled()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithTag("kit.auth.email").performScrollTo().assertTextContains("reader@example.test")
        compose.onNodeWithTag("kit.auth.password").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")),
        )
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("kit.open.login").performClick()
        compose.onNodeWithTag("kit.auth.email").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")),
        )
    }

    @Test
    fun profileDraftSurvivesRotationAndSlowSaveCompletes() {
        signInProfile()
        compose.onNodeWithTag("kit.profile.edit").performClick()
        compose.onNodeWithTag("kit.profile.name").performTextReplacement("Rotated Reader")
        compose.onNodeWithTag("kit.profile.email").performTextReplacement("rotation@example.test")

        compose.activityRule.scenario.recreate()

        compose.onNodeWithTag("kit.profile.name").assertTextContains("Rotated Reader")
        compose.onNodeWithTag("kit.profile.email").assertTextContains("rotation@example.test")
        compose.onNodeWithTag("kit.profile.outcome.slow").performScrollTo().performClick()
        compose.onNodeWithTag("kit.profile.save").performScrollTo().performClick().assertIsNotEnabled()
        compose.waitUntil(12_000) {
            compose.onAllNodes(
                androidx.compose.ui.test.hasTestTag("kit.profile.savedEmail"),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("kit.profile.savedEmail").assertTextContains("rotation@example.test")
    }

    @Test
    fun retryTerminatesAndCompactStateKeepsContent() {
        open("states")
        compose.onNodeWithTag("kit.state.select.error").performScrollTo().performClick()
        compose.onNodeWithTag("kit.state.retry").performScrollTo().performClick()
        waitFor("kit.state.content")
        compose.onNodeWithTag("kit.state.content").assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.catalog_compact))
            .performScrollTo().performClick().assertIsOn()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.catalog_retry_fails))
            .performClick().assertIsOn()
        compose.onNodeWithTag("kit.state.select.error").performScrollTo().performClick()
        compose.onNodeWithTag("kit.state.retry").performScrollTo().performClick()
        waitFor("kit.state.error")
        compose.onNodeWithTag("kit.state.retry").assertIsEnabled()
        capture("states-compact-error")
    }

    @Test
    fun navigationTabsChangeContentAndDialogDismisses() {
        open("navigation")
        compose.onNodeWithTag("kit.tab.news").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.catalog_favorites)).performClick()
        compose.onNodeWithTag("kit.tab.favorites").assertIsDisplayed()
        compose.onNodeWithTag("kit.tab.news").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.catalog_profile)).performClick()
        compose.onNodeWithTag("kit.tab.profile").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.catalog_dialog)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.catalog_confirmation)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.catalog_confirm)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.catalog_confirmation)).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.catalog_feedback)).assertIsDisplayed()
    }

    @Test
    fun confirmationAndRecoveryHaveIndependentEntrypoints() {
        open("forms")
        compose.onNodeWithTag("kit.open.confirm").performScrollTo().performClick()
        compose.onNodeWithTag("kit.auth.code").performTextInput("001234")
        waitFor("kit.open.confirm")
        compose.onNodeWithTag("kit.open.recovery").performScrollTo().performClick()
        compose.onNodeWithTag("kit.auth.email").performTextInput("reader@example.test")
        compose.onNodeWithTag("kit.auth.submit").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(
                androidx.compose.ui.test.hasText(compose.activity.getString(R.string.catalog_recovery_sent)),
            )
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.catalog_close)).performClick()
        compose.onNodeWithTag("kit.open.recovery").performScrollTo().performClick()
        compose.onNodeWithTag("kit.auth.email").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")),
        )
    }

    private fun open(section: String) {
        compose.onNodeWithTag("kit.section.$section").performScrollTo().performClick()
    }

    private fun signInProfile() {
        open("profile")
        compose.onNodeWithTag("kit.open.login").performClick()
        compose.onNodeWithTag("kit.auth.email").performTextInput("reader@example.test")
        compose.onNodeWithTag("kit.auth.password").performTextInput("demo-password")
        closeSoftKeyboard()
        compose.onNodeWithTag("kit.auth.submit").performScrollTo().performClick()
        waitFor("kit.profile.savedEmail")
    }

    private fun waitFor(tag: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        val directory = File(instrumentation.targetContext.filesDir, "kit-evidence").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
}
