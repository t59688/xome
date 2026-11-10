package com.example

import com.example.update.ReleaseVersion
import com.example.update.GitHubRelease
import com.example.update.UpdateChannel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GitHubUpdateTest {
    @Test fun `version comparison uses numeric components and beta order`() {
        assertTrue(ReleaseVersion.parse("v1.10.0")!! > ReleaseVersion.parse("1.9.9")!!)
        assertTrue(ReleaseVersion.parse("1.0.0")!! > ReleaseVersion.parse("1.0.0-beta.9")!!)
        assertFalse(ReleaseVersion.parse("1.0.0-beta.9")!! > ReleaseVersion.parse("1.0.0")!!)
        assertEquals(10_000_999L, ReleaseVersion.parse("1.0.0")!!.versionCode)
        assertNull(ReleaseVersion.parse("1.100.0"))
        assertNull(ReleaseVersion.parse("../1.0.0"))
    }

    @Test fun `release chooses the published APK instead of bundle or debug build`() {
        val release = GitHubRelease.parse(json())!!
        assertEquals("1.1.0", release.version.name)
        assertEquals("xome-1.1.0-release.apk", release.assetName)
        assertEquals(123L, release.size)
    }

    @Test fun `draft prerelease and APK from another repository are rejected`() {
        assertNull(GitHubRelease.parse(json().replace("\"draft\":false", "\"draft\":true")))
        assertNull(GitHubRelease.parse(json().replace("\"prerelease\":false", "\"prerelease\":true")))
        assertNull(GitHubRelease.parse(json().replace("t59688/xome/releases/download", "other/project/releases/download")))
    }

    @Test fun `malformed digest and releases with no installable APK are rejected`() {
        assertNull(GitHubRelease.parse(json().replace("\"digest\":null", "\"digest\":\"sha256:bad\"")))
        assertNull(GitHubRelease.parse(json().replace("xome-1.1.0-release.apk", "app-debug.apk")))
    }

    @Test fun `beta channel accepts only matching prerelease APK and stable ignores it`() {
        val betaJson = json()
            .replace("v1.1.0", "v1.2.0-beta.2")
            .replace("xome-1.1.0-release.apk", "xome-1.2.0-beta.2-release.apk")
            .replace("\"prerelease\":false", "\"prerelease\":true")
        assertNull(GitHubRelease.parse(betaJson, UpdateChannel.STABLE))
        val parsed = GitHubRelease.parse(betaJson, UpdateChannel.BETA)
        assertNotNull(parsed)
        assertEquals("1.2.0-beta.2", parsed!!.version.name)
        assertNotNull(GitHubRelease.parse(json(), UpdateChannel.BETA))
    }

    private fun json() = """{
        "tag_name":"v1.1.0","name":"PhonePulse 1.1.0","body":"更新说明",
        "draft":false,"prerelease":false,
        "assets":[
          {"name":"app-debug.apk","size":123,"browser_download_url":"https://github.com/t59688/xome/releases/download/v1.1.0/app-debug.apk"},
          {"name":"xome-1.1.0-release.aab","size":123},
          {"name":"xome-1.1.0-release.apk","size":123,"digest":null,
           "browser_download_url":"https://github.com/t59688/xome/releases/download/v1.1.0/xome-1.1.0-release.apk"}
        ]
    }"""
}
