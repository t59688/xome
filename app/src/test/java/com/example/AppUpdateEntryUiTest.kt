package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.MyApplicationTheme
import com.example.update.GitHubRelease
import com.example.update.ReleaseVersion
import com.example.update.UpdateRepository
import com.example.viewmodel.XomeViewModel
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppUpdateEntryUiTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private lateinit var application: Application

    @Before fun setup() {
        application = ApplicationProvider.getApplicationContext()
        application.getSharedPreferences(UpdateRepository.PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun cleanup() { store.clear() }

    private fun showApp(): XomeViewModel {
        val vm = XomeViewModel(application)
        store.put("test", vm)
        compose.setContent {
            MyApplicationTheme {
                XomeRootHost(vm, {}, {}, {})
            }
        }
        return vm
    }

    @Test fun updatesCanBeOpenedFromCalculatorWithoutUnlocking() {
        val vm = showApp()
        compose.onNodeWithTag("calc_updates_button").assertIsDisplayed().performClick()
        compose.onNodeWithTag("app_update_sheet").assertIsDisplayed()
        compose.onNodeWithTag("update_check_button").assertIsDisplayed()
        assertNull(vm.activeSession.value)
    }

    @Test fun cachedNewReleasePromptsWithoutUnlockingAndLaterKeepsTheBadge() {
        val release = GitHubRelease(ReleaseVersion(1, 99, 0), "New version", "Notes",
            "xome-1.99.0-release.apk",
            "https://github.com/t59688/xome/releases/download/v1.99.0/xome-1.99.0-release.apk", 100)
        application.getSharedPreferences(UpdateRepository.PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString("release", release.toJson()).commit()
        val vm = showApp()
        compose.onNodeWithTag("app_update_prompt").assertIsDisplayed()
        compose.onNodeWithTag("update_remind_later").performClick()
        compose.onNodeWithTag("app_update_prompt").assertDoesNotExist()
        compose.onNodeWithTag("app_update_badge", useUnmergedTree = true).assertIsDisplayed()
        assertNull(vm.activeSession.value)
    }
}
