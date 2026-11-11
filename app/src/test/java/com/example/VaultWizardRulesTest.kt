package com.example

import com.example.ui.vault.VaultWizardRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultWizardRulesTest {
    @Test fun `space names reject empty control characters and overlong aliases`() {
        assertFalse(VaultWizardRules.validAlias("   "))
        assertFalse(VaultWizardRules.validAlias("bad\nname"))
        assertFalse(VaultWizardRules.validAlias("x".repeat(37)))
        assertTrue(VaultWizardRules.validAlias("访客空间"))
        assertTrue(VaultWizardRules.validAlias(" Work "))
    }

    @Test fun `unlock sequences support calculator expressions and enforce 6 to 64 chars`() {
        assertFalse(VaultWizardRules.validSequence("123+4"))
        assertFalse(VaultWizardRules.validSequence("1".repeat(64) + "+"))
        assertFalse(VaultWizardRules.validSequence("12\n34+"))
        assertTrue(VaultWizardRules.validSequence("9988+11"))
        assertTrue(VaultWizardRules.validSequence("1".repeat(63) + "+"))
        assertTrue(VaultWizardRules.validUnlockSequence("123+45"))
        assertFalse(VaultWizardRules.validUnlockSequence("12345"))
    }

    @Test fun `confirmation must agree on normalized sequence`() {
        assertTrue(VaultWizardRules.confirmedSequence(" 2580+1 ", "2580+1"))
        assertTrue(VaultWizardRules.confirmedSequence("12×345", "12*345"))
        assertTrue(VaultWizardRules.confirmedSequence("12÷345", "12/345"))
        assertTrue(VaultWizardRules.confirmedSequence("12−345", "12-345"))
        assertFalse(VaultWizardRules.confirmedSequence("2580+1", "2580+2"))
        assertFalse(VaultWizardRules.confirmedSequence("", ""))
    }

    @Test fun `credential rotation requires old and new to differ`() {
        assertFalse(VaultWizardRules.changedSequence("1314+2", "1314+2", "1314+2"))
        assertFalse(VaultWizardRules.changedSequence("1314+2", "7788+1", "1234+5"))
        assertFalse(VaultWizardRules.changedSequence("1314", "7788+1", "7788+1"))
        assertTrue(VaultWizardRules.changedSequence("1314+2", "7788+1", "7788+1"))
    }

    @Test fun newCredentialsRequireSixCharactersWithDigitsAndCalculatorSymbols() {
        listOf("1314", "123456", "++++++", "12+34", "12345a", "12345@", "123 4+", "１２３４５+").forEach {
            assertFalse("Must reject $it", VaultWizardRules.validSequence(it))
        }
        listOf("12345+", "12*345", "12×345", "12÷345", "12−345", "(1234)").forEach {
            assertTrue("Must accept $it", VaultWizardRules.validSequence(it))
        }
    }
}
