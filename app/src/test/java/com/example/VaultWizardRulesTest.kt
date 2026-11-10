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

    @Test fun `unlock sequences support calculator expressions not only numeric digits`() {
        assertFalse(VaultWizardRules.validSequence("123"))
        assertFalse(VaultWizardRules.validSequence("x".repeat(17)))
        assertFalse(VaultWizardRules.validSequence("12\n34"))
        assertTrue(VaultWizardRules.validSequence("9988+11"))
        assertTrue(VaultWizardRules.validSequence("x".repeat(16)))
    }

    @Test fun `confirmation must agree on normalized sequence`() {
        assertTrue(VaultWizardRules.confirmedSequence(" 2580 ", "2580"))
        assertFalse(VaultWizardRules.confirmedSequence("2580", "2581"))
        assertFalse(VaultWizardRules.confirmedSequence("", ""))
    }

    @Test fun `credential rotation requires old and new to differ`() {
        assertFalse(VaultWizardRules.changedSequence("1314", "1314", "1314"))
        assertFalse(VaultWizardRules.changedSequence("1314", "7788", "1234"))
        assertTrue(VaultWizardRules.changedSequence("1314", "7788", "7788"))
    }
}
