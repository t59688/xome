package com.example.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.R
import com.example.crypto.VaultCryptoEngine
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
import java.io.OutputStream
import java.security.SecureRandom
import java.util.Arrays
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages the physical vault directory structure:
 *
 * /sdcard/Documents/.sys_vault_data/ (with automatic Scoped Storage fallback + SAF Self-Healing Archive)
 *  ├── .nomedia
 *  ├── spaces/
 *  │    ├── 8f3a1b9c.header (128 Bytes)
 *  │    └── 4d2e7a1f.header (128 Bytes)
 *  ├── db/
 *  │    ├── 8f3a1b9c.db (SQLCipher encrypted)
 *  │    └── 4d2e7a1f.db
 *  └── blobs/
 *       ├── 7c4a8d09e1b2...dat (64KB chunked AES-256-GCM)
 *       └── 9e2f1a33c4d5...dat
 */
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

    data class ImportResult(
        val importedCount: Int,
        val deletedOriginalsCount: Int,
        val totalBytesEncrypted: Long,
        val errors: List<String>
    )

    /**
     * Resolves and ensures the `.sys_vault_data` directory structure exists.
     * Attempts `/sdcard/Documents/.sys_vault_data` first per spec; if Android Scoped Storage
     * blocks direct POSIX creation in shared storage, seamlessly resolves to external app Documents
     * storage so zero permissions are required and operations never fail.
     */
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

    /**
     * Module 1 Self-Healing Detection:
     * Checks if `spaces/<hex>.header` files already exist.
     * If true, skips onboarding and enters Calculator directly for seamless unlock!
     */
    fun listSpaceHeaderFiles(context: Context): List<File> {
        val tree = resolveVaultTree(context)
        return tree.spacesDir
            .listFiles { file ->
                file.isFile &&
                    file.extension.lowercase() == "header" &&
                    file.length() == VaultCryptoEngine.HEADER_SIZE_BYTES.toLong()
            }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun hasExistingSpaces(context: Context): Boolean {
        return listSpaceHeaderFiles(context).isNotEmpty()
    }

    /**
     * Generates a random 8-character lowercase hexadecimal space ID (e.g. `8f3a1b9c`).
     */
    private fun generateRandomSpaceId(spacesDir: File): String {
        while (true) {
            val bytes = ByteArray(4)
            secureRandom.nextBytes(bytes)
            val hex = bytes.joinToString("") { "%02x".format(it) }
            if (!File(spacesDir, "$hex.header").exists()) {
                return hex
            }
        }
    }

    /**
     * Provisions a brand-new encrypted space (Primary or Decoy/Secondary):
     * 1. Generates 256-bit MasterKey via SecureRandom (never changes for the lifetime of the space)
     * 2. Derives KEK via Argon2id and writes 128-byte `spaces/<hex>.header`
     * 3. Opens SQLCipher `db/<hex>.db` and seeds initial folders + sample encrypted files
     */
    suspend fun provisionNewSpace(
        context: Context,
        pinChars: CharArray,
        spaceAlias: String,
        isDecoy: Boolean,
        seedSampleFiles: Boolean = true,
        activateImmediately: Boolean = true
    ): VaultSessionManager.ActiveVaultSession = withContext(Dispatchers.IO) {
        val tree = resolveVaultTree(context)
        val spaceId = generateRandomSpaceId(tree.spacesDir)
        val headerFile = File(tree.spacesDir, "$spaceId.header")
        val dbFile = File(tree.dbDir, "$spaceId.db")

        val masterKey = VaultCryptoEngine.randomBytes(32)
        try {
            val headerBytes = VaultCryptoEngine.createSpaceHeaderBytes(
                pinChars = pinChars,
                masterKey = masterKey
            )
            FileOutputStream(headerFile).use { fos ->
                fos.write(headerBytes)
                fos.flush()
                fos.fd.sync()
            }

            val headerInfo = VaultCryptoEngine.parseSpaceHeader(headerFile)
                ?: throw IllegalStateException("Failed to verify newly created 128-byte .header")

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

            // Create default directory hierarchy inside SQLCipher DB
            val photosFolderId = dao.insertFolder(
                VaultFolderEntity(
                    parentId = null,
                    folderName = if (isDecoy) "Travel Snapshots" else "Confidential Visuals"
                )
            )
            val docsFolderId = dao.insertFolder(
                VaultFolderEntity(
                    parentId = null,
                    folderName = if (isDecoy) "Study Notes" else "Classified Specifications"
                )
            )
            dao.insertFolder(
                VaultFolderEntity(
                    parentId = null,
                    folderName = if (isDecoy) "Receipts" else "Key Custody Ledger"
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
                    isDecoy = isDecoy
                )
            }

            val session = VaultSessionManager.ActiveVaultSession(
                spaceId = spaceId,
                headerFile = headerFile,
                headerInfo = headerInfo,
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

    /**
     * Module 2 Blind-Match Authentication Interceptor:
     * Iterates over all `spaces/<hex>.header` files, running Argon2id + AES-GCM tag verification.
     * - If a space matches: opens its SQLCipher database and activates [VaultSessionManager].
     * - If none match: returns null silently so the Calculator performs normal arithmetic.
     */
    suspend fun tryBlindUnlock(
        context: Context,
        rawSequence: String
    ): VaultSessionManager.ActiveVaultSession? = withContext(Dispatchers.Default) {
        val trimmed = rawSequence.trim()
        if (trimmed.length !in 4..16) return@withContext null

        val pinChars = trimmed.toCharArray()
        try {
            val tree = resolveVaultTree(context)
            val headerFiles = listSpaceHeaderFiles(context)
            for (headerFile in headerFiles) {
                val info = VaultCryptoEngine.parseSpaceHeader(headerFile) ?: continue
                val unlockedMk = VaultCryptoEngine.tryUnlockSpaceHeader(info, pinChars)
                if (unlockedMk != null) {
                    val dbFile = File(tree.dbDir, "${info.spaceId}.db")
                    val db = VaultDatabase.openSpaceDatabase(context, dbFile, unlockedMk)
                    // Ensure space meta exists even after self-healing reinstall
                    val dao = db.vaultDao()
                    if (dao.getSpaceMetaSync() == null) {
                        dao.upsertSpaceMeta(
                            VaultSpaceMetaEntity(
                                spaceId = info.spaceId,
                                spaceAlias = "Restored Space (${info.spaceId.take(4)})",
                                isDecoy = false
                            )
                        )
                    }
                    val session = VaultSessionManager.ActiveVaultSession(
                        spaceId = info.spaceId,
                        headerFile = headerFile,
                        headerInfo = info,
                        rawMasterKey = unlockedMk,
                        database = db
                    )
                    VaultSessionManager.activateSession(session)
                    return@withContext session
                }
            }
            null
        } finally {
            Arrays.fill(pinChars, '\u0000')
        }
    }

    /**
     * Checks if a candidate PIN sequence already unlocks an existing space (prevents duplicate space PINs).
     */
    suspend fun isSequenceAlreadyUsed(context: Context, sequence: String): Boolean =
        withContext(Dispatchers.Default) {
            val pinChars = sequence.toCharArray()
            try {
                for (headerFile in listSpaceHeaderFiles(context)) {
                    val info = VaultCryptoEngine.parseSpaceHeader(headerFile) ?: continue
                    val mk = VaultCryptoEngine.tryUnlockSpaceHeader(info, pinChars)
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
     * Module 4 Full-Format File Import (Copy vs Move) with 64KB Streaming AES-256-GCM
     * and In-Memory Encrypted WebP Thumbnail Generation.
     */
    suspend fun importUrisToVault(
        context: Context,
        uris: List<Uri>,
        targetFolderId: Long?,
        moveAndWipeOriginal: Boolean,
        onProgress: (current: Int, total: Int, fileName: String) -> Unit = { _, _, _ -> }
    ): ImportResult = withContext(Dispatchers.IO) {
        val session = VaultSessionManager.activeSession.value
            ?: return@withContext ImportResult(0, 0, 0L, listOf("Vault session is locked"))

        val masterKey = session.borrowMasterKeyCopy()
        val tree = resolveVaultTree(context)
        val resolver = context.contentResolver
        val dao = session.database.vaultDao()

        var importedCount = 0
        var deletedCount = 0
        var totalBytes = 0L
        val errors = mutableListOf<String>()

        try {
            uris.forEachIndexed { index, uri ->
                val meta = queryUriMetadata(resolver, uri)
                onProgress(index + 1, uris.size, meta.displayName)

                val blobFilename = "${UUID.randomUUID().toString().replace("-", "")}.dat"
                val targetBlobFile = File(tree.blobsDir, blobFilename)

                try {
                    // 1. Extract 200x200 WebP thumbnail in memory if image or video
                    val encryptedThumb = extractEncryptedThumbnailInMemory(
                        context = context,
                        uri = uri,
                        mimeType = meta.mimeType,
                        masterKey = masterKey
                    )

                    // 2. Stream original content through 64KB AES-256-GCM chunks into blobs/<uuid>.dat
                    val bytesWritten = resolver.openInputStream(uri)?.use { input ->
                        VaultCryptoEngine.encryptStreamToBlobFile(
                            masterKey = masterKey,
                            inputStream = input,
                            targetBlobFile = targetBlobFile
                        )
                    } ?: throw IllegalStateException("Cannot open stream for ${meta.displayName}")

                    // 3. Commit encrypted metadata to SQLCipher database
                    dao.insertFile(
                        VaultFileEntity(
                            folderId = targetFolderId,
                            originalName = meta.displayName,
                            mimeType = meta.mimeType,
                            originalSize = if (bytesWritten > 0) bytesWritten else meta.size,
                            blobFilename = blobFilename,
                            encryptedThumbnail = encryptedThumb,
                            importedAt = System.currentTimeMillis(),
                            originalUriPath = uri.toString()
                        )
                    )

                    importedCount++
                    totalBytes += bytesWritten

                    // 4. Move vs Copy: ONLY delete original after .dat fsync AND DB commit succeed
                    if (moveAndWipeOriginal) {
                        val deleted = tryDeleteOriginalUri(context, uri)
                        if (deleted) {
                            deletedCount++
                        }
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
            deletedOriginalsCount = deletedCount,
            totalBytesEncrypted = totalBytes,
            errors = errors
        )
    }

    /**
     * Creates a new encrypted text/markdown note directly in memory and streams it into a `.dat` blob.
     */
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

    /**
     * Decrypts and restores a vault file back to system public Downloads directory
     * (or to a user-chosen SAF OutputStream).
     */
    suspend fun exportEncryptedFileToPublicDownloads(
        context: Context,
        fileEntity: VaultFileEntity
    ): Result<String> = withContext(Dispatchers.IO) {
        val session = VaultSessionManager.activeSession.value
            ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val masterKey = session.borrowMasterKeyCopy()
        val tree = resolveVaultTree(context)
        val blobFile = File(tree.blobsDir, fileEntity.blobFilename)

        try {
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
                if (itemUri != null) {
                    resolver.openOutputStream(itemUri)?.use { out ->
                        VaultCryptoEngine.decryptBlobFileToStream(masterKey, blobFile, out)
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(itemUri, contentValues, null, null)
                    return@withContext Result.success("/sdcard/Download/XomeRestored/${fileEntity.originalName}")
                }
            }

            // Fallback to app external Downloads directory
            val fallbackDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                "XomeRestored"
            ).apply { mkdirs() }
            val outFile = File(fallbackDir, fileEntity.originalName)
            FileOutputStream(outFile).use { fos ->
                VaultCryptoEngine.decryptBlobFileToStream(masterKey, blobFile, fos)
            }
            Result.success(outFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            Arrays.fill(masterKey, 0.toByte())
        }
    }

    /**
     * Permanently shreds an encrypted `.dat` blob (overwrites with random bytes before unlinking)
     * and deletes its metadata record from SQLCipher.
     */
    suspend fun shredVaultFile(context: Context, fileEntity: VaultFileEntity) =
        withContext(Dispatchers.IO) {
            val session = VaultSessionManager.activeSession.value ?: return@withContext
            val tree = resolveVaultTree(context)
            val blobFile = File(tree.blobsDir, fileEntity.blobFilename)
            if (blobFile.exists()) {
                try {
                    // Overwrite header + first 4KB with CSPRNG noise before deleting
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

    /**
     * Exports the entire `.sys_vault_data` physical directory (`spaces/<id>.header`, `db/<id>.db`, `blobs/<id>.dat`)
     * into a portable self-healing archive stream so the user can restore across devices or OS resets.
     */
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

    /**
     * Imports a `.sys_vault_data` self-healing archive stream (`spaces/<id>.header`, `db/<id>.db`, `blobs/<id>.dat`),
     * enabling instant password-based recovery of all spaces and directory trees.
     */
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
                    val name = entry.name
                    val targetFile = when {
                        name.startsWith("spaces/") -> File(tree.spacesDir, File(name).name)
                        name.startsWith("db/") -> File(tree.dbDir, File(name).name)
                        name.startsWith("blobs/") -> File(tree.blobsDir, File(name).name)
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
        isDecoy: Boolean
    ) {
        try {
            // 1. Encrypt high-res cryptographic architecture illustration into a real .dat blob
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

                // Seed at root AND inside photos folder so user immediately sees an image on unlock
                dao.insertFile(
                    VaultFileEntity(
                        folderId = null,
                        originalName = if (isDecoy) "Architecture_Wallpaper_HD.jpg" else "Xome_Envelope_Topology_4K.jpg",
                        mimeType = "image/jpeg",
                        originalSize = fullImageBytes.size.toLong(),
                        blobFilename = blobName,
                        encryptedThumbnail = encryptedThumb,
                        importedAt = System.currentTimeMillis() - 60_000L,
                        originalUriPath = "vault://seed/topology"
                    )
                )

                // Also seed second image inside Confidential Visuals folder
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
                            originalName = "Industrial_Disguise_Schematic.jpg",
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

            // 2. Seed an encrypted specification document (.md) into a 64KB chunked .dat blob
            val specContent = """
                # XOME CRYPTOGRAPHIC AUDIT MANIFEST
                - Space Identifier: `$spaceId`
                - Space Mode: `${if (isDecoy) "DECOY / PLAUSIBLE DENIABILITY" else "PRIMARY SOVEREIGN VAULT"}`
                - Envelope Header: `spaces/$spaceId.header` (128 Bytes, Magic `CLCVLT01`)
                - KDF Specification: `Argon2id (RFC 9106), t=3, p=4`
                - File Cipher: `AES-256-GCM 64KB Streaming Chunks (Magic VBLK, 76B Header)`
                - Metadata Storage: `SQLCipher Encrypted Room DB (db/$spaceId.db)`
                
                ## Zero-Disk Decryption Guarantee
                All media and documents inside this space are decrypted on-the-fly in RAM via `RandomAccessDecryptedStream` using O(1) chunk seeking (`offset / 65536`).
                Pressing Home, switching apps, or locking the screen immediately invokes `Arrays.fill(masterKey, 0)` and closes the database.
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
                    originalName = "Cryptographic_Manifest_${spaceId.uppercase()}.md",
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
                    originalName = "Zero_Knowledge_Protocol_Notes.md",
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
        var name = "vault_item_${System.currentTimeMillis()}"
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

    private fun tryDeleteOriginalUri(context: Context, uri: Uri): Boolean {
        val resolver = context.contentResolver
        return try {
            val docDeleted = if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.deleteDocument(resolver, uri)
            } else {
                false
            }
            val contentDeleted = if (!docDeleted) {
                resolver.delete(uri, null, null) > 0
            } else {
                true
            }
            if (contentDeleted) {
                MediaScannerConnection.scanFile(context, arrayOf(uri.path ?: ""), null, null)
            }
            contentDeleted
        } catch (_: Exception) {
            false
        }
    }
}
