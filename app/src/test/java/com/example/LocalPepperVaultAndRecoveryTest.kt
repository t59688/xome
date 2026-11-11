package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.LocalPepperVault
import com.example.crypto.VaultCryptoEngine
import com.example.storage.VaultStorageManager
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalPepperVaultAndRecoveryTest {

    @Test
    fun saveLoadWipeDeleteSeal_andRejectAadOrCiphertextTampering() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val spaceA = "deadbeef"
        val spaceB = "cafebabe"
        val pepper = ByteArray(VaultCryptoEngine.KEYFILE_PEPPER_BYTES) { idx -> (idx + 3).toByte() }

        LocalPepperVault.deleteSeal(context, spaceA)
        LocalPepperVault.deleteSeal(context, spaceB)
        assertFalse(LocalPepperVault.hasSeal(context, spaceA))

        LocalPepperVault.saveSeal(
            context = context,
            spaceId = spaceA,
            profile = VaultCryptoEngine.Argon2Profile.STANDARD,
            filePepper = pepper
        )
        assertTrue(LocalPepperVault.hasSeal(context, spaceA))
        assertTrue(spaceA in LocalPepperVault.listSealedSpaceIds(context))

        val loaded = LocalPepperVault.loadSeal(context, spaceA)
        assertNotNull(loaded)
        assertEquals(VaultCryptoEngine.Argon2Profile.STANDARD, loaded!!.profile)
        assertArrayEquals(pepper, loaded.filePepper)

        loaded.wipe()
        assertTrue(loaded.filePepper.all { it == 0.toByte() })

        // Cross-space substitution must fail GCM AAD authentication
        val sealsDir = File(context.filesDir, ".vault_seals")
        val sealFileA = File(sealsDir, "$spaceA.seal")
        val sealFileB = File(sealsDir, "$spaceB.seal")
        sealFileA.copyTo(sealFileB, overwrite = true)
        assertNull(LocalPepperVault.loadSeal(context, spaceB))

        // Bit flip inside ciphertext/tag must fail authentication
        val corrupted = sealFileA.readBytes()
        corrupted[corrupted.lastIndex] = (corrupted[corrupted.lastIndex].toInt() xor 0xFF).toByte()
        sealFileA.writeBytes(corrupted)
        assertNull(LocalPepperVault.loadSeal(context, spaceA))

        LocalPepperVault.deleteSeal(context, spaceA)
        LocalPepperVault.deleteSeal(context, spaceB)
        assertFalse(LocalPepperVault.hasSeal(context, spaceA))
    }

    @Test
    fun containerDiscovery_hidesUnsealedSpacesForDisguise_andRecoversWithKeyfile(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tree = VaultStorageManager.resolveVaultTree(context)
        tree.spacesDir.listFiles()?.forEach { it.delete() }
        LocalPepperVault.listSealedSpaceIds(context).forEach { LocalPepperVault.deleteSeal(context, it) }

        val spaceId = "a1b2c3d4"
        val containerFile = File(tree.spacesDir, "$spaceId.dat")
        val masterKey = VaultCryptoEngine.randomBytes(32)
        val keyfileDigest = ByteArrayInputStream("user-backup-contract-pdf".toByteArray()).use {
            VaultCryptoEngine.computeKeyfilePepper(it)
        }
        val wrongKeyfile = ByteArrayInputStream("wrong-file".toByteArray()).use {
            VaultCryptoEngine.computeKeyfilePepper(it)
        }
        val sequence = "2468+10"
        val profile = VaultCryptoEngine.Argon2Profile.COMPACT

        val containerBytes = VaultCryptoEngine.createStealthContainerBytes(
            pinChars = sequence.toCharArray(),
            filePepper = keyfileDigest.pepper,
            masterKey = masterKey,
            params = profile.toParams()
        )
        containerFile.writeBytes(containerBytes)

        try {
            // Reinstall / fresh-device simulation: container exists on disk with NO local seal
            assertEquals(1, VaultStorageManager.listSpaceContainerFiles(context).size)
            assertEquals(0, VaultStorageManager.listSealedSpaceContainerFiles(context).size)
            assertEquals(1, VaultStorageManager.countUnsealedSpaceContainers(context))
            assertFalse(VaultStorageManager.hasReadyLocalSpaces(context))

            // Sequence-only blind unlock ignores unsealed containers
            assertNull(VaultStorageManager.tryBlindUnlock(context, sequence))

            // Wrong keyfile cannot recover even with right sequence
            assertNull(VaultStorageManager.recoverSpaceWithKeyfile(context, sequence, wrongKeyfile.pepper))

            // Simulating successful Keyfile verification + sealing onto new device
            val parsed = VaultCryptoEngine.parseStealthContainer(containerFile)!!
            val recovered = VaultCryptoEngine.tryRecoverStealthContainer(
                containerInfo = parsed,
                pinChars = sequence.toCharArray(),
                filePepper = keyfileDigest.pepper,
                profiles = listOf(profile)
            )
            assertNotNull(recovered)
            assertArrayEquals(masterKey, recovered!!.first)
            assertEquals(profile, recovered.second)

            LocalPepperVault.saveSeal(context, spaceId, recovered.second, keyfileDigest.pepper)

            // Once sealed locally, device knows space is ready for sequence-only daily unlock
            assertEquals(1, VaultStorageManager.listSealedSpaceContainerFiles(context).size)
            assertEquals(0, VaultStorageManager.countUnsealedSpaceContainers(context))
            assertTrue(VaultStorageManager.hasReadyLocalSpaces(context))

            val sealed = LocalPepperVault.loadSeal(context, spaceId)!!
            val dailyUnlockedMk = VaultCryptoEngine.tryUnlockStealthContainer(
                containerInfo = parsed,
                pinChars = sequence.toCharArray(),
                filePepper = sealed.filePepper,
                params = sealed.profile.toParams()
            )
            sealed.wipe()
            assertArrayEquals(masterKey, dailyUnlockedMk)
        } finally {
            containerFile.delete()
            LocalPepperVault.deleteSeal(context, spaceId)
            keyfileDigest.wipe()
            wrongKeyfile.wipe()
        }
    }
}
