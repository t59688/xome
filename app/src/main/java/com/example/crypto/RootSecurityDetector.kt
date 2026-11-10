package com.example.crypto

import android.os.Build
import java.io.File

/**
 * Detects Root (su binaries, Magisk) and Xposed/LSPosed hook frameworks.
 * Used to display a non-blocking security notice in the Vault Settings screen
 * warning that privileged processes could bypass system FLAG_SECURE screenshot protection.
 */
object RootSecurityDetector {

    data class EnvironmentSecurityReport(
        val isRootOrHookDetected: Boolean,
        val suBinaryFound: Boolean,
        val magiskDetected: Boolean,
        val xposedDetected: Boolean,
        val testKeysBuild: Boolean,
        val findings: List<String>
    )

    private val SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su",
        "/su/bin/su",
        "/product/bin/su",
        "/apex/com.android.runtime/bin/su"
    )

    private val MAGISK_PATHS = listOf(
        "/sbin/.magisk",
        "/cache/.disable_magisk",
        "/dev/.magisk.unblock",
        "/data/adb/magisk",
        "/data/adb/ksud"
    )

    fun inspectEnvironment(): EnvironmentSecurityReport {
        val findings = mutableListOf<String>()

        val foundSu = SU_PATHS.firstOrNull { path ->
            try {
                File(path).exists()
            } catch (_: Exception) {
                false
            }
        }
        if (foundSu != null) {
            findings.add("su binary found at $foundSu")
        }

        val foundMagisk = MAGISK_PATHS.firstOrNull { path ->
            try {
                File(path).exists()
            } catch (_: Exception) {
                false
            }
        }
        if (foundMagisk != null) {
            findings.add("Root manager artifact detected at $foundMagisk")
        }

        val xposedFound = detectXposedInRuntime()
        if (xposedFound) {
            findings.add("Xposed / ART Hook bridge detected in JVM runtime")
        }

        val buildTags = Build.TAGS ?: ""
        // Only flag test-keys if combined with su or Magisk so standard emulator builds aren't falsely flagged
        val testKeys = buildTags.contains("test-keys") && (foundSu != null || foundMagisk != null)
        if (testKeys) {
            findings.add("Unsigned / test-keys ROM signature detected")
        }

        val isCompromised = foundSu != null || foundMagisk != null || xposedFound
        return EnvironmentSecurityReport(
            isRootOrHookDetected = isCompromised,
            suBinaryFound = foundSu != null,
            magiskDetected = foundMagisk != null,
            xposedDetected = xposedFound,
            testKeysBuild = testKeys,
            findings = findings
        )
    }

    private fun detectXposedInRuntime(): Boolean {
        return try {
            Class.forName("de.robv.android.xposed.XposedBridge")
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: Throwable) {
            false
        }
    }
}
