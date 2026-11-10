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
    @Test fun `update prefers the first supported architecture over universal and other architectures`() {
        val base = json()
        val universalAsset = org.json.JSONObject(base).getJSONArray("assets").getJSONObject(2)
        val root = org.json.JSONObject(base)
        listOf("armeabi-v7a", "arm64-v8a", "x86_64").forEach { abi ->
            root.getJSONArray("assets").put(org.json.JSONObject(universalAsset.toString()).apply {
                val name = "xome-1.1.0-$abi-release.apk"
                put("name", name)
                put("browser_download_url", "https://github.com/t59688/xome/releases/download/v1.1.0/$name")
            })
        }
        org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build::class.java,
            "SUPPORTED_ABIS", arrayOf("arm64-v8a", "armeabi-v7a"))
        val release = GitHubRelease.parse(root.toString())!!
        assertEquals("xome-1.1.0-arm64-v8a-release.apk", release.assetName)
        assertEquals(release, GitHubRelease.parse(release.toJson()))
    }

    @Test fun `incompatible architecture is never offered without a universal fallback`() {
        org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build::class.java,
            "SUPPORTED_ABIS", arrayOf("arm64-v8a", "armeabi-v7a"))
        val incompatible = json().replace("xome-1.1.0-release.apk", "xome-1.1.0-x86_64-release.apk")
        assertNull(GitHubRelease.parse(incompatible))
        assertEquals("xome-1.1.0-release.apk", GitHubRelease.parse(json())!!.assetName)
    }

    @Test fun `architecture APK still requires the exact repository URL and a valid digest`() {
        org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build::class.java,
            "SUPPORTED_ABIS", arrayOf("arm64-v8a"))
        val matching = json().replace("xome-1.1.0-release.apk", "xome-1.1.0-arm64-v8a-release.apk")
        assertNotNull(GitHubRelease.parse(matching))
        assertNull(GitHubRelease.parse(matching.replace("t59688/xome/releases/download", "other/repo/releases/download")))
        assertNull(GitHubRelease.parse(matching.replace("\"digest\":null", "\"digest\":\"sha256:bad\"")))
    }
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
