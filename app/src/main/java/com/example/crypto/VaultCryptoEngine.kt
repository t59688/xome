package com.example.crypto

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * Core V2 cryptographic engine implementing:
 * - Zero-fingerprint 256-bit Keyfile digest (`computeKeyfilePepper`)
 * - Dual-secret Key Derivation: Argon2id (RFC 9106 with secret pepper) + HMAC-SHA256 KEK mixing
 * - Headerless, magic-free 8,208-byte 64-slot Chaffed Stealth Space Containers (.dat)
 * - Magic-free 80-byte blob headers + 64KB chunked streaming AES-256-GCM (.dat)
 * - Zero re-encryption sequence rotation and Keyfile rotation
 */
object VaultCryptoEngine {

    const val KEYFILE_PEPPER_BYTES = 32
    const val CONTAINER_NONCE_BYTES = 16
    const val SLOT_COUNT = 64
    const val SLOT_SIZE_BYTES = 128
    const val CONTAINER_SIZE_BYTES = CONTAINER_NONCE_BYTES + SLOT_COUNT * SLOT_SIZE_BYTES // 8,208 bytes

    // Magic-free blob header: 12B DEK_Nonce + 48B Wrapped_DEK + 12B Base_Chunk_Nonce + 8B Padding = 80B
    const val FILE_HEADER_SIZE_BYTES = 80
    const val DEFAULT_CHUNK_SIZE = 65536
    const val GCM_TAG_BITS = 128
    const val GCM_TAG_BYTES = 16
    const val ENCRYPTED_CHUNK_SIZE = DEFAULT_CHUNK_SIZE + GCM_TAG_BYTES

    private val KEYFILE_DOMAIN = "XOME_KEYFILE_PEPPER_V2".toByteArray(Charsets.UTF_8)
    private val SLOT_DOMAIN = "XOME_SLOT_V2".toByteArray(Charsets.UTF_8)
    private val KEK_DOMAIN = "XOME_KEK_V2".toByteArray(Charsets.UTF_8)

    private val secureRandom = SecureRandom()

    /** Fixed KDF cost tiers; never written in plaintext to external containers. */
    enum class Argon2Profile(
        val id: Byte,
        val memoryCostKb: Int,
        val timeCost: Int,
        val parallelism: Int
    ) {
        HIGH(id = 1, memoryCostKb = 196608, timeCost = 4, parallelism = 4),
        STANDARD(id = 2, memoryCostKb = 131072, timeCost = 4, parallelism = 4),
        COMPACT(id = 3, memoryCostKb = 65536, timeCost = 6, parallelism = 4);

        fun toParams(): Argon2Params = Argon2Params(memoryCostKb, timeCost, parallelism)

        companion object {
            fun fromId(id: Byte): Argon2Profile? = entries.firstOrNull { it.id == id }

            fun selectForDevice(): Argon2Profile {
                val maxHeapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L)
                return when {
                    maxHeapMb >= 768L -> HIGH
                    maxHeapMb >= 384L -> STANDARD
                    else -> COMPACT
                }
            }

            fun recoveryOrder(preferred: Argon2Profile = selectForDevice()): List<Argon2Profile> =
                (listOf(preferred) + entries).distinct()
        }
    }

    data class Argon2Params(
        val memoryCostKb: Int,
        val timeCost: Int,
        val parallelism: Int
    )

    data class KeyfileDigest(
        val pepper: ByteArray,
        val byteCount: Long
    ) {
        fun wipe() {
            Arrays.fill(pepper, 0.toByte())
        }
    }

    data class SpaceContainerInfo(
        val spaceId: String,
        val containerFile: File,
        val containerNonce: ByteArray
    )

    fun randomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    /**
     * Streams an arbitrary file to compute a domain-separated 256-bit Keyfile pepper without
     * buffering the entire file in memory or persisting any filename/size/hash fingerprint.
     */
    fun computeKeyfilePepper(inputStream: InputStream): KeyfileDigest {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(KEYFILE_DOMAIN)
        val buffer = ByteArray(DEFAULT_CHUNK_SIZE)
        var totalBytes = 0L
        try {
            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) break
                if (read > 0) {
                    digest.update(buffer, 0, read)
                    totalBytes += read
                }
            }
            require(totalBytes > 0L) { "Keyfile cannot be empty" }
            val lenBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(totalBytes).array()
            digest.update(lenBytes)
            return KeyfileDigest(pepper = digest.digest(), byteCount = totalBytes)
        } finally {
            Arrays.fill(buffer, 0.toByte())
        }
    }

    /**
     * Derives the active slot index in [0, SLOT_COUNT) from the Keyfile pepper and container nonce.
     * Without the Keyfile pepper, all 64 uniform random slots appear indistinguishable.
     */
    fun computeSlotIndex(filePepper: ByteArray, containerNonce: ByteArray): Int {
        require(filePepper.size == KEYFILE_PEPPER_BYTES) { "Keyfile pepper must be 32 bytes" }
        require(containerNonce.size == CONTAINER_NONCE_BYTES) { "Container nonce must be 16 bytes" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(filePepper, "HmacSHA256"))
        mac.update(SLOT_DOMAIN)
        mac.update(containerNonce)
        val tag = mac.doFinal()
        val index = tag[0].toInt() and 0x3F
        Arrays.fill(tag, 0.toByte())
        return index
    }

    private fun slotAad(containerNonce: ByteArray, slotIndex: Int): ByteArray {
        val aad = ByteArray(CONTAINER_NONCE_BYTES + 1)
        System.arraycopy(containerNonce, 0, aad, 0, CONTAINER_NONCE_BYTES)
        aad[CONTAINER_NONCE_BYTES] = slotIndex.toByte()
        return aad
    }

    /**
     * Dual-secret key derivation combining user sequence + 256-bit Keyfile pepper via Argon2id
     * (RFC 9106 secret parameter) followed by domain-separated HMAC-SHA256 extraction.
     */
    fun deriveKek(
        pinChars: CharArray,
        filePepper: ByteArray,
        salt: ByteArray,
        params: Argon2Params
    ): ByteArray {
        require(salt.size == 16) { "Argon2id salt must be 16 bytes" }
        require(filePepper.size == KEYFILE_PEPPER_BYTES) { "Keyfile pepper must be 32 bytes" }
        require(params.memoryCostKb in 4096..262144) { "Invalid Argon2 memory cost" }
        require(params.timeCost in 1..32) { "Invalid Argon2 iteration count" }
        require(params.parallelism in 1..16) { "Invalid Argon2 parallelism" }

        val passwordBytes = charsToUtf8Bytes(pinChars)
        val argonOut = ByteArray(32)
        try {
            val argonParams = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withSecret(filePepper)
                .withMemoryAsKB(params.memoryCostKb)
                .withIterations(params.timeCost)
                .withParallelism(params.parallelism)
                .build()

            val generator = Argon2BytesGenerator()
            generator.init(argonParams)
            generator.generateBytes(passwordBytes, argonOut, 0, argonOut.size)

            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(argonOut, "HmacSHA256"))
            mac.update(KEK_DOMAIN)
            mac.update(filePepper)
            return mac.doFinal()
        } finally {
            Arrays.fill(passwordBytes, 0.toByte())
            Arrays.fill(argonOut, 0.toByte())
        }
    }

    /**
     * Creates a magic-free 8,208-byte stealth container with 63 CSPRNG decoy slots and
     * 1 real slot wrapping [masterKey] under the dual-secret KEK.
     */
    fun createStealthContainerBytes(
        pinChars: CharArray,
        filePepper: ByteArray,
        masterKey: ByteArray,
        params: Argon2Params = Argon2Profile.selectForDevice().toParams()
    ): ByteArray {
        require(masterKey.size == 32) { "MasterKey must be 32 bytes (256-bit)" }
        require(filePepper.size == KEYFILE_PEPPER_BYTES) { "Keyfile pepper must be 32 bytes" }

        val container = randomBytes(CONTAINER_SIZE_BYTES)
        val containerNonce = container.copyOfRange(0, CONTAINER_NONCE_BYTES)
        val slotIndex = computeSlotIndex(filePepper, containerNonce)

        val salt = randomBytes(16)
        val mkNonce = randomBytes(12)
        val slotPadding = randomBytes(52)

        val kek = deriveKek(pinChars, filePepper, salt, params)
        try {
            val wrappedMk = aesGcmEncrypt(
                key = kek,
                iv = mkNonce,
                plaintext = masterKey,
                aad = slotAad(containerNonce, slotIndex)
            )
            require(wrappedMk.size == 48) { "Wrapped MK must be 48 bytes" }

            val slotOffset = CONTAINER_NONCE_BYTES + slotIndex * SLOT_SIZE_BYTES
            System.arraycopy(salt, 0, container, slotOffset, 16)
            System.arraycopy(mkNonce, 0, container, slotOffset + 16, 12)
            System.arraycopy(wrappedMk, 0, container, slotOffset + 28, 48)
            System.arraycopy(slotPadding, 0, container, slotOffset + 76, 52)
            return container
        } finally {
            Arrays.fill(kek, 0.toByte())
        }
    }

    fun parseStealthContainer(containerFile: File): SpaceContainerInfo? {
        if (!containerFile.isFile || containerFile.length() != CONTAINER_SIZE_BYTES.toLong()) {
            return null
        }
        val nonce = ByteArray(CONTAINER_NONCE_BYTES)
        return try {
            RandomAccessFile(containerFile, "r").use { raf ->
                raf.seek(0L)
                raf.readFully(nonce)
            }
            SpaceContainerInfo(
                spaceId = containerFile.nameWithoutExtension,
                containerFile = containerFile,
                containerNonce = nonce
            )
        } catch (_: Exception) {
            null
        }
    }

    fun tryUnlockStealthContainer(
        containerInfo: SpaceContainerInfo,
        pinChars: CharArray,
        filePepper: ByteArray,
        params: Argon2Params
    ): ByteArray? {
        if (filePepper.size != KEYFILE_PEPPER_BYTES) return null
        if (!containerInfo.containerFile.isFile ||
            containerInfo.containerFile.length() != CONTAINER_SIZE_BYTES.toLong()
        ) {
            return null
        }

        val slotIndex = computeSlotIndex(filePepper, containerInfo.containerNonce)
        val slotBytes = ByteArray(SLOT_SIZE_BYTES)
        try {
            RandomAccessFile(containerInfo.containerFile, "r").use { raf ->
                raf.seek((CONTAINER_NONCE_BYTES + slotIndex * SLOT_SIZE_BYTES).toLong())
                raf.readFully(slotBytes)
            }
        } catch (_: Exception) {
            return null
        }

        val salt = slotBytes.copyOfRange(0, 16)
        val mkNonce = slotBytes.copyOfRange(16, 28)
        val wrappedMk = slotBytes.copyOfRange(28, 76)
        val kek = deriveKek(pinChars, filePepper, salt, params)
        return try {
            aesGcmDecrypt(
                key = kek,
                iv = mkNonce,
                ciphertextWithTag = wrappedMk,
                aad = slotAad(containerInfo.containerNonce, slotIndex)
            )
        } catch (_: AEADBadTagException) {
            null
        } catch (_: Exception) {
            null
        } finally {
            Arrays.fill(kek, 0.toByte())
            Arrays.fill(slotBytes, 0.toByte())
        }
    }

    /**
     * Cross-device / fresh-install recovery unlock: computes the slot from [filePepper] once
     * and probes the fixed [Argon2Profile] tiers (since no KDF tier is exposed in plaintext).
     */
    fun tryRecoverStealthContainer(
        containerInfo: SpaceContainerInfo,
        pinChars: CharArray,
        filePepper: ByteArray,
        profiles: List<Argon2Profile> = Argon2Profile.recoveryOrder()
    ): Pair<ByteArray, Argon2Profile>? {
        for (profile in profiles) {
            val mk = tryUnlockStealthContainer(containerInfo, pinChars, filePepper, profile.toParams())
            if (mk != null) return mk to profile
        }
        return null
    }

    /**
     * Atomically replaces the stealth container with a fresh containerNonce, fresh slotIndex,
     * fresh 63 chaff slots, and re-wrapped MasterKey without touching encrypted DB or blobs.
     */
    fun rotateStealthContainerCredentials(
        containerFile: File,
        oldPinChars: CharArray,
        oldPepper: ByteArray,
        oldParams: Argon2Params,
        newPinChars: CharArray,
        newPepper: ByteArray,
        newParams: Argon2Params
    ): Boolean {
        if (!VaultSequencePolicy.isValidNewSequence(java.nio.CharBuffer.wrap(newPinChars))) return false
        val info = parseStealthContainer(containerFile) ?: return false
        val mk = tryUnlockStealthContainer(info, oldPinChars, oldPepper, oldParams) ?: return false
        return try {
            val updated = createStealthContainerBytes(
                pinChars = newPinChars,
                filePepper = newPepper,
                masterKey = mk,
                params = newParams
            )
            writeAtomically(containerFile, updated)
            true
        } finally {
            Arrays.fill(mk, 0.toByte())
        }
    }

    fun writeAtomically(targetFile: File, bytes: ByteArray) {
        val parent = targetFile.absoluteFile.parentFile
        val pending = File.createTempFile(".cnt-", ".tmp", parent)
        try {
            FileOutputStream(pending).use { fos ->
                fos.write(bytes)
                fos.flush()
                fos.fd.sync()
            }
            Files.move(
                pending.toPath(),
                targetFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            pending.delete()
        }
    }

    fun deriveDatabaseKeyFromMasterKey(masterKey: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(masterKey, "HmacSHA256"))
        return mac.doFinal("XOME_SQLCIPHER_DB_KEY_V1".toByteArray(Charsets.UTF_8))
    }

    fun deriveThumbnailKeyFromMasterKey(masterKey: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(masterKey, "HmacSHA256"))
        return mac.doFinal("XOME_THUMBNAIL_AES_KEY_V1".toByteArray(Charsets.UTF_8))
    }

    fun encryptThumbnail(masterKey: ByteArray, plainThumbnail: ByteArray): ByteArray {
        val thumbKey = deriveThumbnailKeyFromMasterKey(masterKey)
        try {
            val iv = randomBytes(12)
            val encrypted = aesGcmEncrypt(key = thumbKey, iv = iv, plaintext = plainThumbnail)
            val result = ByteArray(12 + encrypted.size)
            System.arraycopy(iv, 0, result, 0, 12)
            System.arraycopy(encrypted, 0, result, 12, encrypted.size)
            return result
        } finally {
            Arrays.fill(thumbKey, 0.toByte())
        }
    }

    fun decryptThumbnail(masterKey: ByteArray, encryptedBlob: ByteArray): ByteArray? {
        if (encryptedBlob.size <= 12 + GCM_TAG_BYTES) return null
        val thumbKey = deriveThumbnailKeyFromMasterKey(masterKey)
        return try {
            val iv = encryptedBlob.copyOfRange(0, 12)
            val cipherWithTag = encryptedBlob.copyOfRange(12, encryptedBlob.size)
            aesGcmDecrypt(key = thumbKey, iv = iv, ciphertextWithTag = cipherWithTag)
        } catch (_: Exception) {
            null
        } finally {
            Arrays.fill(thumbKey, 0.toByte())
        }
    }

    /**
     * Streaming 64KB chunked encryption into a magic-free .dat blob file.
     *
     * File Header (80 Bytes, statistically uniform):
     * - 0x00 ~ 0x0B (12B): DEK_Nonce
     * - 0x0C ~ 0x3B (48B): Wrapped_DEK = AES-256-GCM(Key=MK, IV=DEK_Nonce, Plaintext=DEK, AAD=Base_Chunk_Nonce)
     * - 0x3C ~ 0x47 (12B): Base_Chunk_Nonce
     * - 0x48 ~ 0x4F (8B):  CSPRNG Padding
     */
    fun encryptStreamToBlobFile(
        masterKey: ByteArray,
        inputStream: InputStream,
        targetBlobFile: File
    ): Long {
        require(masterKey.size == 32) { "MasterKey must be 32 bytes" }
        val dek = randomBytes(32)
        val dekNonce = randomBytes(12)
        val baseChunkNonce = randomBytes(12)
        val headerPad = randomBytes(8)

        var totalPlainBytes = 0L
        try {
            val wrappedDek = aesGcmEncrypt(
                key = masterKey,
                iv = dekNonce,
                plaintext = dek,
                aad = baseChunkNonce
            )
            require(wrappedDek.size == 48)

            val headerBuffer = ByteBuffer.allocate(FILE_HEADER_SIZE_BYTES)
            headerBuffer.put(dekNonce)
            headerBuffer.put(wrappedDek)
            headerBuffer.put(baseChunkNonce)
            headerBuffer.put(headerPad)

            FileOutputStream(targetBlobFile).use { fos ->
                fos.write(headerBuffer.array())

                val plainBuffer = ByteArray(DEFAULT_CHUNK_SIZE)
                var chunkIndex = 0

                while (true) {
                    val bytesRead = readChunkFully(inputStream, plainBuffer)
                    if (bytesRead <= 0) break

                    totalPlainBytes += bytesRead
                    val chunkPlain = if (bytesRead == plainBuffer.size) {
                        plainBuffer
                    } else {
                        plainBuffer.copyOfRange(0, bytesRead)
                    }

                    val chunkNonce = computeChunkNonce(baseChunkNonce, chunkIndex)
                    val aad = chunkIndexToAad(chunkIndex)
                    val encryptedChunk = aesGcmEncrypt(
                        key = dek,
                        iv = chunkNonce,
                        plaintext = chunkPlain,
                        aad = aad
                    )
                    fos.write(encryptedChunk)
                    chunkIndex++

                    if (bytesRead < plainBuffer.size) break
                }

                if (chunkIndex == 0) {
                    val chunkNonce = computeChunkNonce(baseChunkNonce, 0)
                    val aad = chunkIndexToAad(0)
                    val encryptedChunk = aesGcmEncrypt(
                        key = dek,
                        iv = chunkNonce,
                        plaintext = ByteArray(0),
                        aad = aad
                    )
                    fos.write(encryptedChunk)
                }

                fos.flush()
                fos.fd.sync()
            }
            return totalPlainBytes
        } finally {
            Arrays.fill(dek, 0.toByte())
        }
    }

    fun decryptBlobFileToStream(
        masterKey: ByteArray,
        blobFile: File,
        outputStream: OutputStream
    ) {
        RandomAccessDecryptedStream(blobFile = blobFile, masterKey = masterKey).use { decStream ->
            val buffer = ByteArray(DEFAULT_CHUNK_SIZE)
            while (true) {
                val read = decStream.read(buffer)
                if (read <= 0) break
                outputStream.write(buffer, 0, read)
            }
            outputStream.flush()
        }
    }

    fun computeChunkNonce(baseNonce: ByteArray, chunkIndex: Int): ByteArray {
        require(baseNonce.size == 12)
        val result = baseNonce.copyOf()
        val indexBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(chunkIndex.toLong()).array()
        for (k in 0 until 8) {
            result[4 + k] = (result[4 + k].toInt() xor indexBytes[k].toInt()).toByte()
        }
        return result
    }

    fun chunkIndexToAad(chunkIndex: Int): ByteArray {
        return ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(chunkIndex.toLong()).array()
    }

    fun aesGcmEncrypt(
        key: ByteArray,
        iv: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray? = null
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), spec)
        if (aad != null) {
            cipher.updateAAD(aad)
        }
        return cipher.doFinal(plaintext)
    }

    fun aesGcmDecrypt(
        key: ByteArray,
        iv: ByteArray,
        ciphertextWithTag: ByteArray,
        aad: ByteArray? = null
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)
        if (aad != null) {
            cipher.updateAAD(aad)
        }
        return cipher.doFinal(ciphertextWithTag)
    }

    private fun readChunkFully(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read == -1) break
            total += read
        }
        return total
    }

    private fun charsToUtf8Bytes(chars: CharArray): ByteArray {
        val charBuffer = java.nio.CharBuffer.wrap(chars)
        val byteBuffer = Charsets.UTF_8.encode(charBuffer)
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        if (byteBuffer.hasArray()) {
            Arrays.fill(byteBuffer.array(), 0.toByte())
        }
        return bytes
    }
}
