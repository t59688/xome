package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.vault.ChangeUnlockSequenceWizard
import com.example.ui.vault.CreateIndependentSpaceWizard
import com.example.ui.vault.WizardSwitchRow
import com.example.ui.theme.VaultPurpleDecoy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultSecurityWizardUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `standalone switch interaction works in Compose test environment`() {
        compose.setContent {
            val enabled = remember { mutableStateOf(true) }
            WizardSwitchRow(
                title = "生成示例文件",
                subtitle = "启用或关闭",
                valueState = enabled,
                accent = VaultPurpleDecoy,
                tag = "standalone_switch"
            )
        }
        compose.onNodeWithTag("standalone_switch").assertIsOn()
        compose.onNodeWithTag("standalone_switch").performClick()
        compose.onNodeWithTag("standalone_switch").assertIsOff()
    }

    @Test
    fun `guided creation passes verified data and default options`() {
        var completed = false
        var capturedAlias = ""
        var capturedSeed = true
        var capturedSequence = ""
        compose.setContent {
            MyApplicationTheme {
                CreateIndependentSpaceWizard(
                    initialAlias = "访客空间",
                    onCreate = { seq, confirm, alias, _, seed, _, cb ->
                        assertEquals(seq, confirm)
                        capturedSequence = seq
                        capturedAlias = alias
                        capturedSeed = seed
                        completed = true
                        cb(true, "ok")
                    },
                    onClose = {}
                )
            }
        }
        compose.onNodeWithText("为新空间命名").assertIsDisplayed()
        compose.onNodeWithTag("create_space_sequence").assertDoesNotExist()
        compose.onNodeWithTag("wizard_primary").performClick()

        compose.onNodeWithText("设置解锁序列").assertIsDisplayed()
        compose.onNodeWithTag("create_space_sequence").performTextInput("ab12")
        compose.onNodeWithTag("wizard_primary").assertIsNotEnabled()
        compose.onNodeWithTag("create_space_confirmation").performTextInput("ab12")
        compose.onNodeWithTag("wizard_primary").assertIsNotEnabled()
        compose.onNodeWithTag("create_space_sequence").performTextClearance()
        compose.onNodeWithTag("create_space_confirmation").performTextClearance()
        compose.onNodeWithTag("create_space_sequence").performTextInput("2345+6")
        compose.onNodeWithTag("create_space_confirmation").performTextInput("2345+6")
        compose.onNodeWithTag("wizard_primary").assertIsEnabled()
        compose.onNodeWithTag("wizard_primary").performClick()

        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("个性化新空间").assertIsDisplayed()
        compose.onNodeWithTag("create_space_seed_switch").assertIsOn()
        compose.onNodeWithTag("create_space_seed_switch").performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("create_space_seed_switch").assertIsOff()
        compose.onNodeWithTag("wizard_primary").performClick()

        compose.onNodeWithText("确认创建").assertIsDisplayed()
        compose.onNodeWithTag("wizard_primary").performClick()
        assertTrue(completed)
        assertFalse(capturedSeed)
        assertEquals("访客空间", capturedAlias)
        assertEquals("2345+6", capturedSequence)
        compose.onNodeWithText("新空间已创建").assertIsDisplayed()
        compose.onNodeWithText("上一步").assertDoesNotExist()
    }

    @Test
    fun `changing sequence verifies the old one before exposing new fields`() {
        var verifyAttempts = 0
        var didChange = false
        compose.setContent {
            MyApplicationTheme {
                ChangeUnlockSequenceWizard(
                    onVerify = { input, cb ->
                        verifyAttempts++
                        cb(input == "old1")
                    },
                    onChange = { old, next, repeated, cb ->
                        assertEquals("old1", old)
                        assertEquals("7890+1", next)
                        assertEquals(next, repeated)
                        didChange = true
                        cb(true, "updated")
                    },
                    onClose = {}
                )
            }
        }
        compose.onNodeWithText("验证当前序列").assertIsDisplayed()
        compose.onNodeWithTag("change_sequence_new").assertDoesNotExist()
        compose.onNodeWithTag("change_sequence_current").performTextInput("bad1")
        compose.onNodeWithTag("wizard_primary").performClick()
        assertEquals(1, verifyAttempts)
        compose.onNodeWithText("当前解锁序列不正确").assertIsDisplayed()
        compose.onNodeWithTag("change_sequence_new").assertDoesNotExist()

        compose.onNodeWithTag("change_sequence_current").performTextClearance()
        compose.onNodeWithTag("change_sequence_current").performTextInput("old1")
        compose.onNodeWithTag("wizard_primary").performClick()
        assertEquals(2, verifyAttempts)
        compose.onNodeWithText("设置新序列").assertIsDisplayed()
        compose.onNodeWithTag("change_sequence_new").performTextInput("7890+1")
        compose.onNodeWithTag("change_sequence_confirmation").performTextInput("7890+1")
        compose.onNodeWithTag("wizard_primary").performClick()
        compose.onNodeWithText("确认修改").assertIsDisplayed()
        compose.onNodeWithTag("wizard_primary").performClick()
        assertTrue(didChange)
        compose.onNodeWithText("密码已更新").assertIsDisplayed()
        compose.onNodeWithText("上一步").assertDoesNotExist()
    }
}
