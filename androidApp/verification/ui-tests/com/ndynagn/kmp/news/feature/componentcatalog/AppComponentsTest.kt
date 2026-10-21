package com.ndynagn.kmp.news.feature.componentcatalog

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.ndynagn.kmp.news.components.designsystem.appColorScheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppComponentsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun hostColorSchemeRecolorsExistingButtonWithoutRecreation() {
        var scheme by mutableStateOf(appColorScheme(isDark = false))
        compose.setContent {
            AppTheme(scheme) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppActionButton("Action", {}, Modifier.width(200.dp).testTag("theme.action"))
                }
            }
        }

        for (provided in listOf(
            appColorScheme(isDark = false),
            lightColorScheme(primary = Color(0xFF795900), onPrimary = Color.White),
        )) {
            compose.runOnIdle { scheme = provided }
            val image = compose.onNodeWithTag("theme.action").captureToImage()
            val pixel = image.toPixelMap()[image.width / 2, image.height / 5]

            assertEquals(provided.primary.toArgb(), pixel.toArgb())
        }
    }

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
        compose.onNodeWithContentDescription("Back").performClick()
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

        compose.onNodeWithContentDescription("Show").performClick()

        compose.onNodeWithTag("password").assertIsFocused().assertTextContains("demo-password")
        compose.runOnIdle { assertEquals("demo-password", password) }
    }
}
