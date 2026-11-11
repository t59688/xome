package com.example.storage

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.R
import com.example.crypto.LocalPepperVault
import com.example.crypto.VaultCryptoEngine
import com.example.crypto.VaultSequencePolicy
import com.example.crypto.VaultSessionManager
import com.example.data.VaultDatabase
import com.example.data.VaultFileEntity
import com.example.data.VaultFolderEntity
import com.example.data.VaultSpaceMetaEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.io.OutputStream
import java.security.SecureRandom
import java.util.Arrays
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

object VaultStorageManager {

    const val VAULT_ROOT_DIR_NAME = ".sys_vault_data"
    private val secureRandom = SecureRandom()

    data class VaultDirectoryTree(
        val rootDir: File,
        val nomediaFile: File,
        val spacesDir: File,
        val dbDir: File,
        val blobsDir: File,
        val displayPath: String,
        val isPublicDocumentsDirect: Boolean
    )

    data class DeletableSourceHandle(
        val originalUri: Uri,
        val displayName: String,
        val mimeType: String,
        val size: Long,
        val resolvedMediaStoreUri: Uri?,
        val resolvedFilePath: String?
    )

    data class ImportResult(
        val importedCount: Int,
        val totalBytesEncrypted: Long,
        val pendingDeleteHandles: List<DeletableSourceHandle>,
        val errors: List<String>
    )

    data class SourceDeletionOutcome(
        val deletedImmediatelyCount: Int,
        val pendingSystemDeleteIntentSender: IntentSender?,
        val remainingHandlesCount: Int,
        val pendingHandles: List<DeletableSourceHandle> = emptyList(),
        val unresolvedHandles: List<DeletableSourceHandle> = emptyList()
    )

    fun resolveVaultTree(context: Context): VaultDirectoryTree {
        val publicDocsRoot = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            VAULT_ROOT_DIR_NAME
        )
        val canUsePublicDirect = try {
            if (!publicDocsRoot.exists()) {
                publicDocsRoot.mkdirs()
            }
            val probe = File(publicDocsRoot, ".nomedia")
            if (!probe.exists()) {
                probe.createNewFile()
            }
            probe.canRead() && publicDocsRoot.canWrite()
        } catch (_: Exception) {
            false
        }

        val effectiveRoot = if (canUsePublicDirect) {
            publicDocsRoot
        } else {
            val base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            File(base, VAULT_ROOT_DIR_NAME)
        }

        val spacesDir = File(effectiveRoot, "spaces").apply { mkdirs() }
        val dbDir = File(effectiveRoot, "db").apply { mkdirs() }
        val blobsDir = File(effectiveRoot, "blobs").apply { mkdirs() }
        val nomediaFile = File(effectiveRoot, ".nomedia")
        try {
            if (!nomediaFile.exists()) {
                nomediaFile.createNewFile()
            }
        } catch (_: Exception) {
        }

        return VaultDirectoryTree(
            rootDir = effectiveRoot,
            nomediaFile = nomediaFile,
            spacesDir = spacesDir,
            dbDir = dbDir,
            blobsDir = blobsDir,
            displayPath = "/sdcard/Documents/.sys_vault_data/",
            isPublicDocumentsDirect = canUsePublicDirect
        )
    }

    fun listSpaceContainerFiles(context: Context): List<File> {
        val tree = resolveVaultTree(context)
        return tree.spacesDir
            .listFiles { file ->
                file.isFile &&
                    file.extension.lowercase() == "dat" &&
                    file.length() == VaultCryptoEngine.CONTAINER_SIZE_BYTES.toLong()
            }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun listSealedSpaceContainerFiles(context: Context): List<File> =
        listSpaceContainerFiles(context).filter { LocalPepperVault.hasSeal(context, it.nameWithoutExtension) }

    fun countUnsealedSpaceContainers(context: Context): Int =
        listSpaceContainerFiles(context).count { !LocalPepperVault.hasSeal(context, it.nameWithoutExtension) }

    fun hasReadyLocalSpaces(context: Context): Boolean =
        listSealedSpaceContainerFiles(context).isNotEmpty()

    private fun generateRandomSpaceId(spacesDir: File): String {
        while (true) {
            val bytes = ByteArray(4)
            secureRandom.nextBytes(bytes)
            val hex = bytes.joinToString("") { "%02x".format(it) }
            if (!File(spacesDir, "$hex.dat").exists()) {
                return hex
            }
        }
    }

    suspend fun provisionNewSpace(
        context: Context,
        pinChars: CharArray,
        filePepper: ByteArray,
        spaceAlias: String,
        isDecoy: Boolean,
        seedSampleFiles: Boolean = true,
        activateImmediately: Boolean = true,
        profile: VaultCryptoEngine.Argon2Profile = VaultCryptoEngine.Argon2Profile.selectForDevice()
    ): VaultSessionManager.ActiveVaultSession = withContext(Dispatchers.IO) {
        require(VaultSequencePolicy.isValidNewSequence(java.nio.CharBuffer.wrap(pinChars))) {
            "Invalid new vault sequence"
        }
        require(filePepper.size == VaultCryptoEngine.KEYFILE_PEPPER_BYTES) {
            "Invalid Keyfile pepper"
        }
        val tree = resolveVaultTree(context)
        val spaceId = generateRandomSpaceId(tree.spacesDir)
        val containerFile = File(tree.spacesDir, "$spaceId.dat")
        val dbFile = File(tree.dbDir, "$spaceId.db")

        val masterKey = VaultCryptoEngine.randomBytes(32)
        try {
            val containerBytes = VaultCryptoEngine.createStealthContainerBytes(
                pinChars = pinChars,
                filePepper = filePepper,
                masterKey = masterKey,
                params = profile.toParams()
            )
            VaultCryptoEngine.writeAtomically(containerFile, containerBytes)

            try {
                LocalPepperVault.saveSeal(context, spaceId, profile, filePepper)
            } catch (e: Exception) {
                containerFile.delete()
                throw e
            }

            val containerInfo = VaultCryptoEngine.parseStealthContainer(containerFile)
                ?: throw IllegalStateException("Failed to verify newly created stealth container")

            val db = VaultDatabase.openSpaceDatabase(context, dbFile, masterKey)
            val dao = db.vaultDao()
            dao.upsertSpaceMeta(
                VaultSpaceMetaEntity(
                    spaceId = spaceId,
                    spaceAlias = spaceAlias,
                    isDecoy = isDecoy,
                    createdAt = System.currentTimeMillis()
                )
            )

            val photosFolderId = dao.insertFolder(
                VaultFolderEntity(
                    parentId = null,
                    folderName = if (isDecoy) "日常相册" else "私密相册"
                )
            )
            val docsFolderId = dao.insertFolder(
                VaultFolderEntity(
                    parentId = null,
                    folderName = if (isDecoy) "学习笔记" else "重要文档"
                )
            )
            dao.insertFolder(
                VaultFolderEntity(
                    parentId = null,
                    folderName = if (isDecoy) "票据归档" else "财务账单"
                )
            )

            if (seedSampleFiles) {
                seedEncryptedStarterAssets(
                    context = context,
                    tree = tree,
                    masterKey = masterKey,
                    dao = dao,
                    photosFolderId = photosFolderId,
                    docsFolderId = docsFolderId,
                    spaceId = spaceId,
                    isDecoy = isDecoy,
                    profile = profile
                )
            }

            val session = VaultSessionManager.ActiveVaultSession(
                spaceId = spaceId,
                containerFile = containerFile,
                containerInfo = containerInfo,
                kdfProfile = profile,
                rawMasterKey = masterKey.copyOf(),
                database = db
            )

            if (activateImmediately) {
                VaultSessionManager.activateSession(session)
            } else {
                session.destroy()
            }
            session
        } finally {
            Arrays.fill(masterKey, 0.toByte())
        }
    }

    suspend fun tryBlindUnlock(
        context: Context,
        rawSequence: String,
        canActivate: () -> Boolean = { true }
    ): VaultSessionManager.ActiveVaultSession? = withContext(Dispatchers.Default) {
        val trimmed = rawSequence.trim()
        if (!VaultSequencePolicy.isValidUnlockCandidate(trimmed)) return@withContext null

        val pinChars = trimmed.toCharArray()
        try {
            val tree = resolveVaultTree(context)
            val containerFiles = listSealedSpaceContainerFiles(context)
            for (containerFile in containerFiles) {
                currentCoroutineContext().ensureActive()
                if (!canActivate()) return@withContext null
                val info = VaultCryptoEngine.parseStealthContainer(containerFile) ?: continue
                val sealed = LocalPepperVault.loadSeal(context, info.spaceId) ?: continue
                val unlockedMk = try {
                    VaultCryptoEngine.tryUnlockStealthContainer(
                        containerInfo = info,
                        pinChars = pinChars,
                        filePepper = sealed.filePepper,
                        params = sealed.profile.toParams()
                    )
                } finally {
                    sealed.wipe()
                } ?: continue

                val session = activateUnlockedSession(
                    context = context,
                    tree = tree,
                    info = info,
                    profile = sealed.profile,
                    unlockedMk = unlockedMk,
                    canActivate = canActivate
                )
                if (session != null) return@withContext session
            }
            null
        } finally {
            Arrays.fill(pinChars, '\u0000')
        }
    }

    /**
     * Reinstall / New-Device Recovery: probes unsealed or all stealth containers using the
     * user-selected Keyfile pepper + unlock sequence, and seals the Keyfile pepper locally
     * into AndroidKeyStore upon success so future unlocks only need the sequence.
     */
    suspend fun recoverSpaceWithKeyfile(
        context: Context,
        rawSequence: String,
        filePepper: ByteArray,
        canActivate: () -> Boolean = { true }
    ): VaultSessionManager.ActiveVaultSession? = withContext(Dispatchers.Default) {
        val trimmed = rawSequence.trim()
        if (!VaultSequencePolicy.isValidUnlockCandidate(trimmed)) return@withContext null
        if (filePepper.size != VaultCryptoEngine.KEYFILE_PEPPER_BYTES) return@withContext null

        val pinChars = trimmed.toCharArray()
        try {
            val tree = resolveVaultTree(context)
            val containerFiles = listSpaceContainerFiles(context)
            for (containerFile in containerFiles) {
                currentCoroutineContext().ensureActive()
                if (!canActivate()) return@withContext null
                val info = VaultCryptoEngine.parseStealthContainer(containerFile) ?: continue
                val (unlockedMk, matchedProfile) = VaultCryptoEngine.tryRecoverStealthContainer(
                    containerInfo = info,
                    pinChars = pinChars,
                    filePepper = filePepper
                ) ?: continue

                LocalPepperVault.saveSeal(context, info.spaceId, matchedProfile, filePepper)
                val session = activateUnlockedSession(
                    context = context,
                    tree = tree,
                    info = info,
                    profile = matchedProfile,
                    unlockedMk = unlockedMk,
                    canActivate = canActivate
                )
                if (session != null) return@withContext session
            }
            null
        } finally {
            Arrays.fill(pinChars, '\u0000')
        }
    }

    private suspend fun activateUnlockedSession(
        context: Context,
        tree: VaultDirectoryTree,
        info: VaultCryptoEngine.SpaceContainerInfo,
        profile: VaultCryptoEngine.Argon2Profile,
        unlockedMk: ByteArray,
        canActivate: () -> Boolean
    ): VaultSessionManager.ActiveVaultSession? {
        var db: VaultDatabase? = null
        var activated = false
        try {
            currentCoroutineContext().ensureActive()
            if (!canActivate()) return null
            val dbFile = File(tree.dbDir, "${info.spaceId}.db")
            db = VaultDatabase.openSpaceDatabase(context, dbFile, unlockedMk)
            val dao = db.vaultDao()
            if (dao.getSpaceMetaSync() == null) {
                dao.upsertSpaceMeta(
                    VaultSpaceMetaEntity(
                        spaceId = info.spaceId,
                        spaceAlias = "私密空间 (${info.spaceId.take(4)})",
                        isDecoy = false
                    )
                )
            }
            currentCoroutineContext().ensureActive()
            if (!canActivate()) return null
            val session = VaultSessionManager.ActiveVaultSession(
                spaceId = info.spaceId,
                containerFile = info.containerFile,
                containerInfo = info,
                kdfProfile = profile,
                rawMasterKey = unlockedMk,
                database = db
            )
            VaultSessionManager.activateSession(session)
            activated = true
            return session
        } finally {
            if (!activated) {
                runCatching { db?.close() }
                Arrays.fill(unlockedMk, 0.toByte())
            }
        }
    }

    fun verifyActiveSpaceSequence(
        context: Context,
        session: VaultSessionManager.ActiveVaultSession,
        pinChars: CharArray
    ): Boolean {
        val info = VaultCryptoEngine.parseStealthContainer(session.containerFile) ?: return false
        val sealed = LocalPepperVault.loadSeal(context, session.spaceId) ?: return false
        val unwrapped = try {
            VaultCryptoEngine.tryUnlockStealthContainer(
                containerInfo = info,
                pinChars = pinChars,
                filePepper = sealed.filePepper,
                params = sealed.profile.toParams()
            )
        } finally {
            sealed.wipe()
        } ?: return false

        return try {
            val currentKey = session.borrowMasterKeyCopy()
            try {
                java.security.MessageDigest.isEqual(unwrapped, currentKey)
            } finally {
                Arrays.fill(currentKey, 0.toByte())
            }
        } finally {
            Arrays.fill(unwrapped, 0.toByte())
        }
    }

    fun changeSpaceSequenceZeroReencryption(
        context: Context,
        session: VaultSessionManager.ActiveVaultSession,
        oldPinChars: CharArray,
        newPinChars: CharArray
    ): Boolean {
        val sealed = LocalPepperVault.loadSeal(context, session.spaceId) ?: return false
        return try {
            val rotated = VaultCryptoEngine.rotateStealthContainerCredentials(
                containerFile = session.containerFile,
                oldPinChars = oldPinChars,
                oldPepper = sealed.filePepper,
                oldParams = sealed.profile.toParams(),
                newPinChars = newPinChars,
                newPepper = sealed.filePepper,
                newParams = sealed.profile.toParams()
            )
            rotated
        } finally {
            sealed.wipe()
        }
    }

    fun rotateSpaceKeyfileZeroReencryption(
        context: Context,
        session: VaultSessionManager.ActiveVaultSession,
        currentPinChars: CharArray,
        newFilePepper: ByteArray
    ): Boolean {
        val oldSeal = LocalPepperVault.loadSeal(context, session.spaceId) ?: return false
        val targetProfile = VaultCryptoEngine.Argon2Profile.selectForDevice()
        return try {
            val rotated = VaultCryptoEngine.rotateStealthContainerCredentials(
                containerFile = session.containerFile,
                oldPinChars = currentPinChars,
                oldPepper = oldSeal.filePepper,
                oldParams = oldSeal.profile.toParams(),
                newPinChars = currentPinChars,
                newPepper = newFilePepper,
                newParams = targetProfile.toParams()
            )
            if (rotated) {
                LocalPepperVault.saveSeal(context, session.spaceId, targetProfile, newFilePepper)
            }
            rotated
        } finally {
            oldSeal.wipe()
        }
    }

    suspend fun isSequenceAlreadyUsed(context: Context, sequence: String): Boolean =
        withContext(Dispatchers.Default) {
            val pinChars = sequence.toCharArray()
            try {
                for (containerFile in listSealedSpaceContainerFiles(context)) {
                    val info = VaultCryptoEngine.parseStealthContainer(containerFile) ?: continue
                    val sealed = LocalPepperVault.loadSeal(context, info.spaceId) ?: continue
                    val mk = try {
                        VaultCryptoEngine.tryUnlockStealthContainer(
                            containerInfo = info,
                            pinChars = pinChars,
                            filePepper = sealed.filePepper,
                            params = sealed.profile.toParams()
                        )
                    } finally {
                        sealed.wipe()
                    }
                    if (mk != null) {
                        Arrays.fill(mk, 0.toByte())
                        return@withContext true
                    }
                }
                false
            } finally {
                Arrays.fill(pinChars, '\u0000')
            }
        }

    /**
     * Module 4 Streaming File Import:
     * Encrypts all selected URIs into 64KB AES-256-GCM `.dat` files, `fsync`s them,
     * and commits their metadata into SQLCipher.
     * If `moveAndWipeOriginal` is true, collects `DeletableSourceHandle`s so the UI can
     * show the final confirmation prompt and reliably delete the source files.
     */
    suspend fun importUrisToVault(
        context: Context,
        uris: List<Uri>,
        targetFolderId: Long?,
        moveAndWipeOriginal: Boolean,
        onProgress: (current: Int, total: Int, fileName: String) -> Unit = { _, _, _ -> }
    ): ImportResult = withContext(Dispatchers.IO) {
        val session = VaultSessionManager.activeSession.value
            ?: return@withContext ImportResult(0, 0L, emptyList(), listOf("Session locked"))

        val masterKey = session.borrowMasterKeyCopy()
        val tree = resolveVaultTree(context)
        val resolver = context.contentResolver
        val dao = session.database.vaultDao()

        var importedCount = 0
        var totalBytes = 0L
        val pendingHandles = mutableListOf<DeletableSourceHandle>()
        val errors = mutableListOf<String>()

        try {
            uris.forEachIndexed { index, uri ->
                val meta = queryUriMetadata(resolver, uri)
                onProgress(index + 1, uris.size, meta.displayName)

                val blobFilename = "${UUID.randomUUID().toString().replace("-", "")}.dat"
                val targetBlobFile = File(tree.blobsDir, blobFilename)

                try {
                    val encryptedThumb = extractEncryptedThumbnailInMemory(
                        context = context,
                        uri = uri,
                        mimeType = meta.mimeType,
                        masterKey = masterKey
                    )

                    val bytesWritten = resolver.openInputStream(uri)?.use { input ->
                        VaultCryptoEngine.encryptStreamToBlobFile(
                            masterKey = masterKey,
                            inputStream = input,
                            targetBlobFile = targetBlobFile
                        )
                    } ?: throw IllegalStateException("Cannot open stream for ${meta.displayName}")

                    val finalSize = if (bytesWritten > 0) bytesWritten else meta.size
                    dao.insertFile(
                        VaultFileEntity(
                            folderId = targetFolderId,
                            originalName = meta.displayName,
                            mimeType = meta.mimeType,
                            originalSize = finalSize,
                            blobFilename = blobFilename,
                            encryptedThumbnail = encryptedThumb,
                            importedAt = System.currentTimeMillis(),
                            originalUriPath = uri.toString()
                        )
                    )

                    importedCount++
                    totalBytes += bytesWritten

                    if (moveAndWipeOriginal) {
                        val handle = resolveDeletableHandle(
                            context = context,
                            uri = uri,
                            displayName = meta.displayName,
                            mimeType = meta.mimeType,
                            size = finalSize
                        )
                        pendingHandles.add(handle)
                    }
                } catch (e: Exception) {
                    if (targetBlobFile.exists()) {
                        targetBlobFile.delete()
                    }
                    errors.add("${meta.displayName}: ${e.message ?: "Encryption error"}")
                }
            }
        } finally {
            Arrays.fill(masterKey, 0.toByte())
        }

        ImportResult(
            importedCount = importedCount,
            totalBytesEncrypted = totalBytes,
            pendingDeleteHandles = pendingHandles,
            errors = errors
        )
    }

    /**
     * Only the provider may map a document to MediaStore. Names, sizes, picker IDs
     * and filesystem paths are not source identities.
     */
    internal fun resolveDeletableHandle(
        context: Context,
        uri: Uri,
        displayName: String,
        mimeType: String,
        size: Long
    ): DeletableSourceHandle = DeletableSourceHandle(
        originalUri = uri,
        displayName = displayName,
        mimeType = mimeType,
        size = size,
        resolvedMediaStoreUri = exactMediaUri(context, uri),
        resolvedFilePath = null
    )

    private fun exactMediaUri(context: Context, uri: Uri): Uri? {
        if (uri.authority == MediaStore.AUTHORITY &&
            uri.pathSegments.let { it.size == 4 && it[1] in setOf("images", "video", "audio", "file") &&
                it[2] == "media" && it[3].toLongOrNull() != null }
        ) return uri
        // Files URIs use /volume/file/id, without a /media segment.
        if (uri.authority == MediaStore.AUTHORITY && uri.pathSegments.let {
                it.size == 3 && it[1] == "file" && it[2].toLongOrNull() != null
            }) return uri
        if (!DocumentsContract.isDocumentUri(context, uri)) return null
        return try {
            MediaStore.getMediaUri(context, uri)?.takeIf { it.authority == MediaStore.AUTHORITY }
        } catch (_: Exception) {
            null
        }
    }

    internal fun isSameOriginal(context: Context, handle: DeletableSourceHandle, selected: Uri): Boolean {
        if (selected == handle.originalUri) return true
        if (DocumentsContract.isDocumentUri(context, selected) &&
            DocumentsContract.isDocumentUri(context, handle.originalUri) &&
            selected.authority == handle.originalUri.authority &&
            DocumentsContract.getDocumentId(selected) == DocumentsContract.getDocumentId(handle.originalUri)
        ) return true
        val originalMedia = exactMediaUri(context, handle.originalUri) ?: return false
        return exactMediaUri(context, selected) == originalMedia
    }

    fun canReselectOriginal(context: Context, handle: DeletableSourceHandle): Boolean =
        DocumentsContract.isDocumentUri(context, handle.originalUri) ||
            exactMediaUri(context, handle.originalUri) != null

    // Permission loss, null cursors and provider errors are unknown, never proof of deletion.
    internal fun isSourceAbsent(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { !it.moveToFirst() } ?: false
    } catch (_: java.io.FileNotFoundException) {
        // A document provider may report absence this way, but a revoked grant
        // must never be mistaken for absence. Cloud/network errors stay unknown.
        uri.authority in setOf("com.android.externalstorage.documents", "com.android.providers.media.documents") &&
            DocumentsContract.isDocumentUri(context, uri) &&
            context.checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }

    suspend fun verifyOriginalDeletion(
        context: Context,
        handles: List<DeletableSourceHandle>
    ): SourceDeletionOutcome = withContext(Dispatchers.IO) {
        val remaining = handles.distinctBy { it.originalUri }.filterNot {
            isSourceAbsent(context, it.resolvedMediaStoreUri ?: it.originalUri)
        }
        SourceDeletionOutcome(
            handles.distinctBy { it.originalUri }.size - remaining.size, null,
            remaining.size, unresolvedHandles = remaining
        )
    }

    /**
     * Deletes exact imported sources only. Android 10 consent grants a retry;
     * Android 11+ consent performs deletion and must subsequently be verified.
     */
    suspend fun executeConfirmedOriginalDeletion(
        context: Context,
        handles: List<DeletableSourceHandle>
    ): SourceDeletionOutcome = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var deleted = 0
        val remaining = mutableListOf<DeletableSourceHandle>()
        val mediaPending = mutableListOf<DeletableSourceHandle>()
        var recovery: IntentSender? = null
        val recoveryHandles = mutableListOf<DeletableSourceHandle>()
        for (handle in handles.distinctBy { it.originalUri }) {
            val mediaUri = exactMediaUri(context, handle.originalUri)
            val exact = handle.copy(resolvedMediaStoreUri = mediaUri, resolvedFilePath = null)
            val targets = listOfNotNull(handle.originalUri, mediaUri).distinct()
            var completed = false
            var consent: IntentSender? = null
            for (target in targets) {
                try {
                    val accepted = if (DocumentsContract.isDocumentUri(context, target)) {
                        DocumentsContract.deleteDocument(resolver, target)
                    } else {
                        resolver.delete(target, null, null) > 0
                    }
                    if (accepted && isSourceAbsent(context, target)) {
                        completed = true
                        break
                    }
                } catch (e: RecoverableSecurityException) {
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                        consent = e.userAction.actionIntent.intentSender
                    }
                } catch (_: Exception) {
                    // Retain this exact handle for consent or SAF recovery.
                }
            }
            if (completed) {
                deleted++
            } else if (consent != null && recovery == null) {
                recovery = consent
                recoveryHandles.add(exact)
            } else if (mediaUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                mediaPending.add(exact)
            } else {
                remaining.add(exact)
            }
        }
        if (mediaPending.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                recovery = MediaStore.createDeleteRequest(
                    resolver, mediaPending.mapNotNull { it.resolvedMediaStoreUri }.distinct()
                ).intentSender
                recoveryHandles.addAll(mediaPending)
            } catch (_: Exception) {
                remaining.addAll(mediaPending)
            }
        }
        SourceDeletionOutcome(deleted, recovery, remaining.size + recoveryHandles.size,
            recoveryHandles, remaining)
    }

    suspend fun createEncryptedNoteInVault(
        context: Context,
        folderId: Long?,
        title: String,
        content: String
    ): VaultFileEntity? = withContext(Dispatchers.IO) {
        val session = VaultSessionManager.activeSession.value ?: return@withContext null
        val masterKey = session.borrowMasterKeyCopy()
        val tree = resolveVaultTree(context)
        val dao = session.database.vaultDao()

        try {
            val cleanName = if (title.endsWith(".md") || title.endsWith(".txt")) title else "$title.md"
            val blobFilename = "${UUID.randomUUID().toString().replace("-", "")}.dat"
            val blobFile = File(tree.blobsDir, blobFilename)
            val plainBytes = content.toByteArray(Charsets.UTF_8)

            ByteArrayInputStream(plainBytes).use { bais ->
                VaultCryptoEngine.encryptStreamToBlobFile(
                    masterKey = masterKey,
                    inputStream = bais,
                    targetBlobFile = blobFile
                )
            }

            val entity = VaultFileEntity(
                folderId = folderId,
                originalName = cleanName,
                mimeType = "text/markdown",
                originalSize = plainBytes.size.toLong(),
                blobFilename = blobFilename,
                encryptedThumbnail = null,
                importedAt = System.currentTimeMillis(),
                originalUriPath = "in-memory://authored-note"
            )
            val id = dao.insertFile(entity)
            entity.copy(id = id)
        } finally {
            Arrays.fill(masterKey, 0.toByte())
        }
    }

    suspend fun exportEncryptedFileToPublicDownloads(
        context: Context,
        fileEntity: VaultFileEntity
    ): Result<String> = withContext(Dispatchers.IO) {
        val session = VaultSessionManager.activeSession.value
            ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val masterKey = try {
            session.borrowMasterKeyCopy()
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
        try {
            val tree = resolveVaultTree(context)
            val blobFile = File(tree.blobsDir, fileEntity.blobFilename)
            if (!blobFile.exists()) {
                return@withContext Result.failure(IllegalStateException("Encrypted blob file missing"))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileEntity.originalName)
                    put(MediaStore.Downloads.MIME_TYPE, fileEntity.mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/XomeRestored")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val itemUri = resolver.insert(collection, contentValues)
                    ?: throw IOException("Unable to create a Downloads entry")
                try {
                    resolver.openOutputStream(itemUri, "w")?.use { out ->
                        VaultCryptoEngine.decryptBlobFileToStream(masterKey, blobFile, out)
                    } ?: throw IOException("Unable to open the Downloads file for writing")

                    val publishValues = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    if (resolver.update(itemUri, publishValues, null, null) <= 0) {
                        throw IOException("Unable to publish exported file in Downloads")
                    }
                    // MediaStore can rename duplicate names; report the actual saved name.
                    val publishedName = runCatching {
                        resolver.query(
                            itemUri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                    }.getOrNull() ?: fileEntity.originalName
                    return@withContext Result.success("Download/XomeRestored/$publishedName")
                } catch (e: Exception) {
                    // Clean only the row created by this export; surface cleanup failure.
                    try {
                        if (resolver.delete(itemUri, null, null) <= 0 && !isSourceAbsent(context, itemUri)) {
                            throw IOException("Partial plaintext export could not be removed")
                        }
                    } catch (cleanup: Exception) {
                        throw IOException("Export failed; partial plaintext cleanup could not be confirmed", e)
                            .apply { addSuppressed(cleanup) }
                    }
                    throw e
                }
            }

            val fallbackDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                "XomeRestored"
            ).apply { mkdirs() }
            val outFile = File.createTempFile("restored-", "." + File(fileEntity.originalName).extension, fallbackDir)
            try {
                FileOutputStream(outFile).use { fos ->
                    VaultCryptoEngine.decryptBlobFileToStream(masterKey, blobFile, fos)
                }
            } catch (e: Exception) {
                if (!outFile.delete()) {
                    throw IOException("Export failed; partial plaintext cleanup could not be confirmed", e)
                }
                throw e
            }
            Result.success(outFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            Arrays.fill(masterKey, 0.toByte())
        }
    }

    suspend fun shredVaultFile(context: Context, fileEntity: VaultFileEntity) =
        withContext(Dispatchers.IO) {
            val session = VaultSessionManager.activeSession.value ?: return@withContext
            val tree = resolveVaultTree(context)
            val blobFile = File(tree.blobsDir, fileEntity.blobFilename)
            if (blobFile.exists()) {
                try {
                    val shredLen = blobFile.length().coerceAtMost(4096L).toInt()
                    if (shredLen > 0) {
                        val noise = VaultCryptoEngine.randomBytes(shredLen)
                        java.io.RandomAccessFile(blobFile, "rw").use { raf ->
                            raf.seek(0L)
                            raf.write(noise)
                            raf.fd.sync()
                        }
                    }
                } catch (_: Exception) {
                }
                blobFile.delete()
            }
            session.database.vaultDao().deleteFileById(fileEntity.id)
        }

    suspend fun exportVaultSelfHealingBundle(
        context: Context,
        outputStream: OutputStream
    ): Int = withContext(Dispatchers.IO) {
        val tree = resolveVaultTree(context)
        var entriesWritten = 0
        ZipOutputStream(outputStream).use { zos ->
            listOf("spaces" to tree.spacesDir, "db" to tree.dbDir, "blobs" to tree.blobsDir).forEach { (prefix, dir) ->
                dir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val entry = ZipEntry("$prefix/${file.name}")
                        zos.putNextEntry(entry)
                        FileInputStream(file).use { fis -> fis.copyTo(zos) }
                        zos.closeEntry()
                        entriesWritten++
                    }
                }
            }
        }
        entriesWritten
    }

    suspend fun importVaultSelfHealingBundle(
        context: Context,
        inputStream: InputStream
    ): Int = withContext(Dispatchers.IO) {
        val tree = resolveVaultTree(context)
        var filesRestored = 0
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val safeName = File(entry.name).name
                    if (safeName.isNotEmpty() && safeName != "." && safeName != "..") {
                        val name = entry.name
                        val targetFile = when {
                            name.startsWith("spaces/") -> File(tree.spacesDir, safeName)
                            name.startsWith("db/") -> File(tree.dbDir, safeName)
                            name.startsWith("blobs/") -> File(tree.blobsDir, safeName)
                            else -> null
                        }
                        if (targetFile != null) {
                            FileOutputStream(targetFile).use { fos ->
                                zis.copyTo(fos)
                                fos.flush()
                                fos.fd.sync()
                            }
                            filesRestored++
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        filesRestored
    }

    private suspend fun seedEncryptedStarterAssets(
        context: Context,
        tree: VaultDirectoryTree,
        masterKey: ByteArray,
        dao: com.example.data.VaultDao,
        photosFolderId: Long,
        docsFolderId: Long,
        spaceId: String,
        isDecoy: Boolean,
        profile: VaultCryptoEngine.Argon2Profile
    ) {
        try {
            val bannerBitmap = BitmapFactory.decodeResource(
                context.resources,
                R.drawable.img_vault_banner_1794279611736
            )
            if (bannerBitmap != null) {
                val fullImageBytes = ByteArrayOutputStream().use { baos ->
                    bannerBitmap.compress(Bitmap.CompressFormat.JPEG, 94, baos)
                    baos.toByteArray()
                }
                val thumbBitmap = Bitmap.createScaledBitmap(bannerBitmap, 200, 112, true)
                val thumbBytes = ByteArrayOutputStream().use { baos ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        thumbBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, baos)
                    } else {
                        @Suppress("DEPRECATION")
                        thumbBitmap.compress(Bitmap.CompressFormat.WEBP, 80, baos)
                    }
                    baos.toByteArray()
                }
                val encryptedThumb = VaultCryptoEngine.encryptThumbnail(masterKey, thumbBytes)
                val blobName = "${UUID.randomUUID().toString().replace("-", "")}.dat"
                val blobFile = File(tree.blobsDir, blobName)

                ByteArrayInputStream(fullImageBytes).use { bais ->
                    VaultCryptoEngine.encryptStreamToBlobFile(masterKey, bais, blobFile)
                }

                dao.insertFile(
                    VaultFileEntity(
                        folderId = null,
                        originalName = if (isDecoy) "Wallpaper_Cover.jpg" else "Vault_Architecture.jpg",
                        mimeType = "image/jpeg",
                        originalSize = fullImageBytes.size.toLong(),
                        blobFilename = blobName,
                        encryptedThumbnail = encryptedThumb,
                        importedAt = System.currentTimeMillis() - 60_000L,
                        originalUriPath = "vault://seed/topology"
                    )
                )

                val calcBitmap = BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.img_launcher_calc_1794279609341
                )
                if (calcBitmap != null) {
                    val calcBytes = ByteArrayOutputStream().use { baos ->
                        calcBitmap.compress(Bitmap.CompressFormat.JPEG, 92, baos)
                        baos.toByteArray()
                    }
                    val calcThumb = Bitmap.createScaledBitmap(calcBitmap, 200, 200, true)
                    val calcThumbBytes = ByteArrayOutputStream().use { baos ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            calcThumb.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, baos)
                        } else {
                            @Suppress("DEPRECATION")
                            calcThumb.compress(Bitmap.CompressFormat.WEBP, 80, baos)
                        }
                        baos.toByteArray()
                    }
                    val blobName2 = "${UUID.randomUUID().toString().replace("-", "")}.dat"
                    val blobFile2 = File(tree.blobsDir, blobName2)
                    ByteArrayInputStream(calcBytes).use { bais ->
                        VaultCryptoEngine.encryptStreamToBlobFile(masterKey, bais, blobFile2)
                    }
                    dao.insertFile(
                        VaultFileEntity(
                            folderId = photosFolderId,
                            originalName = "Design_Preview.jpg",
                            mimeType = "image/jpeg",
                            originalSize = calcBytes.size.toLong(),
                            blobFilename = blobName2,
                            encryptedThumbnail = VaultCryptoEngine.encryptThumbnail(masterKey, calcThumbBytes),
                            importedAt = System.currentTimeMillis() - 120_000L,
                            originalUriPath = "vault://seed/schematic"
                        )
                    )
                }
            }

            val specContent = """
                # 空间安全说明 ($spaceId)
                - 空间容器: `spaces/$spaceId.dat` (8,208 B · 64 诱饵槽位 · 无魔数)
                - 双因子密钥: `256-bit 原文件摘要 (零指纹) + 计算序列`
                - 本机便利封印: `AndroidKeyStore (StrongBox/TEE)` 加密存放在应用私有区
                - 密钥派生: `Argon2id (${profile.memoryCostKb / 1024} MiB, t=${profile.timeCost}, p=${profile.parallelism}) + HMAC-SHA256`
                - 文件加密: `无魔数 64KB AES-256-GCM 分块流式加密`
                - 数据库: `SQLCipher (db/$spaceId.db)`
                
                注意：请妥善保留创建空间时所选原文件的副本（如电脑或网盘）；卸载重装或更换设备后，需要重新选择同一原文件与计算序列即可恢复。
            """.trimIndent()
            val specBytes = specContent.toByteArray(Charsets.UTF_8)
            val specBlobName = "${UUID.randomUUID().toString().replace("-", "")}.dat"
            val specBlobFile = File(tree.blobsDir, specBlobName)
            ByteArrayInputStream(specBytes).use { bais ->
                VaultCryptoEngine.encryptStreamToBlobFile(masterKey, bais, specBlobFile)
            }
            dao.insertFile(
                VaultFileEntity(
                    folderId = null,
                    originalName = "README_${spaceId.uppercase()}.md",
                    mimeType = "text/markdown",
                    originalSize = specBytes.size.toLong(),
                    blobFilename = specBlobName,
                    encryptedThumbnail = null,
                    importedAt = System.currentTimeMillis(),
                    originalUriPath = "vault://seed/manifest"
                )
            )
            dao.insertFile(
                VaultFileEntity(
                    folderId = docsFolderId,
                    originalName = "Security_Notes.md",
                    mimeType = "text/markdown",
                    originalSize = specBytes.size.toLong(),
                    blobFilename = specBlobName,
                    encryptedThumbnail = null,
                    importedAt = System.currentTimeMillis(),
                    originalUriPath = "vault://seed/protocol"
                )
            )
        } catch (_: Exception) {
        }
    }

    private data class UriMeta(
        val displayName: String,
        val mimeType: String,
        val size: Long
    )

    private fun queryUriMetadata(resolver: ContentResolver, uri: Uri): UriMeta {
        var name = "file_${System.currentTimeMillis()}"
        var size = 0L
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                            name = cursor.getString(nameIdx)
                        }
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            size = cursor.getLong(sizeIdx)
                        }
                    }
                }
        } catch (_: Exception) {
        }
        val mime = resolver.getType(uri) ?: guessMimeTypeFromName(name)
        return UriMeta(displayName = name, mimeType = mime, size = size)
    }

    private fun guessMimeTypeFromName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4", "mkv", "mov" -> "video/mp4"
            "pdf" -> "application/pdf"
            "xls", "xlsx", "csv" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "zip", "rar", "7z" -> "application/zip"
            "md", "txt", "json", "log" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private fun extractEncryptedThumbnailInMemory(
        context: Context,
        uri: Uri,
        mimeType: String,
        masterKey: ByteArray
    ): ByteArray? {
        return try {
            val bitmap: Bitmap? = when {
                mimeType.startsWith("image/") -> {
                    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, boundsOpts)
                    }
                    val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
                    var sampleSize = 1
                    while (maxDim / sampleSize > 240) {
                        sampleSize *= 2
                    }
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, decodeOpts)
                    }
                }
                mimeType.startsWith("video/") -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally {
                        retriever.release()
                    }
                }
                else -> null
            }

            if (bitmap != null) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
                val targetW = if (ratio >= 1f) 200 else (200 * ratio).toInt().coerceAtLeast(60)
                val targetH = if (ratio >= 1f) (200 / ratio).toInt().coerceAtLeast(60) else 200
                val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                val webpBytes = ByteArrayOutputStream().use { baos ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, baos)
                    } else {
                        @Suppress("DEPRECATION")
                        scaled.compress(Bitmap.CompressFormat.WEBP, 80, baos)
                    }
                    baos.toByteArray()
                }
                VaultCryptoEngine.encryptThumbnail(masterKey, webpBytes)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
