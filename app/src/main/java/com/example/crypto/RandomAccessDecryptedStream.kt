package com.example.crypto

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.Arrays
import kotlin.math.min

/**
 * O(1) Random-Access In-Memory Decrypted Stream for magic-free .dat encrypted blobs.
 *
 * - File Header is 80 bytes (12B DEK_Nonce, 48B Wrapped_DEK, 12B Base_Chunk_Nonce, 8B Padding)
 * - Each encrypted chunk on disk is (65536 + 16) = 65552 bytes.
 * - Zero temporary files are ever written to disk.
 */
class RandomAccessDecryptedStream(
    private val blobFile: File,
    masterKey: ByteArray
) : InputStream() {

    private val raf = RandomAccessFile(blobFile, "r")
    val chunkSize: Int = VaultCryptoEngine.DEFAULT_CHUNK_SIZE
    private val dek: ByteArray
    private val baseChunkNonce: ByteArray
    val totalChunks: Int
    val plainLength: Long

    private var currentPlainPosition: Long = 0L
    private var cachedChunkIndex: Int = -1
    private var cachedPlainChunk: ByteArray? = null
    private var closed = false

    init {
        if (raf.length() < VaultCryptoEngine.FILE_HEADER_SIZE_BYTES) {
            raf.close()
            throw IOException("Invalid .dat blob file: smaller than 80-byte header")
        }

        val headerBytes = ByteArray(VaultCryptoEngine.FILE_HEADER_SIZE_BYTES)
        raf.seek(0L)
        raf.readFully(headerBytes)

        val buffer = ByteBuffer.wrap(headerBytes)
        val dekNonce = ByteArray(12)
        buffer.get(dekNonce)

        val wrappedDek = ByteArray(48)
        buffer.get(wrappedDek)

        baseChunkNonce = ByteArray(12)
        buffer.get(baseChunkNonce)

        dek = try {
            VaultCryptoEngine.aesGcmDecrypt(
                key = masterKey,
                iv = dekNonce,
                ciphertextWithTag = wrappedDek,
                aad = baseChunkNonce
            )
        } catch (e: Exception) {
            raf.close()
            throw IOException("Failed to unwrap file DEK with space MasterKey", e)
        }

        val encryptedPayloadBytes = raf.length() - VaultCryptoEngine.FILE_HEADER_SIZE_BYTES
        val encryptedChunkSize = (chunkSize + VaultCryptoEngine.GCM_TAG_BYTES).toLong()
        if (encryptedPayloadBytes <= 0L) {
            totalChunks = 0
            plainLength = 0L
        } else {
            val fullChunks = (encryptedPayloadBytes / encryptedChunkSize).toInt()
            val remainder = (encryptedPayloadBytes % encryptedChunkSize).toInt()
            if (remainder == 0) {
                totalChunks = fullChunks
                plainLength = fullChunks.toLong() * chunkSize.toLong()
            } else {
                totalChunks = fullChunks + 1
                val lastChunkPlainBytes = (remainder - VaultCryptoEngine.GCM_TAG_BYTES).coerceAtLeast(0)
                plainLength = fullChunks.toLong() * chunkSize.toLong() + lastChunkPlainBytes.toLong()
            }
        }
    }

    fun seek(plainOffset: Long) {
        check(!closed) { "Stream is closed" }
        currentPlainPosition = plainOffset.coerceIn(0L, plainLength)
    }

    fun currentOffset(): Long = currentPlainPosition

    fun readRange(plainOffset: Long, length: Int): ByteArray {
        seek(plainOffset)
        val toRead = min(length.toLong(), plainLength - currentPlainPosition).toInt().coerceAtLeast(0)
        val out = ByteArray(toRead)
        var offset = 0
        while (offset < toRead) {
            val n = read(out, offset, toRead - offset)
            if (n <= 0) break
            offset += n
        }
        return if (offset == out.size) out else out.copyOf(offset)
    }

    fun decryptAllToMemory(): ByteArray {
        seek(0L)
        val cap = plainLength.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val out = ByteArrayOutputStream(cap)
        val buf = ByteArray(min(chunkSize, 32768))
        while (true) {
            val r = read(buf)
            if (r <= 0) break
            out.write(buf, 0, r)
        }
        return out.toByteArray()
    }

    override fun read(): Int {
        val one = ByteArray(1)
        val r = read(one, 0, 1)
        return if (r == -1) -1 else (one[0].toInt() and 0xFF)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        check(!closed) { "Stream is closed" }
        if (len == 0) return 0
        if (currentPlainPosition >= plainLength) return -1

        var totalCopied = 0
        while (totalCopied < len && currentPlainPosition < plainLength) {
            val chunkIdx = (currentPlainPosition / chunkSize.toLong()).toInt()
            val inChunkOffset = (currentPlainPosition % chunkSize.toLong()).toInt()
            val plainChunk = loadOrDecryptChunk(chunkIdx)
            val available = plainChunk.size - inChunkOffset
            if (available <= 0) break

            val toCopy = min(len - totalCopied, available)
            System.arraycopy(plainChunk, inChunkOffset, b, off + totalCopied, toCopy)
            totalCopied += toCopy
            currentPlainPosition += toCopy.toLong()
        }
        return if (totalCopied == 0) -1 else totalCopied
    }

    private fun loadOrDecryptChunk(chunkIndex: Int): ByteArray {
        if (cachedChunkIndex == chunkIndex && cachedPlainChunk != null) {
            return cachedPlainChunk!!
        }

        cachedPlainChunk?.let { Arrays.fill(it, 0.toByte()) }
        cachedPlainChunk = null
        cachedChunkIndex = -1

        val encryptedChunkSize = (chunkSize + VaultCryptoEngine.GCM_TAG_BYTES).toLong()
        val physicalOffset = VaultCryptoEngine.FILE_HEADER_SIZE_BYTES.toLong() +
            chunkIndex.toLong() * encryptedChunkSize
        val remainingPhysical = (raf.length() - physicalOffset).coerceAtMost(encryptedChunkSize).toInt()
        if (remainingPhysical < VaultCryptoEngine.GCM_TAG_BYTES) {
            throw IOException("Corrupt encrypted chunk at index $chunkIndex")
        }

        val encryptedChunk = ByteArray(remainingPhysical)
        raf.seek(physicalOffset)
        raf.readFully(encryptedChunk)

        val chunkNonce = VaultCryptoEngine.computeChunkNonce(baseChunkNonce, chunkIndex)
        val aad = VaultCryptoEngine.chunkIndexToAad(chunkIndex)
        val plain = VaultCryptoEngine.aesGcmDecrypt(
            key = dek,
            iv = chunkNonce,
            ciphertextWithTag = encryptedChunk,
            aad = aad
        )
        cachedChunkIndex = chunkIndex
        cachedPlainChunk = plain
        return plain
    }

    override fun close() {
        if (!closed) {
            closed = true
            Arrays.fill(dek, 0.toByte())
            cachedPlainChunk?.let { Arrays.fill(it, 0.toByte()) }
            cachedPlainChunk = null
            cachedChunkIndex = -1
            try {
                raf.close()
            } catch (_: Exception) {
            }
        }
    }
}
