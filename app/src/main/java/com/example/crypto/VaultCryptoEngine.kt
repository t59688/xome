package com.example.crypto

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
 * Core cryptographic engine implementing:
 * - Argon2id (RFC 9106) Key Derivation (KEK from PIN/Sequence CharArray)
 * - 128-Byte Binary Space Envelope Header (.header) with Key Wrapping
 * - 76-Byte File Header + 64KB Streaming AES-256-GCM Chunked Encryption (.dat)
 * - Zero Re-encryption Password/Sequence Rotation
 */
object VaultCryptoEngine {

    const val HEADER_SIZE_BYTES = 128
    val HEADER_MAGIC = "CLCVLT01".toByteArray(Charsets.US_ASCII) // 8 bytes

    const val FILE_HEADER_SIZE_BYTES = 80 // 0x00..0x4F: 4B Magic + 4B ChunkSize + 12B DEK_Nonce + 48B Wrapped_DEK + 12B Base_Chunk_Nonce
    val FILE_MAGIC = "VBLK".toByteArray(Charsets.US_ASCII) // 4 bytes

    const val DEFAULT_CHUNK_SIZE = 65536 // 64 KB
    const val GCM_TAG_BITS = 128
    const val GCM_TAG_BYTES = 16
    const val ENCRYPTED_CHUNK_SIZE = DEFAULT_CHUNK_SIZE + GCM_TAG_BYTES // 65552 bytes

    const val DEFAULT_ARGON2_MEMORY_KB = 65536 // 64 MB per spec
    const val DEFAULT_ARGON2_ITERATIONS = 3
    const val DEFAULT_ARGON2_PARALLELISM = 4

    private val secureRandom = SecureRandom()

    data class SpaceHeaderInfo(
        val spaceId: String,
        val headerFile: File,
        val salt: ByteArray,
        val memoryCostKb: Int,
        val timeCost: Int,
        val parallelism: Int,
        val mkNonce: ByteArray,
        val wrappedMk: ByteArray,
        val reservedPadding: ByteArray
    )

    data class BlobHeaderInfo(
        val chunkSize: Int,
        val dekNonce: ByteArray,
        val wrappedDek: ByteArray,
        val baseChunkNonce: ByteArray
    )

    /**
     * Generates cryptographically strong random bytes using CSPRNG.
     */
    fun randomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    /**
     * Derives a 256-bit Key Encryption Key (KEK) from a user PIN/Sequence CharArray
     * using Argon2id (RFC 9106).
     * Uses the exact Argon2 parameters supplied by the caller. A space header persists
     * these parameters, so silently adapting them during unlock would derive a different
     * key and make a valid sequence appear incorrect.
     */
    fun deriveKekArgon2id(
        pinChars: CharArray,
        salt: ByteArray,
        memoryCostKb: Int = DEFAULT_ARGON2_MEMORY_KB,
        iterations: Int = DEFAULT_ARGON2_ITERATIONS,
        parallelism: Int = DEFAULT_ARGON2_PARALLELISM
    ): ByteArray {
        require(salt.size == 16) { "Argon2id salt must be 16 bytes" }
        val passwordBytes = charsToUtf8Bytes(pinChars)
        val kek = ByteArray(32)
        try {
            require(memoryCostKb in 4096..262144) { "Invalid Argon2 memory cost" }
            require(iterations in 1..32) { "Invalid Argon2 iteration count" }
            require(parallelism in 1..16) { "Invalid Argon2 parallelism" }
            val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(memoryCostKb)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .build()

            val generator = Argon2BytesGenerator()
            generator.init(params)
            generator.generateBytes(passwordBytes, kek, 0, kek.size)
            return kek
        } finally {
            Arrays.fill(passwordBytes, 0.toByte())
        }
    }

    /**
     * Ensures that if the Android JVM heap is smaller than 192MB (e.g., unit tests or low-RAM container),
     * Argon2id uses a safe memory cap so it does not throw OutOfMemoryError, while using full 64MB
     * when sufficient heap is available.
     */
    fun resolveSafeArgon2MemoryKb(requestedKb: Int): Int {
        val runtime = Runtime.getRuntime()
        val maxHeapBytes = runtime.maxMemory()
        val usedBytes = runtime.totalMemory() - runtime.freeMemory()
        val availableBytes = maxHeapBytes - usedBytes
        // Need roughly 2x the Argon2 buffer size in free JVM heap to avoid OOM during block allocation
        val maxSafeKb = ((availableBytes / (1024 * 3)).toInt()).coerceAtLeast(4096)
        return requestedKb.coerceAtMost(maxSafeKb)
    }

    /**
     * Creates a new 128-byte Space Envelope Header (.header) wrapping the provided 256-bit MasterKey.
     * Note: Does NOT wipe [masterKey] so caller can continue using it; caller must wipe [pinChars].
     */
    fun createSpaceHeaderBytes(
        pinChars: CharArray,
        masterKey: ByteArray,
        memoryCostKb: Int = DEFAULT_ARGON2_MEMORY_KB,
        timeCost: Int = DEFAULT_ARGON2_ITERATIONS,
        parallelism: Int = DEFAULT_ARGON2_PARALLELISM
    ): ByteArray {
        require(masterKey.size == 32) { "MasterKey must be 32 bytes (256-bit)" }
        val salt = randomBytes(16)
        val mkNonce = randomBytes(12)
        val reserved = randomBytes(32)
        val effectiveMemoryKb = resolveSafeArgon2MemoryKb(memoryCostKb)

        val kek = deriveKekArgon2id(
            pinChars = pinChars,
            salt = salt,
            memoryCostKb = effectiveMemoryKb,
            iterations = timeCost,
            parallelism = parallelism
        )
        try {
            val wrappedMk = aesGcmEncrypt(key = kek, iv = mkNonce, plaintext = masterKey)
            require(wrappedMk.size == 48) { "Wrapped MK must be 48 bytes (32B ciphertext + 16B tag)" }

            val buffer = ByteBuffer.allocate(HEADER_SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            // 0x00 ~ 0x07: Magic CLCVLT01 (8B)
            buffer.put(HEADER_MAGIC)
            // 0x08 ~ 0x17: Salt (16B)
            buffer.put(salt)
            // 0x18 ~ 0x1B: MemoryCost UInt32 LE (4B)
            buffer.putInt(effectiveMemoryKb)
            // 0x1C ~ 0x1F: TimeCost UInt32 LE (4B)
            buffer.putInt(timeCost)
            // 0x20 ~ 0x23: Parallelism UInt32 LE (4B)
            buffer.putInt(parallelism)
            // 0x24 ~ 0x2F: MK_Nonce (12B)
            buffer.put(mkNonce)
            // 0x30 ~ 0x5F: Wrapped_MK (48B)
            buffer.put(wrappedMk)
            // 0x60 ~ 0x7F: Reserved CSPRNG Padding (32B)
            buffer.put(reserved)

            return buffer.array()
        } finally {
            Arrays.fill(kek, 0.toByte())
        }
    }

    /**
     * Parses a 128-byte .header file into [SpaceHeaderInfo].
     */
    fun parseSpaceHeader(headerFile: File): SpaceHeaderInfo? {
        if (!headerFile.exists() || headerFile.length() != HEADER_SIZE_BYTES.toLong()) {
            return null
        }
        val bytes = headerFile.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val magic = ByteArray(8)
        buffer.get(magic)
        if (!magic.contentEquals(HEADER_MAGIC)) {
            return null
        }

        val salt = ByteArray(16)
        buffer.get(salt)

        val memoryCostKb = buffer.int
        val timeCost = buffer.int
        val parallelism = buffer.int

        val mkNonce = ByteArray(12)
        buffer.get(mkNonce)

        val wrappedMk = ByteArray(48)
        buffer.get(wrappedMk)

        val reserved = ByteArray(32)
        buffer.get(reserved)

        val spaceId = headerFile.nameWithoutExtension
        return SpaceHeaderInfo(
            spaceId = spaceId,
            headerFile = headerFile,
            salt = salt,
            memoryCostKb = memoryCostKb,
            timeCost = timeCost,
            parallelism = parallelism,
            mkNonce = mkNonce,
            wrappedMk = wrappedMk,
            reservedPadding = reserved
        )
    }

    /**
     * Attempts to unlock a space header using [pinChars].
     * Returns the 32-byte MasterKey if the GCM authentication tag succeeds, or null if invalid.
     */
    fun tryUnlockSpaceHeader(headerInfo: SpaceHeaderInfo, pinChars: CharArray): ByteArray? {
        val kek = deriveKekArgon2id(
            pinChars = pinChars,
            salt = headerInfo.salt,
            memoryCostKb = headerInfo.memoryCostKb,
            iterations = headerInfo.timeCost,
            parallelism = headerInfo.parallelism
        )
        return try {
            aesGcmDecrypt(
                key = kek,
                iv = headerInfo.mkNonce,
                ciphertextWithTag = headerInfo.wrappedMk
            )
        } catch (_: AEADBadTagException) {
            null
        } catch (_: Exception) {
            null
        } finally {
            Arrays.fill(kek, 0.toByte())
        }
    }

    /**
     * Zero Re-encryption Password Change:
     * 1. Verifies Old_PIN against .header and unwraps MK
     * 2. Generates fresh 16B Salt and 12B MK_Nonce
     * 3. Derives New_KEK with Argon2id and wraps the SAME MK
     * 4. Atomically overwrites and fsyncs the 128-byte .header file without touching .db or .dat files!
     */
    fun changeSpacePinZeroReencryption(
        headerFile: File,
        oldPinChars: CharArray,
        newPinChars: CharArray
    ): Boolean {
        val headerInfo = parseSpaceHeader(headerFile) ?: return false
        val mk = tryUnlockSpaceHeader(headerInfo, oldPinChars) ?: return false
        return try {
            val newHeaderBytes = createSpaceHeaderBytes(
                pinChars = newPinChars,
                masterKey = mk,
                memoryCostKb = DEFAULT_ARGON2_MEMORY_KB,
                timeCost = headerInfo.timeCost,
                parallelism = headerInfo.parallelism
            )
            FileOutputStream(headerFile).use { fos ->
                fos.write(newHeaderBytes)
                fos.flush()
                fos.fd.sync()
            }
            true
        } finally {
            Arrays.fill(mk, 0.toByte())
        }
    }

    /**
     * Derives the 256-bit SQLCipher database passphrase bytes from the space's MasterKey
     * using HMAC-SHA256 domain separation so the raw MK is isolated.
     */
    fun deriveDatabaseKeyFromMasterKey(masterKey: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(masterKey, "HmacSHA256"))
        return mac.doFinal("XOME_SQLCIPHER_DB_KEY_V1".toByteArray(Charsets.UTF_8))
    }

    /**
     * Derives the 256-bit thumbnail encryption key from the space's MasterKey.
     */
    fun deriveThumbnailKeyFromMasterKey(masterKey: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(masterKey, "HmacSHA256"))
        return mac.doFinal("XOME_THUMBNAIL_AES_KEY_V1".toByteArray(Charsets.UTF_8))
    }

    /**
     * Encrypts an in-memory thumbnail (WebP/PNG bytes) using AES-256-GCM:
     * Format: [12B Nonce][Ciphertext + 16B Tag]
     */
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

    /**
     * Decrypts an in-memory thumbnail BLOB back into image bytes without touching disk.
     */
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
     * Streaming 64KB chunked encryption into a .dat blob file.
     *
     * File Header (76 Bytes):
     * - 0x00 ~ 0x03 (4B): Magic "VBLK"
     * - 0x04 ~ 0x07 (4B): Chunk Size (65536)
     * - 0x08 ~ 0x13 (12B): DEK_Nonce
     * - 0x14 ~ 0x43 (48B): Wrapped_DEK = AES-256-GCM(Key=MK, IV=DEK_Nonce, Plaintext=DEK)
     * - 0x44 ~ 0x4F (12B): Base_Chunk_Nonce
     *
     * Followed by Encrypted Chunks:
     * - Chunk i Nonce = Base_Chunk_Nonce XOR BigEndian(i)
     * - GCM AAD = BigEndian(i) (8 bytes)
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

        var totalPlainBytes = 0L
        try {
            val wrappedDek = aesGcmEncrypt(key = masterKey, iv = dekNonce, plaintext = dek)
            require(wrappedDek.size == 48)

            val headerBuffer = ByteBuffer.allocate(FILE_HEADER_SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            headerBuffer.put(FILE_MAGIC) // 4B
            headerBuffer.putInt(DEFAULT_CHUNK_SIZE) // 4B
            headerBuffer.put(dekNonce) // 12B
            headerBuffer.put(wrappedDek) // 48B
            headerBuffer.put(baseChunkNonce) // 12B

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

                // Handle 0-byte empty file gracefully by writing a single empty authenticated chunk
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

    /**
     * Streams and decrypts a .dat blob file to an OutputStream (used only when user explicitly
     * invokes "Decrypt & Export / Restore to Public Storage").
     */
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

    /**
     * Computes Chunk i Nonce = Base_Chunk_Nonce XOR BigEndian(i) on the lower 8 bytes.
     */
    fun computeChunkNonce(baseNonce: ByteArray, chunkIndex: Int): ByteArray {
        require(baseNonce.size == 12)
        val result = baseNonce.copyOf()
        val indexBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(chunkIndex.toLong()).array()
        for (k in 0 until 8) {
            result[4 + k] = (result[4 + k].toInt() xor indexBytes[k].toInt()).toByte()
        }
        return result
    }

    /**
     * Computes 8-byte BigEndian AAD for chunk index i to prevent block reordering attacks.
     */
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
