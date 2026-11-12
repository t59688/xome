package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.VaultCryptoEngine
import com.example.crypto.VaultSessionManager
import com.example.data.VaultDatabase
import com.example.data.VaultFileEntity
import com.example.data.VaultFolderEntity
import com.example.storage.VaultStorageManager
import com.example.ui.vault.buildFlattenedFolderTree
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultFileManagerUnitTest {

    private lateinit var context: Context
    private lateinit var database: VaultDatabase
    private lateinit var masterKey: ByteArray
    private lateinit var blobsDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        masterKey = ByteArray(32) { (it + 1).toByte() }
        blobsDir = VaultStorageManager.resolveVaultTree(context).blobsDir.apply { mkdirs() }
        val spaceId = "test_fm_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val containerFile = File(context.cacheDir, "$spaceId.xome").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(64))
        }
        VaultSessionManager.activateSession(
            VaultSessionManager.ActiveVaultSession(
                spaceId = spaceId,
                containerFile = containerFile,
                containerInfo = VaultCryptoEngine.SpaceContainerInfo(
                    spaceId = spaceId,
                    containerFile = containerFile,
                    containerNonce = ByteArray(16) { 9 }
                ),
                kdfProfile = VaultCryptoEngine.Argon2Profile.COMPACT,
                rawMasterKey = masterKey.copyOf(),
                database = database
            )
        )
    }

    @After
    fun tearDown() {
        val session = VaultSessionManager.activeSession.value
        VaultSessionManager.lock()
        session?.containerFile?.delete()
        blobsDir.deleteRecursively()
        runCatching { database.close() }
    }

    @Test
    fun `resolveUniqueCopyName handles extensions extensionless dotfiles and case conflicts`() {
        val existing = mutableSetOf("report.pdf", "report (1).pdf", "readme", ".env")
        assertEquals("REPORT (2).pdf", VaultStorageManager.resolveUniqueCopyName(existing, "REPORT.pdf"))
        assertEquals("report (3).pdf", VaultStorageManager.resolveUniqueCopyName(existing, "report.pdf"))
        assertEquals("README (1)", VaultStorageManager.resolveUniqueCopyName(existing, "README"))
        assertEquals(".env (1)", VaultStorageManager.resolveUniqueCopyName(existing, ".env"))
        assertEquals("fresh.txt", VaultStorageManager.resolveUniqueCopyName(existing, "fresh.txt"))
    }

    @Test
    fun `buildFlattenedFolderTree preserves hierarchy alphabetical order and resists cycles`() {
        val rootB = VaultFolderEntity(id = 1, parentId = null, folderName = "Work")
        val rootA = VaultFolderEntity(id = 2, parentId = null, folderName = "archive")
        val childOfA = VaultFolderEntity(id = 3, parentId = 2, folderName = "2026")
        val cycle1 = VaultFolderEntity(id = 4, parentId = 5, folderName = "CycleA")
        val cycle2 = VaultFolderEntity(id = 5, parentId = 4, folderName = "CycleB")

        val flattened = buildFlattenedFolderTree(listOf(rootB, rootA, childOfA, cycle1, cycle2))
        assertEquals(listOf("archive", "2026", "Work", "CycleA", "CycleB"), flattened.map { it.folder.folderName })
        assertEquals(listOf(0, 1, 0, 0, 1), flattened.map { it.depth })
    }

    @Test
    fun `copy move shred and recursive folder delete operate cleanly with real encrypted blobs`() = runBlocking {
        val dao = database.vaultDao()
        val folderAId = dao.insertFolder(VaultFolderEntity(parentId = null, folderName = "FolderA"))
        val childBId = dao.insertFolder(VaultFolderEntity(parentId = folderAId, folderName = "ChildB"))
        val folderCId = dao.insertFolder(VaultFolderEntity(parentId = null, folderName = "FolderC"))

        val payload1 = "Secret document payload 1234567890".encodeToByteArray()
        val payload2 = ByteArray(150_000) { (it % 251).toByte() } // Multi-chunk 150KB payload
        val thumbBytes = "fake_thumb_jpeg".encodeToByteArray()

        val blobName1 = "${UUID.randomUUID()}.dat"
        val blobName2 = "${UUID.randomUUID()}.dat"
        val blob1File = File(blobsDir, blobName1)
        val blob2File = File(blobsDir, blobName2)

        VaultCryptoEngine.encryptStreamToBlobFile(masterKey, ByteArrayInputStream(payload1), blob1File)
        VaultCryptoEngine.encryptStreamToBlobFile(masterKey, ByteArrayInputStream(payload2), blob2File)
        val encThumb1 = VaultCryptoEngine.encryptThumbnail(masterKey, thumbBytes)

        val file1Id = dao.insertFile(
            VaultFileEntity(
                folderId = null,
                originalName = "secret.txt",
                mimeType = "text/plain",
                originalSize = payload1.size.toLong(),
                blobFilename = blobName1,
                encryptedThumbnail = encThumb1,
                originalUriPath = ""
            )
        )
        val file2Id = dao.insertFile(
            VaultFileEntity(
                folderId = folderAId,
                originalName = "archive.bin",
                mimeType = "application/octet-stream",
                originalSize = payload2.size.toLong(),
                blobFilename = blobName2,
                encryptedThumbnail = null,
                originalUriPath = ""
            )
        )

        val file1 = dao.getFileById(file1Id)!!
        val file2 = dao.getFileById(file2Id)!!

        // 1. Copy file1 to same folder (Root) and verify collision name + independent blob & thumbnail ciphertext
        val copySummary = VaultStorageManager.copyVaultFilesToFolder(context, listOf(file1), null)
        assertEquals(1, copySummary.succeeded)
        assertEquals(0, copySummary.failed)

        val rootFiles = dao.getFilesInRootSync()
        assertEquals(2, rootFiles.size)
        val copiedEntity = rootFiles.first { it.id != file1Id }
        assertEquals("secret (1).txt", copiedEntity.originalName)
        assertNotEquals(file1.blobFilename, copiedEntity.blobFilename)
        assertNotEquals(file1.encryptedThumbnail?.toList(), copiedEntity.encryptedThumbnail?.toList())

        val copiedPlainOut = ByteArrayOutputStream()
        VaultCryptoEngine.decryptBlobFileToStream(
            masterKey = masterKey,
            blobFile = File(blobsDir, copiedEntity.blobFilename),
            outputStream = copiedPlainOut
        )
        assertArrayEquals(payload1, copiedPlainOut.toByteArray())

        val decryptedCopiedThumb = VaultCryptoEngine.decryptThumbnail(
            masterKey = masterKey,
            encryptedBlob = copiedEntity.encryptedThumbnail!!
        )
        assertArrayEquals(thumbBytes, decryptedCopiedThumb)

        // 2. Copy multi-chunk file2 into ChildB and verify full multi-chunk integrity
        val childCopySummary = VaultStorageManager.copyVaultFilesToFolder(context, listOf(file2), childBId)
        assertEquals(1, childCopySummary.succeeded)
        val childBFiles = dao.getFilesInFolderSync(childBId)
        assertEquals(1, childBFiles.size)
        val childOut = ByteArrayOutputStream()
        VaultCryptoEngine.decryptBlobFileToStream(
            masterKey = masterKey,
            blobFile = File(blobsDir, childBFiles.first().blobFilename),
            outputStream = childOut
        )
        assertArrayEquals(payload2, childOut.toByteArray())

        // 3. Move copiedEntity from Root into FolderC (and verify moving again into FolderC skips)
        val moveSummary = VaultStorageManager.moveVaultFilesToFolder(listOf(copiedEntity), folderCId)
        assertEquals(1, moveSummary.succeeded)
        assertEquals(0, moveSummary.skipped)
        assertEquals(folderCId, dao.getFileById(copiedEntity.id)?.folderId)

        val moveSameSummary = VaultStorageManager.moveVaultFilesToFolder(listOf(dao.getFileById(copiedEntity.id)!!), folderCId)
        assertEquals(0, moveSameSummary.succeeded)
        assertEquals(1, moveSameSummary.skipped)

        // 4. Delete FolderA recursively: shreds file2 in FolderA and copy of file2 in ChildB, keeps file1 in Root and copiedEntity in FolderC
        val folderA = dao.getFolderById(folderAId)!!
        val deletedIds = VaultStorageManager.deleteFolderRecursive(context, folderA)
        assertTrue(deletedIds.contains(file2Id))
        assertTrue(deletedIds.contains(childBFiles.first().id))
        assertEquals(null, dao.getFolderById(folderAId))
        assertEquals(null, dao.getFolderById(childBId))
        assertFalse(File(blobsDir, blobName2).exists())
        assertFalse(File(blobsDir, childBFiles.first().blobFilename).exists())
        assertTrue(File(blobsDir, blobName1).exists())
        assertTrue(dao.getFileById(copiedEntity.id) != null)
    }

    @Test
    fun `import file uri with moveAndWipeOriginal preserves metadata decrypts identically and deletes original file`() = runBlocking {
        val tempSourceDir = File(context.cacheDir, "import_move_src_${System.nanoTime()}").apply { mkdirs() }
        val originalPdf = File(tempSourceDir, "contract 2026.pdf")
        val payload = "%PDF-1.4\nConfidential document payload\n".encodeToByteArray() + ByteArray(70_000) { (it % 199).toByte() }
        originalPdf.writeBytes(payload)
        try {
            val result = VaultStorageManager.importUrisToVault(
                context = context,
                uris = listOf(Uri.fromFile(originalPdf)),
                targetFolderId = null,
                moveAndWipeOriginal = true
            )
            assertEquals(1, result.importedCount)
            assertTrue(result.errors.isEmpty())
            assertEquals(1, result.pendingDeleteHandles.size)

            val stored = database.vaultDao().getFilesInRootSync().single()
            assertEquals("contract 2026.pdf", stored.originalName)
            assertEquals("application/pdf", stored.mimeType)
            assertEquals(payload.size.toLong(), stored.originalSize)

            val decryptedOut = ByteArrayOutputStream()
            VaultCryptoEngine.decryptBlobFileToStream(
                masterKey = masterKey,
                blobFile = File(blobsDir, stored.blobFilename),
                outputStream = decryptedOut
            )
            assertArrayEquals(payload, decryptedOut.toByteArray())

            val outcome = VaultStorageManager.executeConfirmedOriginalDeletion(context, result.pendingDeleteHandles)
            assertEquals(1, outcome.deletedImmediatelyCount)
            assertEquals(0, outcome.remainingHandlesCount)
            assertFalse(originalPdf.exists())
        } finally {
            tempSourceDir.deleteRecursively()
        }
    }
}
