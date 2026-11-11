package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.calculator.FirstLaunchVaultSetupSheet
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.stringsForLanguage
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.XomeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnboardingFieldLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun sampleKeyfile() = XomeViewModel.KeyfileSelection(
        displayName = "sample.pdf",
        byteCount = 4096L,
        pepper = ByteArray(32) { 9 }
    )

    private fun show(
        width: Dp,
        language: AppLanguage = AppLanguage.ZH,
        fontScale: Float = 1f,
        initialKeyfile: XomeViewModel.KeyfileSelection? = sampleKeyfile(),
        submit: (String, String, ByteArray?, String, Boolean) -> Unit = { _, _, _, _, _ -> }
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalAppStrings provides stringsForLanguage(language),
                LocalDensity provides Density(density.density, fontScale)
            ) {
                MyApplicationTheme {
                    Box(Modifier.requiredWidth(width)) {
                        FirstLaunchVaultSetupSheet(
                            isProvisioning = false,
                            errorText = null,
                            isRecovering = false,
                            recoveryError = null,
                            unsealedRecoverableSpaces = 0,
                            onProvision = submit,
                            onExtractKeyfileDigest = { Result.failure(IllegalStateException("unused")) },
                            onRecoverSpace = { _, _ -> },
                            onSkipToCalculator = {},
                            onRestoreArchive = {},
                            initialKeyfile = initialKeyfile
                        )
                    }
                }
            }
        }
    }

    private fun assertEqualFieldHeights(): Pair<androidx.compose.ui.geometry.Rect, androidx.compose.ui.geometry.Rect> {
        val alias = compose.onNodeWithTag("onboarding_alias_input").fetchSemanticsNode().boundsInRoot
        val pin = compose.onNodeWithTag("onboarding_pin_input").fetchSemanticsNode().boundsInRoot
        val confirmation = compose.onNodeWithTag("onboarding_confirm_input").fetchSemanticsNode().boundsInRoot
        assertEquals("space name and secret field height", alias.height, pin.height, 1f)
        assertEquals("both secret field heights", pin.height, confirmation.height, 1f)
        return pin to confirmation
    }

    @Test fun regularPhoneUsesTwoEqualHeightSingleLineFields() {
        show(390.dp)
        val (pin, confirmation) = assertEqualFieldHeights()
        assertEquals("two fields have equal top baseline", pin.top, confirmation.top, 1f)
        assertTrue("separate controls", pin.right < confirmation.left)
    }

    @Test fun narrowPhoneStacksTwoEqualHeightFieldsInsteadOfWrappingLabels() {
        show(320.dp)
        val (pin, confirmation) = assertEqualFieldHeights()
        assertTrue("sequence and confirmation stack vertically", confirmation.top > pin.bottom)
        assertEquals("columns share left edge", pin.left, confirmation.left, 1f)
        assertEquals("columns share width", pin.width, confirmation.width, 1f)
    }

    @Test fun englishLabelsAlsoUseConsistentHeight() {
        show(390.dp, AppLanguage.EN)
        val (pin, confirmation) = assertEqualFieldHeights()
        assertEquals(pin.top, confirmation.top, 1f)
    }

    @Test fun largerAccessibilityFontsSwitchToStackedLayout() {
        show(390.dp, fontScale = 1.4f)
        val (pin, confirmation) = assertEqualFieldHeights()
        assertTrue(confirmation.top > pin.bottom)
    }

    @Test fun compactFormStillSubmitsBothSecretsAndSpaceName() {
        var submitted: List<String>? = null
        var submittedPepperSize = -1
        show(390.dp) { pin, confirmation, pepper, alias, _ ->
            submitted = listOf(pin, confirmation, alias)
            submittedPepperSize = pepper?.size ?: -1
        }
        compose.onNodeWithTag("onboarding_keyfile_selected_name").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("onboarding_pin_input").performScrollTo().performTextInput("123+45")
        compose.onNodeWithTag("onboarding_confirm_input").performScrollTo().performTextInput("123+45")
        compose.onNodeWithTag("onboarding_provision_button").performScrollTo().performClick()
        assertEquals(listOf("123+45", "123+45", "主空间"), submitted)
        assertEquals(32, submittedPepperSize)
    }

    @Test fun recoverTabExposesKeyfilePickerAndSequenceInput() {
        show(390.dp, initialKeyfile = null)
        compose.onNodeWithTag("onboarding_tab_recover").performClick()
        compose.onNodeWithTag("recover_keyfile_card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("onboarding_recover_pin_input").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("onboarding_recover_button").performScrollTo().assertIsDisplayed()
    }
}
