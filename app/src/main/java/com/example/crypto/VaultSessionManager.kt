package com.example.crypto

import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import com.example.data.VaultDatabase
import java.io.File
import java.util.Arrays
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory security session manager for the currently unlocked vault space.
 */
object VaultSessionManager {

    class ActiveVaultSession(
        val spaceId: String,
        val containerFile: File,
        val containerInfo: VaultCryptoEngine.SpaceContainerInfo,
        val kdfProfile: VaultCryptoEngine.Argon2Profile,
        private val rawMasterKey: ByteArray,
        val database: VaultDatabase
    ) {
        @Volatile
        var isDestroyed: Boolean = false
            private set

        fun borrowMasterKeyCopy(): ByteArray {
            check(!isDestroyed) { "Vault session has already been locked and wiped" }
            return rawMasterKey.copyOf()
        }

        internal fun destroy() {
            if (!isDestroyed) {
                isDestroyed = true
                Arrays.fill(rawMasterKey, 0.toByte())
                try {
                    database.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private val _activeSession = MutableStateFlow<ActiveVaultSession?>(null)
    val activeSession: StateFlow<ActiveVaultSession?> = _activeSession.asStateFlow()

    private val _lockCount = MutableStateFlow(0)
    val lockCount: StateFlow<Int> = _lockCount.asStateFlow()

    private val thumbnailCache = LruCache<String, ImageBitmap>(60)

    fun activateSession(session: ActiveVaultSession) {
        _activeSession.value?.destroy()
        thumbnailCache.evictAll()
        _activeSession.value = session
    }

    fun getCachedThumbnail(cacheKey: String): ImageBitmap? {
        return synchronized(thumbnailCache) {
            thumbnailCache.get(cacheKey)
        }
    }

    fun putCachedThumbnail(cacheKey: String, bitmap: ImageBitmap) {
        synchronized(thumbnailCache) {
            thumbnailCache.put(cacheKey, bitmap)
        }
    }

    fun lock() {
        val current = _activeSession.value
        if (current != null) {
            current.destroy()
            _activeSession.value = null
            synchronized(thumbnailCache) {
                thumbnailCache.evictAll()
            }
            _lockCount.value += 1
        }
    }
}
