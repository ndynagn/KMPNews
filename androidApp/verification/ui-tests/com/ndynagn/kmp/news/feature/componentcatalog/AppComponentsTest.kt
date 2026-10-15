package com.ndynagn.kmp.news.feature.componentcatalog

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.ndynagn.kmp.news.components.designsystem.AppActionButton
import com.ndynagn.kmp.news.components.designsystem.AppFormMessage
import com.ndynagn.kmp.news.components.designsystem.AppPasswordField
import com.ndynagn.kmp.news.components.designsystem.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppComponentsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingKeepsWrappedButtonGeometryAndPreventsRepeatedActions() {
        var busy by mutableStateOf(false)
        var actions = 0
        compose.setContent {
            AppTheme {
                AppActionButton(
                    "A long action title that wraps across several lines",
                    { actions += 1 },
                    Modifier.width(160.dp).testTag("action"),
                    isBusy = busy,
                )
            }
        }
        val initial = compose.onNodeWithTag("action").getBoundsInRoot()

        compose.runOnIdle { busy = true }

        compose.onNodeWithTag("action").assertIsNotEnabled().performClick()
        assertEquals(initial, compose.onNodeWithTag("action").getBoundsInRoot())
        compose.runOnIdle { assertEquals(0, actions) }
    }

    @Test
    fun confirmationAndResendRenderTheirPendingOperation() {
        var operation by mutableStateOf<CatalogAuthOperation?>(CatalogAuthOperation.SUBMIT)
        compose.setContent {
            AppTheme {
                CatalogAuthScreen(
                    CatalogAuthState(path = listOf(CatalogAuthStep.CONFIRM), operation = operation),
                    CatalogAuthActions({}, {}, {}, {}, {}, {}, {}, {}),
                    onBack = { operation = null },
                    onClose = { operation = null },
                )
            }
        }

        compose.onNodeWithTag("kit.auth.progress").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("kit.auth.resend").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { operation = CatalogAuthOperation.RESEND }
        compose.onNodeWithTag("kit.auth.resend").performScrollTo().assertIsNotEnabled().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Loading"),
        )
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithTag("kit.auth.resend").assert(
            SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription),
        )
    }

    @Test
    fun formErrorAnnouncesUpdatedFeedback() {
        compose.setContent {
            AppTheme { AppFormMessage("Please try again", Modifier.testTag("error"), isError = true) }
        }

        compose.onNodeWithTag("error").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        )
    }

    @Test
    fun visibilityTogglePreservesPasswordAndEditorFocus() {
        var password by mutableStateOf("")
        compose.setContent {
            AppTheme {
                Column {
                    AppPasswordField(password, { password = it }, "Password", Modifier.testTag("password"))
                }
            }
        }
        compose.onNodeWithTag("password").performClick().performTextInput("demo-password")

        compose.onNodeWithText("Show").performClick()

        compose.onNodeWithTag("password").assertIsFocused().assertTextContains("demo-password")
        compose.runOnIdle { assertEquals("demo-password", password) }
    }
}
