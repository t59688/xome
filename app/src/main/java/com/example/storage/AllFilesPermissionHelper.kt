package com.example.storage

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale

object AllFilesPermissionHelper {

    data class StorageShortcut(
        val key: String,
        val labelZh: String,
        val labelEn: String,
        val directory: File,
        val isRoot: Boolean = false
    )

    data class LocalFileEntry(
        val file: File,
        val name: String,
        val isDirectory: Boolean,
        val sizeBytes: Long,
        val lastModifiedMs: Long,
        val childCount: Int = 0
    )

    data class LocalMediaItem(
        val mediaUri: Uri,
        val file: File,
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val dateModifiedSeconds: Long,
        val bucketName: String,
        val isVideo: Boolean,
        val durationMs: Long
    )

    @VisibleForTesting
    internal var testAllFilesAccessOverride: Boolean? = null

    fun hasAllFilesAccess(context: Context): Boolean {
        testAllFilesAccessOverride?.let { return it }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun needsLegacyRuntimePermissions(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R

    fun legacyStoragePermissions(): Array<String> = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    fun createAllFilesSettingsIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appIntent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            if (appIntent.resolveActivity(context.packageManager) != null) {
                return appIntent
            }
            return Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
    }

    fun discoverStorageVolumeRoots(context: Context): Map<String, File> {
        val roots = linkedMapOf<String, File>()
        val primary = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        if (primary != null && primary.exists()) {
            roots["primary"] = primary
        }
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        storageManager?.storageVolumes?.forEach { volume ->
            val dir: File? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                volume.directory
            } else {
                val uuid = volume.uuid
                when {
                    volume.isPrimary -> primary
                    !uuid.isNullOrBlank() -> File("/storage/$uuid")
                    else -> null
                }
            }
            if (dir != null && dir.exists()) {
                val key = if (volume.isPrimary) "primary" else (volume.uuid ?: dir.name)
                roots.putIfAbsent(key, dir)
            }
        }
        return roots
    }

    fun discoverStorageShortcuts(context: Context): List<StorageShortcut> {
        val volumes = discoverStorageVolumeRoots(context)
        val primary = volumes["primary"] ?: Environment.getExternalStorageDirectory()
        val shortcuts = mutableListOf<StorageShortcut>()

        if (primary != null && primary.exists()) {
            shortcuts += StorageShortcut(
                key = "primary",
                labelZh = "内部存储",
                labelEn = "Internal",
                directory = primary,
                isRoot = true
            )
            listOf(
                Triple(Environment.DIRECTORY_DOWNLOADS, "下载", "Download"),
                Triple(Environment.DIRECTORY_DOCUMENTS, "文档", "Documents"),
                Triple(Environment.DIRECTORY_PICTURES, "图片", "Pictures"),
                Triple(Environment.DIRECTORY_DCIM, "相册", "DCIM"),
                Triple(Environment.DIRECTORY_MOVIES, "视频", "Movies")
            ).forEach { (dirName, zh, en) ->
                val sub = File(primary, dirName)
                if (sub.exists() && sub.isDirectory) {
                    shortcuts += StorageShortcut(
                        key = "sub_$dirName",
                        labelZh = zh,
                        labelEn = en,
                        directory = sub,
                        isRoot = false
                    )
                }
            }
        }

        volumes.forEach { (volId, dir) ->
            if (volId != "primary" && dir.exists()) {
                shortcuts += StorageShortcut(
                    key = "vol_$volId",
                    labelZh = "存储卡 ($volId)",
                    labelEn = "SD ($volId)",
                    directory = dir,
                    isRoot = true
                )
            }
        }
        return shortcuts
    }

    fun listDirectoryEntries(
        directory: File,
        protectedRootPaths: Set<String>
    ): List<LocalFileEntry> {
        val children = runCatching { directory.listFiles() }.getOrNull() ?: return emptyList()
        val normalizedProtected = protectedRootPaths.mapNotNull { runCatching { File(it).canonicalPath }.getOrNull() }

        return children.asSequence()
            .filter { file ->
                if (!file.exists()) return@filter false
                if (file.name == VaultStorageManager.VAULT_ROOT_DIR_NAME) return@filter false
                val canonical = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
                normalizedProtected.none { prot ->
                    canonical == prot || canonical.startsWith("$prot${File.separator}")
                }
            }
            .map { file ->
                val isDir = file.isDirectory
                val childCount = if (isDir) {
                    runCatching { file.list()?.size ?: 0 }.getOrDefault(0)
                } else {
                    0
                }
                LocalFileEntry(
                    file = file,
                    name = file.name,
                    isDirectory = isDir,
                    sizeBytes = if (isDir) 0L else file.length(),
                    lastModifiedMs = file.lastModified(),
                    childCount = childCount
                )
            }
            .sortedWith(
                compareByDescending<LocalFileEntry> { it.isDirectory }
                    .thenComparator { a, b ->
                        if (a.isDirectory && b.isDirectory) {
                            a.name.lowercase(Locale.ROOT).compareTo(b.name.lowercase(Locale.ROOT))
                        } else {
                            val timeCmp = b.lastModifiedMs.compareTo(a.lastModifiedMs)
                            if (timeCmp != 0) timeCmp else a.name.lowercase(Locale.ROOT).compareTo(b.name.lowercase(Locale.ROOT))
                        }
                    }
            )
            .toList()
    }

    fun listLocalMediaItems(
        context: Context,
        protectedRootPaths: Set<String>
    ): List<LocalMediaItem> {
        val normalizedProtected = protectedRootPaths.mapNotNull { runCatching { File(it).canonicalPath }.getOrNull() }
        val images = queryMediaCollection(
            context = context,
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            isVideo = false,
            protectedPaths = normalizedProtected
        )
        val videos = queryMediaCollection(
            context = context,
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            isVideo = true,
            protectedPaths = normalizedProtected
        )
        return (images + videos).sortedByDescending { it.dateModifiedSeconds }
    }

    @Suppress("DEPRECATION")
    private fun queryMediaCollection(
        context: Context,
        collection: Uri,
        isVideo: Boolean,
        protectedPaths: List<String>
    ): List<LocalMediaItem> {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            add(MediaStore.MediaColumns.DATA)
            if (isVideo) {
                add(MediaStore.Video.VideoColumns.DURATION)
            }
        }.toTypedArray()

        val items = mutableListOf<LocalMediaItem>()
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val modIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val bucketIdx = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val durIdx = if (isVideo) cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1

                while (cursor.moveToNext()) {
                    if (idIdx < 0 || dataIdx < 0) continue
                    val id = cursor.getLong(idIdx)
                    val path = cursor.getString(dataIdx) ?: continue
                    val file = File(path)
                    if (!file.exists() || !file.isFile) continue
                    val canonical = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
                    if (protectedPaths.any { prot -> canonical == prot || canonical.startsWith("$prot${File.separator}") }) {
                        continue
                    }
                    val displayName = if (nameIdx >= 0) cursor.getString(nameIdx) ?: file.name else file.name
                    val mimeType = if (mimeIdx >= 0) {
                        cursor.getString(mimeIdx) ?: if (isVideo) "video/*" else "image/*"
                    } else {
                        if (isVideo) "video/*" else "image/*"
                    }
                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else file.length()
                    val modified = if (modIdx >= 0 && !cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L
                    val bucket = if (bucketIdx >= 0) cursor.getString(bucketIdx).orEmpty() else ""
                    val duration = if (durIdx >= 0 && !cursor.isNull(durIdx)) cursor.getLong(durIdx) else 0L

                    items += LocalMediaItem(
                        mediaUri = ContentUris.withAppendedId(collection, id),
                        file = file,
                        displayName = displayName,
                        mimeType = mimeType,
                        sizeBytes = if (size > 0L) size else file.length(),
                        dateModifiedSeconds = modified,
                        bucketName = bucket.ifBlank { file.parentFile?.name ?: "Media" },
                        isVideo = isVideo,
                        durationMs = duration
                    )
                }
            }
        }
        return items
    }
}
