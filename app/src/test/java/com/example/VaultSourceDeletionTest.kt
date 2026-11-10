package com.example

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.app.RemoteAction
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.drawable.Icon
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.storage.VaultStorageManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
