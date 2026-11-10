package com.example

import com.example.calculator.ScientificCalculatorEngine
import com.example.crypto.RandomAccessDecryptedStream
import com.example.crypto.VaultCryptoEngine
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun weakReplacementCannotChangeExistingVaultCredential() {
        val tempDir = Files.createTempDirectory("xome_rotation_test").toFile()
        try {
            val headerFile = File(tempDir, "legacy.header")
            val original = VaultCryptoEngine.createSpaceHeaderBytes(
                "1314".toCharArray(), ByteArray(32) { 7 }, 4096, 1, 1
            )
            headerFile.writeBytes(original)
            assertFalse(VaultCryptoEngine.changeSpacePinZeroReencryption(
                headerFile, "1314".toCharArray(), "123456".toCharArray()
            ))
            assertArrayEquals(original, headerFile.readBytes())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun spaceHeader_128Bytes_createParseAndZeroReencryptionPinChange() {
        val tempDir = Files.createTempDirectory("xome_test").toFile()
        try {
            val headerFile = File(tempDir, "8f3a1b9c.header")
            val originalMasterKey = VaultCryptoEngine.randomBytes(32)

            val headerBytes = VaultCryptoEngine.createSpaceHeaderBytes(
                pinChars = "1314".toCharArray(),
                masterKey = originalMasterKey,
                memoryCostKb = 4096,
                timeCost = 2,
                parallelism = 2
            )
            assertEquals(128, headerBytes.size)
            headerFile.writeBytes(headerBytes)

            val parsed = VaultCryptoEngine.parseSpaceHeader(headerFile)
            assertNotNull(parsed)
            assertEquals("8f3a1b9c", parsed!!.spaceId)

            // Wrong PIN returns null
            assertNull(VaultCryptoEngine.tryUnlockSpaceHeader(parsed, "9999".toCharArray()))

            // Correct PIN unwraps exact 32B MasterKey
            val unlockedMk = VaultCryptoEngine.tryUnlockSpaceHeader(parsed, "1314".toCharArray())
            assertNotNull(unlockedMk)
            assertArrayEquals(originalMasterKey, unlockedMk)

            // Zero Re-encryption Password Change to "9988+11"
            val rotated = VaultCryptoEngine.changeSpacePinZeroReencryption(
                headerFile = headerFile,
                oldPinChars = "1314".toCharArray(),
                newPinChars = "9988+11".toCharArray()
            )
            assertTrue(rotated)
            assertEquals(128L, headerFile.length())

            val reparsed = VaultCryptoEngine.parseSpaceHeader(headerFile)!!
            assertEquals(65536, reparsed.memoryCostKb)
            assertEquals(3, reparsed.timeCost)
            assertNull(VaultCryptoEngine.tryUnlockSpaceHeader(reparsed, "1314".toCharArray()))
            val unlockedWithNewPin = VaultCryptoEngine.tryUnlockSpaceHeader(reparsed, "9988+11".toCharArray())
            assertNotNull(unlockedWithNewPin)
            assertArrayEquals(originalMasterKey, unlockedWithNewPin)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun streaming64KBChunks_and_RandomAccessSeekDecryption() {
        val tempDir = Files.createTempDirectory("xome_blob_test").toFile()
        try {
            val masterKey = VaultCryptoEngine.randomBytes(32)
            val blobFile = File(tempDir, "test_multi_chunk.dat")

            // Create 150,000 bytes (spans 3 x 64KB chunks: 65536 + 65536 + 18928)
            val originalPlain = ByteArray(150_000) { idx -> ((idx * 31 + 7) and 0xFF).toByte() }

            val written = ByteArrayInputStream(originalPlain).use { bais ->
                VaultCryptoEngine.encryptStreamToBlobFile(masterKey, bais, blobFile)
            }
            assertEquals(150_000L, written)
            // Expected size: 80B header (0x00..0x4F) + 3 chunks * 16B tag + 150,000B ciphertext
            assertEquals(VaultCryptoEngine.FILE_HEADER_SIZE_BYTES.toLong() + 150_000L + 3L * 16L, blobFile.length())

            RandomAccessDecryptedStream(blobFile, masterKey).use { stream ->
                assertEquals(3, stream.totalChunks)
                assertEquals(150_000L, stream.plainLength)

                // Test O(1) seek across chunk boundary (from offset 65500 to 65600)
                val crossBoundarySlice = stream.readRange(65_500L, 100)
                assertArrayEquals(originalPlain.copyOfRange(65_500, 65_600), crossBoundarySlice)

                // Test O(1) seek into 3rd chunk (offset 140,000)
                val tailSlice = stream.readRange(140_000L, 500)
                assertArrayEquals(originalPlain.copyOfRange(140_000, 140_500), tailSlice)

                // Full stream verification
                val fullDecrypted = stream.decryptAllToMemory()
                assertArrayEquals(originalPlain, fullDecrypted)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun scientificCalculator_evaluatesExpressionsCorrectly() {
        val r1 = ScientificCalculatorEngine.evaluateExpression("9988+11").getOrThrow()
        assertEquals(9999.0, r1, 1e-9)

        val r2 = ScientificCalculatorEngine.evaluateExpression("2(3+4)^2").getOrThrow()
        assertEquals(98.0, r2, 1e-9)

        val r3 = ScientificCalculatorEngine.evaluateExpression("sin(30)", isDegreeMode = true).getOrThrow()
        assertEquals(0.5, r3, 1e-6)

        val r4 = ScientificCalculatorEngine.evaluateExpression("5!").getOrThrow()
        assertEquals(120.0, r4, 1e-9)
    }
}
