package com.example

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.ui.graph.GraphStudioScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GraphStudioUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun graphEditingSwitchingAndReopeningRestoresFormula() {
        var show by mutableStateOf(true)
        compose.setContent {
            MyApplicationTheme {
                if (show) {
                    GraphStudioScreen(onClose = { show = false })
                } else {
                    Button(onClick = { show = true }) { Text("Open graph") }
                }
            }
        }
        compose.onNodeWithTag("graph_studio_root").assertIsDisplayed()
        compose.onNodeWithTag("graph_formula_0").assertDoesNotExist()
        compose.onNodeWithTag("graph_formula_panel_toggle").performClick()
        compose.onNodeWithTag("graph_formula_0").performTextClearance()
        compose.onNodeWithTag("graph_formula_0").performTextInput("sin(3*x)")
        compose.onNodeWithTag("graph_formula_0").assertTextContains("sin(3*x)")
        compose.onNodeWithTag("graph_mode_POLAR").performClick()
        compose.onNodeWithTag("graph_mode_VECTOR_FIELD").performClick()
        compose.onNodeWithTag("graph_mode_CARTESIAN").performClick()
        compose.onNodeWithTag("graph_formula_panel_toggle").performClick()
        compose.onNodeWithTag("graph_formula_0").assertTextContains("sin(3*x)")
        compose.onNodeWithTag("graph_close").performClick()
        compose.onNodeWithText("Open graph").performClick()
        compose.onNodeWithTag("graph_formula_panel_toggle").performClick()
        compose.onNodeWithTag("graph_formula_0").assertTextContains("sin(3*x)")
    }

    @Test fun waveformTabIsFunctionalAndKeepsMathFormulas() {
        compose.setContent { MyApplicationTheme { GraphStudioScreen(onClose = {}) } }
        compose.onNodeWithTag("graph_category_WAVE").performClick()
        compose.onNodeWithTag("graph_formula_panel_toggle").performClick()
        compose.onNodeWithTag("graph_formula_0").assertTextContains("sin(", substring = true)
        compose.onNodeWithTag("graph_2d_canvas").assertExists()
        compose.onNodeWithTag("graph_category_2D").performClick()
        compose.onNodeWithTag("graph_category_WAVE").performClick()
        compose.onNodeWithTag("graph_formula_panel_toggle").performClick()
        compose.onNodeWithTag("graph_formula_0").assertTextContains("sin(", substring = true)
    }

    @Test fun fullscreenHidesEditorAndRestoresItOnExit() {
        compose.setContent {
            MyApplicationTheme { GraphStudioScreen(onClose = {}) }
        }
        compose.onNodeWithTag("graph_formula_panel_toggle").assertExists()
        compose.onNodeWithTag("graph_fullscreen").performClick()
        compose.onNodeWithTag("graph_formula_panel_toggle").assertDoesNotExist()
        compose.onNodeWithTag("graph_fullscreen").performClick()
        compose.onNodeWithTag("graph_formula_panel_toggle").assertExists()
    }

    @Test fun invalidFormulaShowsInlineErrorAndCanBeRepaired() {
        compose.setContent {
            MyApplicationTheme { GraphStudioScreen(onClose = {}) }
        }
        compose.onNodeWithTag("graph_formula_panel_toggle").performClick()
        compose.onNodeWithTag("graph_formula_0").performTextClearance()
        compose.onNodeWithTag("graph_formula_0").performTextInput("sin(")
        compose.onNodeWithTag("graph_formula_error", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("graph_formula_0").performTextClearance()
        compose.onNodeWithTag("graph_formula_0").performTextInput("sin(x)")
        compose.onNodeWithTag("graph_formula_error", useUnmergedTree = true).assertDoesNotExist()
    }
}
