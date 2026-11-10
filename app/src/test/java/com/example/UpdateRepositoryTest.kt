package com.example

import android.app.DownloadManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.update.GitHubRelease
import com.example.update.ReleaseVersion
import com.example.update.UpdatePhase
import com.example.update.UpdateRepository
import com.example.update.UpdateChannel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateRepositoryTest {
    private lateinit var context: Context
    private val release = GitHubRelease(ReleaseVersion(1, 1, 0), "1.1.0", "说明", "xome-1.1.0-release.apk",
        "https://github.com/t59688/xome/releases/download/v1.1.0/xome-1.1.0-release.apk", 100)

    @Before fun resetPreferences() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(UpdateRepository.PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `repository construction is passive until the app lifecycle or user checks`() = runBlocking {
        var calls = 0
        val repository = UpdateRepository(context) { _ -> calls++; release }
        assertEquals(0, calls)
        assertEquals(UpdatePhase.IDLE, repository.state.value.phase)
        repository.check(force = true)
        assertEquals(1, calls)
    }

    @Test fun `failed manual check can retry after fifteen minutes despite recent success`() = runBlocking {
        var requests = 0
        val repository = UpdateRepository(context) {
            requests++
            if (requests == 2) throw java.io.IOException("Offline")
            release
        }
        repository.check()
        repository.check(force = true)
        assertEquals(UpdatePhase.FAILED, repository.state.value.phase)
        context.getSharedPreferences(UpdateRepository.PREFERENCES, Context.MODE_PRIVATE).edit()
            .putLong("attempted_at", System.currentTimeMillis() - 16 * 60_000L).commit()
        val restored = UpdateRepository(context) { requests++; release }
        restored.check()
        assertEquals(3, requests)
    }

    @Test fun `switching channel allows its first automatic check immediately`() = runBlocking {
        var requests = 0
        val repository = UpdateRepository(context) { requests++; release }
        repository.check()
        repository.selectChannel(UpdateChannel.BETA)
        repository.check()
        assertEquals(2, requests)
    }

    @Test fun `channel switching clears cached release without network access`() = runBlocking {
        var calls = 0
        val repository = UpdateRepository(context) { _ -> calls++; release }
        repository.check(force = true)
        assertTrue(repository.state.value.hasUpdate)
        repository.selectChannel(UpdateChannel.BETA)
        assertEquals(UpdateChannel.BETA, repository.channel.value)
        assertEquals(UpdatePhase.IDLE, repository.state.value.phase)
        assertFalse(repository.state.value.hasUpdate)
        assertEquals(1, calls)
        val restored = UpdateRepository(context) { _ -> error("Unexpected request") }
        assertEquals(UpdateChannel.BETA, restored.channel.value)
    }

    @Test fun `automatic checks are throttled but manual checks bypass the interval`() = runBlocking {
        var requests = 0
        val repository = UpdateRepository(context) { _ -> requests++; release }
        repository.check()
        repository.check()
        assertEquals(1, requests)
        assertEquals(UpdatePhase.AVAILABLE, repository.state.value.phase)
        assertTrue(repository.state.value.hasUpdate)
        repository.check(force = true)
        assertEquals(2, requests)
    }

    @Test fun `network failures retain cached update and prevent tight retry loops`() = runBlocking {
        UpdateRepository(context) { _ -> release }.check()
        val repository = UpdateRepository(context) { _ -> throw java.io.IOException("网络不可用") }
        repository.check(force = true)
        assertEquals(UpdatePhase.FAILED, repository.state.value.phase)
        assertTrue(repository.state.value.hasUpdate)
        assertEquals("网络不可用", repository.state.value.message)
    }

    @Test fun `no public release does not report that the app is up to date`() = runBlocking {
        val repository = UpdateRepository(context) { _ -> null }
        repository.check()
        assertEquals(UpdatePhase.NO_RELEASE, repository.state.value.phase)
        assertFalse(repository.state.value.hasUpdate)
    }

    @Test fun `background cancellation does not leave check button permanently disabled`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val repository = UpdateRepository(context) { _ -> entered.complete(Unit); awaitCancellation() }
        val job = launch { repository.check(force = true) }
        entered.await()
        assertEquals(UpdatePhase.CHECKING, repository.state.value.phase)
        job.cancelAndJoin()
        assertEquals(UpdatePhase.IDLE, repository.state.value.phase)
    }

    @Test fun `download delegates progress notifications to system and restores progress`() = runBlocking {
        val repository = UpdateRepository(context) { _ -> release }
        repository.check()
        repository.download()
        repository.download()
        val manager = shadowOf(context.getSystemService(DownloadManager::class.java))
        assertEquals(1, manager.requestCount)
        val request = shadowOf(manager.getRequest(repository.state.value.downloadId))
        assertEquals(DownloadManager.Request.VISIBILITY_VISIBLE, request.notificationVisibility)
        assertEquals(UpdateRepository.APK_MIME, request.mimeType)
        request.setStatus(DownloadManager.STATUS_RUNNING)
        request.setBytesSoFar(40)
        request.setTotalSize(100)
        val restored = UpdateRepository(context) { _ -> error("恢复下载不应联网") }
        restored.refresh()
        assertEquals(UpdatePhase.DOWNLOADING, restored.state.value.phase)
        assertEquals(40L, restored.state.value.downloadedBytes)
        assertEquals(repository.state.value.downloadId, restored.state.value.downloadId)
        assertFalse(restored.state.value.autoInstall) // Never auto-launch installer for a disguised app
        restored.cancelDownload()
        assertEquals(0, manager.requestCount)
        assertEquals(UpdatePhase.AVAILABLE, restored.state.value.phase)
    }

    @Test fun `cancelled system download can be retried after restart`() = runBlocking {
        val repository = UpdateRepository(context) { _ -> release }
        repository.check()
        repository.download()
        context.getSystemService(DownloadManager::class.java).remove(repository.state.value.downloadId)
        val restored = UpdateRepository(context)
        restored.refresh()
        assertEquals(UpdatePhase.FAILED, restored.state.value.phase)
        restored.download()
        assertEquals(UpdatePhase.DOWNLOADING, restored.state.value.phase)
    }

    @Test fun `remind later persists without clearing update badge`() = runBlocking {
        val repository = UpdateRepository(context) { _ -> release }
        repository.check()
        assertTrue(repository.shouldPrompt())
        repository.dismissPrompt()
        val restored = UpdateRepository(context)
        assertFalse(restored.shouldPrompt())
        assertTrue(restored.state.value.hasUpdate)
    }
}
