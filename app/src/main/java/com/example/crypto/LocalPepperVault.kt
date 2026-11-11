package com.example.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.ProviderException
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hardware-backed local cache for per-space Keyfile peppers.
 *
 * Threat model:
 * - External storage (`/sdcard/Documents/.sys_vault_data/`) contains ONLY stealth containers,
 *   encrypted DBs, and encrypted blobs. It NEVER stores the Keyfile, any Keyfile fingerprint,
 *   or any sealed pepper.
 * - On the user's currently installed device, this vault seals `(profileId, filePepper)` inside
 *   `context.filesDir/.vault_seals/<spaceId>.seal` encrypted under a non-exportable
 *   AndroidKeyStore AES-256-GCM key (StrongBox preferred, TEE fallback).
 * - Thus daily calculator unlocks only require entering the calculator sequence, while stealing
 *   external files or backups still leaves an attacker missing the full 256-bit Keyfile entropy.
 * - After uninstall/reinstall or device migration, `.vault_seals/` is empty; the user recovers by
 *   re-selecting the original Keyfile + sequence, which automatically re-seals locally.
 */
object LocalPepperVault {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "xome_local_pepper_seal_v2"
    private const val SEALS_DIR_NAME = ".vault_seals"
    private const val SEAL_VERSION: Byte = 2
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val GCM_TAG_BYTES = 16
    private const val PAYLOAD_BYTES = 1 + VaultCryptoEngine.KEYFILE_PEPPER_BYTES // 1B profileId + 32B pepper
    private const val SEAL_FILE_BYTES = 1 + GCM_IV_BYTES + PAYLOAD_BYTES + GCM_TAG_BYTES // 62 bytes
    private val VALID_SPACE_ID = Regex("^[0-9a-fA-F]{4,32}$")

    data class SealedPepper(
        val profile: VaultCryptoEngine.Argon2Profile,
        val filePepper: ByteArray
    ) {
        fun wipe() {
            Arrays.fill(filePepper, 0.toByte())
        }
    }

    interface SealCryptoBackend {
        fun encrypt(plaintext: ByteArray, aad: ByteArray): Pair<ByteArray, ByteArray>
        fun decrypt(iv: ByteArray, ciphertextWithTag: ByteArray, aad: ByteArray): ByteArray
    }

    @Volatile
    private var overrideBackendForTest: SealCryptoBackend? = null

    private object AndroidKeystoreBackend : SealCryptoBackend {
        private val keyLock = Any()

        private fun getOrCreateKey(): SecretKey {
            synchronized(keyLock) {
                val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
                val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
                if (existing != null) return existing

                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        generator.init(buildKeySpec(useStrongBox = true))
                        return generator.generateKey()
                    } catch (_: StrongBoxUnavailableException) {
                        // Fall through to standard hardware TEE AndroidKeyStore.
                    } catch (_: ProviderException) {
                        // Some devices throw ProviderException instead of StrongBoxUnavailableException.
                    }
                }
                generator.init(buildKeySpec(useStrongBox = false))
                return generator.generateKey()
            }
        }

        private fun buildKeySpec(useStrongBox: Boolean): KeyGenParameterSpec {
            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
            if (useStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(true)
            }
            return builder.build()
        }

        override fun encrypt(plaintext: ByteArray, aad: ByteArray): Pair<ByteArray, ByteArray> {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(aad)
            val ciphertext = cipher.doFinal(plaintext)
            return cipher.iv to ciphertext
        }

        override fun decrypt(iv: ByteArray, ciphertextWithTag: ByteArray, aad: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad)
            return cipher.doFinal(ciphertextWithTag)
        }
    }

    /** Ephemeral JVM-only seal backend used strictly under Robolectric unit tests. */
    private object RobolectricEphemeralBackend : SealCryptoBackend {
        private val random = SecureRandom()
        private val keyBytes = ByteArray(32).also { random.nextBytes(it) }
        private val key = SecretKeySpec(keyBytes, "AES")

        override fun encrypt(plaintext: ByteArray, aad: ByteArray): Pair<ByteArray, ByteArray> {
            val iv = ByteArray(GCM_IV_BYTES).also { random.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad)
            return iv to cipher.doFinal(plaintext)
        }

        override fun decrypt(iv: ByteArray, ciphertextWithTag: ByteArray, aad: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad)
            return cipher.doFinal(ciphertextWithTag)
        }
    }

    private fun isRobolectricRuntime(): Boolean =
        "robolectric".equals(Build.FINGERPRINT, ignoreCase = true)

    private fun activeBackend(): SealCryptoBackend {
        overrideBackendForTest?.let { return it }
        if (isRobolectricRuntime()) return RobolectricEphemeralBackend
        return AndroidKeystoreBackend
    }

    internal fun setTestBackend(backend: SealCryptoBackend?) {
        overrideBackendForTest = backend
    }

    private fun sealsDir(context: Context): File =
        File(context.filesDir, SEALS_DIR_NAME).apply { mkdirs() }

    private fun sealFile(context: Context, spaceId: String): File {
        require(VALID_SPACE_ID.matches(spaceId)) { "Invalid spaceId for seal" }
        return File(sealsDir(context), "$spaceId.seal")
    }

    private fun buildAad(spaceId: String): ByteArray =
        "XOME_LOCAL_SEAL_V2:$spaceId".toByteArray(Charsets.UTF_8)

    fun saveSeal(
        context: Context,
        spaceId: String,
        profile: VaultCryptoEngine.Argon2Profile,
        filePepper: ByteArray
    ) {
        require(filePepper.size == VaultCryptoEngine.KEYFILE_PEPPER_BYTES) {
            "Keyfile pepper must be ${VaultCryptoEngine.KEYFILE_PEPPER_BYTES} bytes"
        }
        val payload = ByteArray(PAYLOAD_BYTES)
        payload[0] = profile.id
        System.arraycopy(filePepper, 0, payload, 1, filePepper.size)

        try {
            val (iv, encrypted) = activeBackend().encrypt(payload, buildAad(spaceId))
            require(iv.size == GCM_IV_BYTES)
            require(encrypted.size == PAYLOAD_BYTES + GCM_TAG_BYTES)

            val output = ByteBuffer.allocate(SEAL_FILE_BYTES)
                .put(SEAL_VERSION)
                .put(iv)
                .put(encrypted)
                .array()

            val target = sealFile(context, spaceId)
            val tmp = File.createTempFile(".seal-", ".tmp", target.parentFile)
            try {
                FileOutputStream(tmp).use { fos ->
                    fos.write(output)
                    fos.flush()
                    fos.fd.sync()
                }
                Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } finally {
                tmp.delete()
                Arrays.fill(output, 0.toByte())
            }
        } finally {
            Arrays.fill(payload, 0.toByte())
        }
    }

    fun loadSeal(context: Context, spaceId: String): SealedPepper? {
        if (!VALID_SPACE_ID.matches(spaceId)) return null
        val file = sealFile(context, spaceId)
        if (!file.isFile || file.length() != SEAL_FILE_BYTES.toLong()) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        if (bytes.size != SEAL_FILE_BYTES || bytes[0] != SEAL_VERSION) {
            Arrays.fill(bytes, 0.toByte())
            return null
        }

        val iv = bytes.copyOfRange(1, 1 + GCM_IV_BYTES)
        val ciphertext = bytes.copyOfRange(1 + GCM_IV_BYTES, bytes.size)
        var plaintext: ByteArray? = null
        return try {
            plaintext = activeBackend().decrypt(iv, ciphertext, buildAad(spaceId))
            if (plaintext.size != PAYLOAD_BYTES) return null
            val profile = VaultCryptoEngine.Argon2Profile.fromId(plaintext[0]) ?: return null
            val pepper = plaintext.copyOfRange(1, PAYLOAD_BYTES)
            SealedPepper(profile = profile, filePepper = pepper)
        } catch (_: Exception) {
            null
        } finally {
            Arrays.fill(bytes, 0.toByte())
            Arrays.fill(iv, 0.toByte())
            Arrays.fill(ciphertext, 0.toByte())
            plaintext?.let { Arrays.fill(it, 0.toByte()) }
        }
    }

    fun hasSeal(context: Context, spaceId: String): Boolean {
        if (!VALID_SPACE_ID.matches(spaceId)) return false
        val file = sealFile(context, spaceId)
        return file.isFile && file.length() == SEAL_FILE_BYTES.toLong()
    }

    fun listSealedSpaceIds(context: Context): Set<String> {
        val dir = File(context.filesDir, SEALS_DIR_NAME)
        if (!dir.isDirectory) return emptySet()
        return dir.listFiles { f ->
            f.isFile && f.extension == "seal" && f.length() == SEAL_FILE_BYTES.toLong()
        }?.map { it.nameWithoutExtension }?.toSet() ?: emptySet()
    }

    fun deleteSeal(context: Context, spaceId: String) {
        if (!VALID_SPACE_ID.matches(spaceId)) return
        runCatching { sealFile(context, spaceId).delete() }
    }
}
