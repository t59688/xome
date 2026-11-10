package com.example.crypto

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays
import kotlin.math.min

/**
 * O(1) Random-Access In-Memory Decrypted Stream for .dat encrypted blobs.
 *
 * - File Header is 76 bytes ("VBLK", chunkSize=65536, DEK_Nonce, Wrapped_DEK, Base_Chunk_Nonce)
 * - Each encrypted chunk on disk is (chunkSize + 16) = 65552 bytes.
 * - Any plaintext byte offset `offset` maps directly in O(1) to:
 *   `chunkIndex = (offset / 65536).toInt()`
 *   `physicalOffset = 76L + chunkIndex * 65552L`
 *   `inChunkOffset = (offset % 65536).toInt()`
 * - Zero temporary files are ever written to disk.
 */
class RandomAccessDecryptedStream(
    private val blobFile: File,
    masterKey: ByteArray
) : InputStream() {

    private val raf = RandomAccessFile(blobFile, "r")
    val chunkSize: Int
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
            throw IOException("Invalid .dat blob file: smaller than 76-byte header")
        }

        val headerBytes = ByteArray(VaultCryptoEngine.FILE_HEADER_SIZE_BYTES)
        raf.seek(0L)
        raf.readFully(headerBytes)

        val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4)
        buffer.get(magic)
        if (!magic.contentEquals(VaultCryptoEngine.FILE_MAGIC)) {
            raf.close()
            throw IOException("Invalid .dat blob magic header (expected VBLK)")
        }

        chunkSize = buffer.int
        if (chunkSize <= 0 || chunkSize > 1024 * 1024) {
            raf.close()
            throw IOException("Invalid chunk size in .dat header: $chunkSize")
        }

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
                ciphertextWithTag = wrappedDek
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
                // Last chunk is full size
                plainLength = fullChunks.toLong() * chunkSize.toLong()
            } else {
                totalChunks = fullChunks + 1
                val lastChunkPlainBytes = (remainder - VaultCryptoEngine.GCM_TAG_BYTES).coerceAtLeast(0)
                plainLength = fullChunks.toLong() * chunkSize.toLong() + lastChunkPlainBytes.toLong()
            }
        }
    }

    /**
     * O(1) random-access seek to any plaintext byte offset.
     */
    fun seek(plainOffset: Long) {
        check(!closed) { "Stream is closed" }
        currentPlainPosition = plainOffset.coerceIn(0L, plainLength)
    }

    fun currentOffset(): Long = currentPlainPosition

    /**
     * Reads a random-access byte range `[plainOffset, plainOffset + length)` directly in memory.
     */
    fun readRange(plainOffset: Long, length: Int): ByteArray {
        seek(plainOffset)
        val toRead = min(length.toLong(), plainLength - currentPlainPosition).toInt().coerceAtLeast(0)
        val out = ByteArray(toRead)
        var total = 0
        while (total < toRead) {
            val r = read(out, total, toRead - total)
            if (r <= 0) break
            total += r
        }
        return if (total == toRead) out else out.copyOf(total)
    }

    /**
     * Decrypts the entire stream into memory for in-memory BitmapRegionDecoder / BitmapFactory decoding.
     */
    fun decryptAllToMemory(): ByteArray {
        seek(0L)
        val out = ByteArrayOutputStream(plainLength.toInt().coerceAtLeast(32))
        val buf = ByteArray(chunkSize)
        while (true) {
            val r = read(buf)
            if (r <= 0) break
            out.write(buf, 0, r)
        }
        return out.toByteArray()
    }

    override fun read(): Int {
        val single = ByteArray(1)
        val r = read(single, 0, 1)
        return if (r == -1) -1 else (single[0].toInt() and 0xFF)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        check(!closed) { "Stream is closed" }
        if (len == 0) return 0
        if (currentPlainPosition >= plainLength) return -1

        val chunkIndex = (currentPlainPosition / chunkSize).toInt()
        val inChunkOffset = (currentPlainPosition % chunkSize).toInt()

        val plainChunk = loadAndDecryptChunk(chunkIndex)
        val availableInChunk = plainChunk.size - inChunkOffset
        if (availableInChunk <= 0) return -1

        val bytesToCopy = min(len, availableInChunk)
        System.arraycopy(plainChunk, inChunkOffset, b, off, bytesToCopy)
        currentPlainPosition += bytesToCopy
        return bytesToCopy
    }

    override fun available(): Int {
        return (plainLength - currentPlainPosition).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun loadAndDecryptChunk(chunkIndex: Int): ByteArray {
        if (cachedChunkIndex == chunkIndex && cachedPlainChunk != null) {
            return cachedPlainChunk!!
        }

        val encryptedChunkSize = chunkSize + VaultCryptoEngine.GCM_TAG_BYTES
        val physicalOffset = VaultCryptoEngine.FILE_HEADER_SIZE_BYTES.toLong() +
            chunkIndex.toLong() * encryptedChunkSize.toLong()

        val remainingFileBytes = (raf.length() - physicalOffset).coerceAtLeast(0L)
        val bytesToRead = min(encryptedChunkSize.toLong(), remainingFileBytes).toInt()
        if (bytesToRead < VaultCryptoEngine.GCM_TAG_BYTES) {
            throw IOException("Corrupted encrypted chunk $chunkIndex in ${blobFile.name}")
        }

        val encryptedChunk = ByteArray(bytesToRead)
        raf.seek(physicalOffset)
        raf.readFully(encryptedChunk)

        val chunkNonce = VaultCryptoEngine.computeChunkNonce(baseChunkNonce, chunkIndex)
        val aad = VaultCryptoEngine.chunkIndexToAad(chunkIndex)

        val decrypted = VaultCryptoEngine.aesGcmDecrypt(
            key = dek,
            iv = chunkNonce,
            ciphertextWithTag = encryptedChunk,
            aad = aad
        )

        // Wipe previous cached plaintext chunk before caching new chunk
        cachedPlainChunk?.let { Arrays.fill(it, 0.toByte()) }
        cachedChunkIndex = chunkIndex
        cachedPlainChunk = decrypted
        return decrypted
    }

    override fun close() {
        if (!closed) {
            closed = true
            Arrays.fill(dek, 0.toByte())
            Arrays.fill(baseChunkNonce, 0.toByte())
            cachedPlainChunk?.let { Arrays.fill(it, 0.toByte()) }
            cachedPlainChunk = null
            try {
                raf.close()
            } catch (_: Exception) {
            }
        }
    }
}
