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
    fun keyfilePepper_isDeterministicPerFile_andRejectsEmptyStream() {
        val pepper1a = ByteArrayInputStream("original-pdf-content-v1".toByteArray()).use {
            VaultCryptoEngine.computeKeyfilePepper(it)
        }
        val pepper1b = ByteArrayInputStream("original-pdf-content-v1".toByteArray()).use {
            VaultCryptoEngine.computeKeyfilePepper(it)
        }
        val pepper2 = ByteArrayInputStream("original-pdf-content-v2".toByteArray()).use {
            VaultCryptoEngine.computeKeyfilePepper(it)
        }
        try {
            assertEquals(VaultCryptoEngine.KEYFILE_PEPPER_BYTES, pepper1a.pepper.size)
            assertEquals(23L, pepper1a.byteCount)
            assertArrayEquals(pepper1a.pepper, pepper1b.pepper)
            assertFalse(pepper1a.pepper.contentEquals(pepper2.pepper))

            var threw = false
            try {
                ByteArrayInputStream(ByteArray(0)).use {
                    VaultCryptoEngine.computeKeyfilePepper(it)
                }
            } catch (_: IllegalArgumentException) {
                threw = true
            }
            assertTrue("Empty keyfile must be rejected", threw)
        } finally {
            pepper1a.wipe()
            pepper1b.wipe()
            pepper2.wipe()
        }
    }

    @Test
    fun stealthContainer_8208Bytes_zeroMagic_slotChaff_recoveryAndRotations() {
        val tempDir = Files.createTempDirectory("xome_v2_container_test").toFile()
        try {
            val containerFile = File(tempDir, "8f3a1b9c.dat")
            val originalMasterKey = VaultCryptoEngine.randomBytes(32)
            val pepperA = ByteArrayInputStream("alice-master-keyfile-binary".toByteArray()).use {
                VaultCryptoEngine.computeKeyfilePepper(it)
            }
            val pepperB = ByteArrayInputStream("alice-rotated-keyfile-binary".toByteArray()).use {
                VaultCryptoEngine.computeKeyfilePepper(it)
            }
            val testParams = VaultCryptoEngine.Argon2Params(4096, 2, 2)

            val containerBytes = VaultCryptoEngine.createStealthContainerBytes(
                pinChars = "1314+20".toCharArray(),
                filePepper = pepperA.pepper,
                masterKey = originalMasterKey,
                params = testParams
            )
            assertEquals(VaultCryptoEngine.CONTAINER_SIZE_BYTES, containerBytes.size)
            assertEquals(8208, containerBytes.size)

            // Magic-free check: no recognizable ASCII container signature exists
            val ascii = String(containerBytes, Charsets.ISO_8859_1)
            assertFalse(ascii.contains("CLCVLT"))
            assertFalse(ascii.contains("VBLK"))
            assertFalse(ascii.contains("XOME"))

            containerFile.writeBytes(containerBytes)

            val parsed = VaultCryptoEngine.parseStealthContainer(containerFile)
            assertNotNull(parsed)
            assertEquals("8f3a1b9c", parsed!!.spaceId)

            val slotIndex = VaultCryptoEngine.computeSlotIndex(pepperA.pepper, parsed.containerNonce)
            assertTrue(slotIndex in 0 until VaultCryptoEngine.SLOT_COUNT)

            // Wrong sequence fails even with right Keyfile
            assertNull(
                VaultCryptoEngine.tryUnlockStealthContainer(
                    parsed, "9999+99".toCharArray(), pepperA.pepper, testParams
                )
            )
            // Wrong Keyfile fails even with right sequence
            assertNull(
                VaultCryptoEngine.tryUnlockStealthContainer(
                    parsed, "1314+20".toCharArray(), pepperB.pepper, testParams
                )
            )

            // Right sequence + right Keyfile unwraps exact 32B MasterKey
            val unlockedMk = VaultCryptoEngine.tryUnlockStealthContainer(
                parsed, "1314+20".toCharArray(), pepperA.pepper, testParams
            )
            assertNotNull(unlockedMk)
            assertArrayEquals(originalMasterKey, unlockedMk)

            // Weak sequence replacement cannot modify existing stealth container
            assertFalse(
                VaultCryptoEngine.rotateStealthContainerCredentials(
                    containerFile = containerFile,
                    oldPinChars = "1314+20".toCharArray(),
                    oldPepper = pepperA.pepper,
                    oldParams = testParams,
                    newPinChars = "123456".toCharArray(),
                    newPepper = pepperA.pepper,
                    newParams = testParams
                )
            )
            assertArrayEquals(containerBytes, containerFile.readBytes())

            // Zero Re-encryption Password Change: "1314+20" -> "9988+11"
            assertTrue(
                VaultCryptoEngine.rotateStealthContainerCredentials(
                    containerFile = containerFile,
                    oldPinChars = "1314+20".toCharArray(),
                    oldPepper = pepperA.pepper,
                    oldParams = testParams,
                    newPinChars = "9988+11".toCharArray(),
                    newPepper = pepperA.pepper,
                    newParams = VaultCryptoEngine.Argon2Profile.COMPACT.toParams()
                )
            )
            assertEquals(8208L, containerFile.length())

            val reparsedAfterSeqChange = VaultCryptoEngine.parseStealthContainer(containerFile)!!
            assertNull(
                VaultCryptoEngine.tryUnlockStealthContainer(
                    reparsedAfterSeqChange,
                    "1314+20".toCharArray(),
                    pepperA.pepper,
                    VaultCryptoEngine.Argon2Profile.COMPACT.toParams()
                )
            )
            // Cross-device recovery probes fixed tiers cleanly without stored KDF hints
            val recovered = VaultCryptoEngine.tryRecoverStealthContainer(
                containerInfo = reparsedAfterSeqChange,
                pinChars = "9988+11".toCharArray(),
                filePepper = pepperA.pepper,
                profiles = listOf(VaultCryptoEngine.Argon2Profile.COMPACT)
            )
            assertNotNull(recovered)
            assertEquals(VaultCryptoEngine.Argon2Profile.COMPACT, recovered!!.second)
            assertArrayEquals(originalMasterKey, recovered.first)

            // Zero Re-encryption Keyfile Rotation: pepperA -> pepperB (keeps sequence)
            assertTrue(
                VaultCryptoEngine.rotateStealthContainerCredentials(
                    containerFile = containerFile,
                    oldPinChars = "9988+11".toCharArray(),
                    oldPepper = pepperA.pepper,
                    oldParams = VaultCryptoEngine.Argon2Profile.COMPACT.toParams(),
                    newPinChars = "9988+11".toCharArray(),
                    newPepper = pepperB.pepper,
                    newParams = testParams
                )
            )
            val reparsedAfterKeyfileRotation = VaultCryptoEngine.parseStealthContainer(containerFile)!!
            assertNull(
                VaultCryptoEngine.tryUnlockStealthContainer(
                    reparsedAfterKeyfileRotation, "9988+11".toCharArray(), pepperA.pepper, testParams
                )
            )
            val unlockedWithRotatedKeyfile = VaultCryptoEngine.tryUnlockStealthContainer(
                reparsedAfterKeyfileRotation, "9988+11".toCharArray(), pepperB.pepper, testParams
            )
            assertNotNull(unlockedWithRotatedKeyfile)
            assertArrayEquals(originalMasterKey, unlockedWithRotatedKeyfile)

            pepperA.wipe()
            pepperB.wipe()
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
            // Expected size: 80B magic-free header (0x00..0x4F) + 3 chunks * 16B tag + 150,000B ciphertext
            assertEquals(VaultCryptoEngine.FILE_HEADER_SIZE_BYTES.toLong() + 150_000L + 3L * 16L, blobFile.length())
            val headerSlice = blobFile.inputStream().use { ins ->
                ByteArray(VaultCryptoEngine.FILE_HEADER_SIZE_BYTES).also { ins.read(it) }
            }
            assertFalse(String(headerSlice, Charsets.ISO_8859_1).contains("VBLK"))

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
