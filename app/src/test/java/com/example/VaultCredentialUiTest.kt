package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.viewmodel.XomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultCredentialUiTest {
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun aPossibleCredentialIsNotRecordedWhileUnlockIsPending() {
        val vm = XomeViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.onCalculatorInput("123+45")
        vm.onCalculatorEqualsPressed()
        assertTrue(vm.calcState.value.history.isEmpty())
        assertEquals("168", vm.calcState.value.expression)
    }

    @Test fun ordinaryShortCalculationsStillHaveHistory() {
        val vm = XomeViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.onCalculatorInput("1+2")
        vm.onCalculatorEqualsPressed()
        assertEquals("1+2", vm.calcState.value.history.single().expression)
    }

    @Test fun initialSetupRejectsWeakAndUnreachableCredentialsBeforeProvisioning() {
        listOf("1314", "123456", "12345a", "12345@", "++++++").forEach { secret ->
            val vm = XomeViewModel(ApplicationProvider.getApplicationContext<Application>())
            vm.provisionInitialPrimarySpace(secret, secret)
            assertNotNull("Must reject $secret", vm.onboardingError.value)
        }
    }
}
