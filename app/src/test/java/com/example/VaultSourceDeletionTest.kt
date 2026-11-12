package com.example

import android.Manifest
import android.app.Application
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.app.RemoteAction
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import com.example.storage.AllFilesPermissionHelper
import com.example.storage.VaultStorageManager
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class VaultSourceDeletionTest {
    private lateinit var context: Context
    private lateinit var provider: Sources
    private val first = Uri.parse("content://fixtures/original/1")
    private val other = Uri.parse("content://fixtures/original/2")

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        provider = Sources()
        provider.attachInfo(context, android.content.pm.ProviderInfo().apply { authority = "fixtures" })
        ShadowContentResolver.registerProviderInternal("fixtures", provider)
        provider.present.addAll(listOf(first, other))
    }

    private fun handle(uri: Uri) = VaultStorageManager.resolveDeletableHandle(
        context, uri, "duplicate.jpg", "image/jpeg", 42
    )

    @Test fun sameNamesNeverResolveAnotherMediaItem() {
        val handle = handle(first)
        assertNull(handle.resolvedMediaStoreUri)
        assertNull(handle.resolvedFilePath)
        assertFalse(VaultStorageManager.isSameOriginal(context, handle, other))
        assertTrue(provider.queries.isEmpty())
    }

    @Test fun pickerNumericIdIsNotAMediaStoreIdentity() {
        val picker = handle(Uri.parse("content://media/picker/0/cloud/media/123"))
        assertNull(picker.resolvedMediaStoreUri)
        assertFalse(VaultStorageManager.canReselectOriginal(context, picker))
    }

    @Test fun deletesOnlyExactSourceAndDeduplicates() = runBlocking {
        val result = VaultStorageManager.executeConfirmedOriginalDeletion(context, listOf(handle(first), handle(first)))
        assertEquals(1, result.deletedImmediatelyCount)
        assertEquals(0, result.remainingHandlesCount)
        assertEquals(listOf(first), provider.deletes)
        assertTrue(provider.present.contains(other))
    }

    @Test fun providerReportingSuccessWithoutDeletionStaysUnresolved() = runBlocking {
        provider.keepOnDelete = true
        val result = VaultStorageManager.executeConfirmedOriginalDeletion(context, listOf(handle(first)))
        assertEquals(0, result.deletedImmediatelyCount)
        assertEquals(listOf(first), result.unresolvedHandles.map { it.originalUri })
        assertEquals(1, result.remainingHandlesCount)
    }

    @Test @Config(sdk = [36])
    fun verifiesEachItemAfterConsentInsteadOfAssumingWholeBatch() = runBlocking {
        provider.present.remove(first)
        val result = VaultStorageManager.verifyOriginalDeletion(context, listOf(handle(first), handle(other)))
        assertEquals(1, result.deletedImmediatelyCount)
        assertEquals(listOf(other), result.unresolvedHandles.map { it.originalUri })
    }

    @Test fun lostPermissionIsNotProofOfDeletion() = runBlocking {
        provider.queryDenied = true
        val result = VaultStorageManager.verifyOriginalDeletion(context, listOf(handle(first)))
        assertEquals(0, result.deletedImmediatelyCount)
        assertEquals(1, result.remainingHandlesCount)
    }

    @Test fun nullCursorIsNotProofOfDeletion() = runBlocking {
        provider.nullCursor = true
        val result = VaultStorageManager.verifyOriginalDeletion(context, listOf(handle(first)))
        assertEquals(0, result.deletedImmediatelyCount)
        assertEquals(1, result.remainingHandlesCount)
    }

    @Test fun partialProviderErrorRetainsOnlyUnverifiedItem() = runBlocking {
        provider.present.remove(first)
        provider.deniedUris.add(other)
        val result = VaultStorageManager.verifyOriginalDeletion(context, listOf(handle(first), handle(other)))
        assertEquals(1, result.deletedImmediatelyCount)
        assertEquals(listOf(other), result.unresolvedHandles.map { it.originalUri })
    }

    @Test fun fileNotFoundWithoutDocumentGrantRemainsUnconfirmed() = runBlocking {
        provider.fileNotFound = true
        val result = VaultStorageManager.verifyOriginalDeletion(context, listOf(handle(first)))
        assertEquals(0, result.deletedImmediatelyCount)
        assertEquals(1, result.remainingHandlesCount)
    }

    @Test fun android10RecoveryRequestsConsentThenRetriesExactItem() = runBlocking {
        val action = PendingIntent.getActivity(context, 0, Intent("fixture.consent"), PendingIntent.FLAG_IMMUTABLE)
        provider.recovery = RecoverableSecurityException(SecurityException("fixture"), "Grant delete",
            RemoteAction(Icon.createWithResource(context, android.R.drawable.ic_delete), "Delete", "Delete", action))
        val result = VaultStorageManager.executeConfirmedOriginalDeletion(context, listOf(handle(first), handle(other)))
        assertNotNull(result.pendingSystemDeleteIntentSender)
        assertEquals(0, result.deletedImmediatelyCount)
        assertEquals(2, result.remainingHandlesCount)
        assertEquals(listOf(first), result.pendingHandles.map { it.originalUri })
        assertEquals(listOf(other), result.unresolvedHandles.map { it.originalUri })
        provider.recovery = null
        val retry = VaultStorageManager.executeConfirmedOriginalDeletion(context, result.pendingHandles)
        assertEquals(1, retry.deletedImmediatelyCount)
        assertTrue(provider.present.contains(other))
    }

    @Test fun directLocalFileUriResolvesPathAndDeletesOnlySelectedFile() = runBlocking {
        val tempDir = File(context.cacheDir, "deletion_local_test_${System.nanoTime()}").apply { mkdirs() }
        try {
            val target = File(tempDir, "secret.pdf").apply { writeBytes(ByteArray(8192) { 0x55 }) }
            val sibling = File(tempDir, "keep_me.pdf").apply { writeText("keep") }
            val targetUri = Uri.fromFile(target)
            val resolvedHandle = VaultStorageManager.resolveDeletableHandle(
                context, targetUri, target.name, "application/pdf", target.length()
            )
            assertEquals(target.canonicalPath, resolvedHandle.resolvedFilePath)

            val outcome = VaultStorageManager.executeConfirmedOriginalDeletion(context, listOf(resolvedHandle))
            assertEquals(1, outcome.deletedImmediatelyCount)
            assertEquals(0, outcome.remainingHandlesCount)
            assertFalse(target.exists())
            assertTrue(sibling.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test @Config(sdk = [36])
    fun allFilesPermissionHelperAndDownloadsRawUriDeletionWorkOnAndroid11Plus() = runBlocking {
        AllFilesPermissionHelper.testAllFilesAccessOverride = false
        assertFalse(AllFilesPermissionHelper.hasAllFilesAccess(context))

        AllFilesPermissionHelper.testAllFilesAccessOverride = true
        assertTrue(AllFilesPermissionHelper.hasAllFilesAccess(context))

        val tempDir = File(context.cacheDir, "downloads_raw_test_${System.nanoTime()}").apply { mkdirs() }
        try {
            val pdf = File(tempDir, "report 1.pdf").apply { writeText("top secret pdf content") }
            val docsProvider = Sources()
            docsProvider.attachInfo(context, ProviderInfo().apply {
                authority = "com.android.providers.downloads.documents"
            })
            ShadowContentResolver.registerProviderInternal(
                "com.android.providers.downloads.documents",
                docsProvider
            )
            val docUri = DocumentsContract.buildDocumentUri(
                "com.android.providers.downloads.documents",
                "raw:${pdf.absolutePath}"
            )
            val handle = VaultStorageManager.resolveDeletableHandle(
                context, docUri, pdf.name, "application/pdf", pdf.length()
            )
            assertEquals(pdf.canonicalPath, handle.resolvedFilePath)

            val outcome = VaultStorageManager.executeConfirmedOriginalDeletion(context, listOf(handle))
            assertEquals(1, outcome.deletedImmediatelyCount)
            assertFalse(pdf.exists())
        } finally {
            AllFilesPermissionHelper.testAllFilesAccessOverride = null
            tempDir.deleteRecursively()
        }
    }

    @Test @Config(sdk = [36])
    fun externalStoragePrimaryDocumentUriResolvesExactFile() = runBlocking {
        AllFilesPermissionHelper.testAllFilesAccessOverride = true
        val downloadDir = File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
        val target = File(downloadDir, "video_test_${System.nanoTime()}.mp4").apply { writeBytes(ByteArray(2048) { 0x1A }) }
        try {
            val relPath = "${Environment.DIRECTORY_DOWNLOADS}/${target.name}"
            val docUri = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:$relPath"
            )
            val handle = VaultStorageManager.resolveDeletableHandle(
                context, docUri, target.name, "video/mp4", target.length()
            )
            assertEquals(target.canonicalPath, handle.resolvedFilePath)

            val outcome = VaultStorageManager.executeConfirmedOriginalDeletion(context, listOf(handle))
            assertEquals(1, outcome.deletedImmediatelyCount)
            assertFalse(target.exists())
        } finally {
            AllFilesPermissionHelper.testAllFilesAccessOverride = null
            target.delete()
        }
    }

    @Test fun android10AllFilesAccessChecksReadAndWritePermissions() {
        val app = context as Application
        Shadows.shadowOf(app).denyPermissions(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        assertFalse(AllFilesPermissionHelper.hasAllFilesAccess(context))

        Shadows.shadowOf(app).grantPermissions(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        assertTrue(AllFilesPermissionHelper.hasAllFilesAccess(context))
    }

    @Test fun protectedVaultDirectoryIsNeverResolvedOrListedForDeletion() = runBlocking {
        val tree = VaultStorageManager.resolveVaultTree(context)
        val internalBlob = File(tree.blobsDir, "keep_blob.dat").apply { writeText("vault-blob") }
        val externalDir = File(context.cacheDir, "browse_mix_${System.nanoTime()}").apply { mkdirs() }
        val userDoc = File(externalDir, "notes.txt").apply { writeText("ok") }
        val nestedVaultDir = File(externalDir, VaultStorageManager.VAULT_ROOT_DIR_NAME).apply { mkdirs() }
        File(nestedVaultDir, "hidden.dat").apply { writeText("skip") }
        try {
            val protectedUri = Uri.fromFile(internalBlob)
            val handle = VaultStorageManager.resolveDeletableHandle(
                context, protectedUri, internalBlob.name, "application/octet-stream", internalBlob.length()
            )
            assertNull(handle.resolvedFilePath)

            val outcome = VaultStorageManager.executeConfirmedOriginalDeletion(context, listOf(handle))
            assertEquals(0, outcome.deletedImmediatelyCount)
            assertTrue(internalBlob.exists())

            val entries = AllFilesPermissionHelper.listDirectoryEntries(
                externalDir,
                VaultStorageManager.protectedVaultPaths(context)
            )
            assertEquals(listOf("notes.txt"), entries.map { it.name })
        } finally {
            internalBlob.delete()
            externalDir.deleteRecursively()
        }
    }

    class Sources : ContentProvider() {
        val present = mutableSetOf<Uri>()
        val deletes = mutableListOf<Uri>()
        val queries = mutableListOf<Uri>()
        var keepOnDelete = false
        var queryDenied = false
        var nullCursor = false
        var fileNotFound = false
        val deniedUris = mutableSetOf<Uri>()
        var recovery: RecoverableSecurityException? = null
        override fun onCreate() = true
        override fun getType(uri: Uri) = "image/jpeg"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            queries.add(uri)
            if (queryDenied || uri in deniedUris) throw SecurityException("fixture permission revoked")
            if (fileNotFound) throw java.io.FileNotFoundException("fixture missing")
            if (nullCursor) return null
            return MatrixCursor(arrayOf("_id")).apply {
                if (uri in present) addRow(arrayOf(1))
            }
        }
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            deletes.add(uri)
            recovery?.let { throw it }
            if (keepOnDelete) return 1
            return if (present.remove(uri)) 1 else 0
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
