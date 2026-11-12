package com.example.ui.vault

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.storage.AllFilesPermissionHelper
import com.example.storage.VaultStorageManager
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CalcAccentCopper
import com.example.ui.theme.CalcClearKeyText
import com.example.ui.theme.CalcTextMuted
import com.example.ui.theme.CalcTextPrimary
import com.example.ui.theme.CalcTextSecondary
import com.example.ui.theme.VaultAccentPrimary
import com.example.ui.theme.VaultAmberWarning
import com.example.ui.theme.VaultBorderSubtle
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceElevated
import com.example.ui.theme.VaultVoidBg
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AllFilesPermissionRequiredDialog(
    onDismiss: () -> Unit,
    onConfirmGrant: () -> Unit,
    onContinueWithoutDelete: (() -> Unit)? = null
) {
    val zh = LocalAppStrings.current.language == AppLanguage.ZH
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultSurface,
        icon = {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = VaultAmberWarning,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Text(
                text = if (zh) "需要所有文件管理权限" else "All Files Access Required",
                style = MaterialTheme.typography.titleLarge,
                color = CalcTextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (zh) {
                        "开启「导入并删除原文件」或直选本地目录时，需要授予全部文件管理权限，才能确保在导入加密后彻底删除相册、下载目录或任意本地文档原文件，避免系统拦截导致删除失败。"
                    } else {
                        "Full file management access is required to reliably delete original photos, videos, downloads, and documents after encryption without system provider restrictions."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = CalcTextSecondary
                )
                if (onContinueWithoutDelete != null) {
                    TextButton(
                        onClick = onContinueWithoutDelete,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = if (zh) "改用「仅导入并保留原文件」" else "Switch to Keep Originals mode",
                            color = VaultAccentPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmGrant,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultAccentPrimary,
                    contentColor = Color(0xFF06202B)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("grant_all_files_dialog_button")
            ) {
                Text(
                    text = if (zh) "立即开启权限" else "Grant Access",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = if (zh) "取消" else "Cancel",
                    color = CalcTextSecondary,
                    maxLines = 1
                )
            }
        }
    )
}

@Composable
internal fun LocalFileBrowserDialog(
    isMoveAndWipeMode: Boolean,
    onDismiss: () -> Unit,
    onConfirmUris: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val zh = LocalAppStrings.current.language == AppLanguage.ZH

    val shortcuts = remember { AllFilesPermissionHelper.discoverStorageShortcuts(context) }
    val protectedPaths = remember { VaultStorageManager.protectedVaultPaths(context) }
    val defaultDir = remember(shortcuts) {
        shortcuts.firstOrNull { it.key == "sub_Download" }?.directory
            ?: shortcuts.firstOrNull()?.directory
            ?: android.os.Environment.getExternalStorageDirectory()
    }

    var currentDir by remember { mutableStateOf(defaultDir) }
    var searchFilter by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<AllFilesPermissionHelper.LocalFileEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val selectedSizesByPath = remember { mutableStateMapOf<String, Long>() }

    LaunchedEffect(currentDir) {
        isLoading = true
        entries = withContext(Dispatchers.IO) {
            AllFilesPermissionHelper.listDirectoryEntries(currentDir, protectedPaths)
        }
        isLoading = false
    }

    val visibleEntries = remember(entries, searchFilter) {
        val q = searchFilter.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) entries else entries.filter { it.name.lowercase(Locale.ROOT).contains(q) }
    }
    val visibleFiles = remember(visibleEntries) { visibleEntries.filter { !it.isDirectory } }
    val allVisibleFilesSelected = visibleFiles.isNotEmpty() &&
        visibleFiles.all { selectedSizesByPath.containsKey(it.file.absolutePath) }

    val activeRoot = remember(currentDir, shortcuts) {
        shortcuts.filter { it.isRoot }
            .firstOrNull { root ->
                val cur = currentDir.absolutePath
                val r = root.directory.absolutePath
                cur == r || cur.startsWith("$r${File.separator}")
            }?.directory ?: shortcuts.firstOrNull { it.isRoot }?.directory
    }
    val canGoUp = remember(currentDir, activeRoot) {
        activeRoot != null && currentDir.absolutePath != activeRoot.absolutePath && currentDir.parentFile != null
    }

    BackHandler {
        if (canGoUp) {
            currentDir.parentFile?.let { currentDir = it }
        } else {
            onDismiss()
        }
    }

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = VaultVoidBg,
            modifier = Modifier
                .fillMaxSize()
                .testTag("local_file_browser_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VaultSurface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = CalcTextPrimary)
                        }
                        Column {
                            Text(
                                text = if (zh) "选择本地文件" else "Select Local Files",
                                style = MaterialTheme.typography.titleMedium,
                                color = CalcTextPrimary
                            )
                            if (isMoveAndWipeMode) {
                                Text(
                                    text = if (zh) "导入后将彻底删除所选原文件" else "Originals will be deleted after encryption",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CalcAccentCopper
                                )
                            }
                        }
                    }

                    if (visibleFiles.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                if (allVisibleFilesSelected) {
                                    visibleFiles.forEach { selectedSizesByPath.remove(it.file.absolutePath) }
                                } else {
                                    visibleFiles.forEach { selectedSizesByPath[it.file.absolutePath] = it.sizeBytes }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = null,
                                tint = VaultAccentPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (allVisibleFilesSelected) {
                                    if (zh) "取消全选" else "Deselect"
                                } else {
                                    if (zh) "全选文件" else "Select All"
                                },
                                color = VaultAccentPrimary
                            )
                        }
                    }
                }

                // Shortcuts row
                if (shortcuts.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        shortcuts.forEach { shortcut ->
                            val isSelected = currentDir.absolutePath == shortcut.directory.absolutePath
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    searchFilter = ""
                                    currentDir = shortcut.directory
                                },
                                label = {
                                    Text(
                                        text = if (zh) shortcut.labelZh else shortcut.labelEn,
                                        maxLines = 1
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VaultAccentPrimary,
                                    selectedLabelColor = Color(0xFF06202B)
                                )
                            )
                        }
                    }
                }

                // Current path + Up button + Search
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = VaultSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, VaultBorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { currentDir.parentFile?.let { currentDir = it } },
                                enabled = canGoUp,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = if (zh) "上一级" else "Up",
                                    tint = if (canGoUp) VaultAccentPrimary else CalcTextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = currentDir.absolutePath,
                                style = MaterialTheme.typography.labelMedium,
                                color = CalcTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = searchFilter,
                        onValueChange = { searchFilter = it },
                        placeholder = {
                            Text(
                                text = if (zh) "过滤当前目录文件名..." else "Filter current folder...",
                                color = CalcTextMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = CalcTextMuted, modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VaultAccentPrimary,
                            unfocusedBorderColor = VaultBorderSubtle,
                            focusedTextColor = CalcTextPrimary,
                            unfocusedTextColor = CalcTextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Directory list
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        isLoading -> {
                            CircularProgressIndicator(
                                color = VaultAccentPrimary,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        visibleEntries.isEmpty() -> {
                            Text(
                                text = if (zh) "当前目录没有可选文件" else "No files in this directory",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CalcTextMuted,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        else -> {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(visibleEntries, key = { it.file.absolutePath }) { entry ->
                                    val isChecked = selectedSizesByPath.containsKey(entry.file.absolutePath)
                                    LocalEntryRow(
                                        entry = entry,
                                        isChecked = isChecked,
                                        formattedDate = dateFormatter.format(Date(entry.lastModifiedMs)),
                                        zh = zh,
                                        onClick = {
                                            if (entry.isDirectory) {
                                                searchFilter = ""
                                                currentDir = entry.file
                                            } else {
                                                val path = entry.file.absolutePath
                                                if (selectedSizesByPath.containsKey(path)) {
                                                    selectedSizesByPath.remove(path)
                                                } else {
                                                    selectedSizesByPath[path] = entry.sizeBytes
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom confirmation bar
                HorizontalDivider(color = VaultBorderSubtle)
                Surface(color = VaultSurface, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val totalBytes = selectedSizesByPath.values.sum()
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (zh) "已选择 ${selectedSizesByPath.size} 个文件"
                                else "${selectedSizesByPath.size} file(s) selected",
                                style = MaterialTheme.typography.titleSmall,
                                color = CalcTextPrimary
                            )
                            Text(
                                text = if (selectedSizesByPath.isEmpty()) {
                                    if (zh) "点选文件，点击文件夹进入浏览" else "Tap files to select or folders to open"
                                } else {
                                    formatByteSize(totalBytes)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = CalcTextSecondary
                            )
                        }
                        Button(
                            onClick = {
                                val uris = selectedSizesByPath.keys.map { Uri.fromFile(File(it)) }
                                onConfirmUris(uris)
                            },
                            enabled = selectedSizesByPath.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMoveAndWipeMode) CalcAccentCopper else VaultAccentPrimary,
                                contentColor = Color(0xFF06202B)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("local_browser_confirm_button")
                        ) {
                            Text(
                                text = if (zh) "导入 (${selectedSizesByPath.size})"
                                else "Import (${selectedSizesByPath.size})",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalEntryRow(
    entry: AllFilesPermissionHelper.LocalFileEntry,
    isChecked: Boolean,
    formattedDate: String,
    zh: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isChecked) VaultAccentPrimary.copy(alpha = 0.12f) else VaultSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isChecked) VaultAccentPrimary.copy(alpha = 0.5f) else VaultBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val icon = if (entry.isDirectory) {
                Icons.Default.Folder
            } else {
                val lower = entry.name.lowercase(Locale.ROOT)
                when {
                    lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                        lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".heic") -> Icons.Default.Image
                    lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".mov") ||
                        lower.endsWith(".avi") || lower.endsWith(".webm") -> Icons.Default.Movie
                    lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".csv") -> Icons.Default.TableChart
                    lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") -> Icons.Default.FolderZip
                    lower.endsWith(".pdf") || lower.endsWith(".md") || lower.endsWith(".txt") ||
                        lower.endsWith(".doc") || lower.endsWith(".docx") -> Icons.Default.Description
                    else -> Icons.AutoMirrored.Filled.InsertDriveFile
                }
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (entry.isDirectory) VaultAccentPrimary else CalcTextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CalcTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (entry.isDirectory) {
                        if (zh) "${entry.childCount} 项" else "${entry.childCount} items"
                    } else {
                        "${formatByteSize(entry.sizeBytes)} · $formattedDate"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = CalcTextSecondary,
                    maxLines = 1
                )
            }
            if (entry.isDirectory) {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CalcTextMuted)
            } else {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = VaultAccentPrimary,
                        checkmarkColor = Color(0xFF06202B),
                        uncheckedColor = CalcTextSecondary
                    )
                )
            }
        }
    }
}

@Composable
internal fun LocalMediaPickerDialog(
    isMoveAndWipeMode: Boolean,
    onDismiss: () -> Unit,
    onConfirmUris: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val zh = LocalAppStrings.current.language == AppLanguage.ZH
    val protectedPaths = remember { VaultStorageManager.protectedVaultPaths(context) }

    var allItems by remember { mutableStateOf<List<AllFilesPermissionHelper.LocalMediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedBucket by remember { mutableStateOf<String?>(null) }
    val selectedItemsByUri = remember { mutableStateMapOf<Uri, Long>() }

    LaunchedEffect(Unit) {
        isLoading = true
        allItems = withContext(Dispatchers.IO) {
            AllFilesPermissionHelper.listLocalMediaItems(context, protectedPaths)
        }
        isLoading = false
    }

    val buckets = remember(allItems) {
        allItems.groupBy { it.bucketName }
            .entries
            .sortedByDescending { it.value.size }
            .map { it.key to it.value.size }
    }
    val visibleItems = remember(allItems, selectedBucket) {
        if (selectedBucket == null) allItems else allItems.filter { it.bucketName == selectedBucket }
    }
    val allVisibleSelected = visibleItems.isNotEmpty() &&
        visibleItems.all { selectedItemsByUri.containsKey(it.mediaUri) }

    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = VaultVoidBg,
            modifier = Modifier
                .fillMaxSize()
                .testTag("local_media_picker_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VaultSurface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = CalcTextPrimary)
                        }
                        Column {
                            Text(
                                text = if (zh) "选择照片与视频" else "Select Photos & Videos",
                                style = MaterialTheme.typography.titleMedium,
                                color = CalcTextPrimary
                            )
                            if (isMoveAndWipeMode) {
                                Text(
                                    text = if (zh) "导入后将彻底删除所选原媒体文件" else "Original media files will be deleted after import",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CalcAccentCopper
                                )
                            }
                        }
                    }

                    if (visibleItems.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                if (allVisibleSelected) {
                                    visibleItems.forEach { selectedItemsByUri.remove(it.mediaUri) }
                                } else {
                                    visibleItems.forEach { selectedItemsByUri[it.mediaUri] = it.sizeBytes }
                                }
                            }
                        ) {
                            Text(
                                text = if (allVisibleSelected) {
                                    if (zh) "取消全选" else "Deselect"
                                } else {
                                    if (zh) "全选" else "Select All"
                                },
                                color = VaultAccentPrimary
                            )
                        }
                    }
                }

                if (buckets.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedBucket == null,
                                onClick = { selectedBucket = null },
                                label = { Text(if (zh) "全部 (${allItems.size})" else "All (${allItems.size})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VaultAccentPrimary,
                                    selectedLabelColor = Color(0xFF06202B)
                                )
                            )
                        }
                        items(buckets, key = { it.first }) { (bucket, count) ->
                            FilterChip(
                                selected = selectedBucket == bucket,
                                onClick = { selectedBucket = bucket },
                                label = { Text("$bucket ($count)", maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VaultAccentPrimary,
                                    selectedLabelColor = Color(0xFF06202B)
                                )
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when {
                        isLoading -> {
                            CircularProgressIndicator(
                                color = VaultAccentPrimary,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        visibleItems.isEmpty() -> {
                            Text(
                                text = if (zh) "未发现照片或视频" else "No photos or videos found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CalcTextMuted,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        else -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(visibleItems, key = { it.mediaUri.toString() }) { item ->
                                    val selected = selectedItemsByUri.containsKey(item.mediaUri)
                                    LocalMediaGridCell(
                                        item = item,
                                        selected = selected,
                                        onToggle = {
                                            if (selected) {
                                                selectedItemsByUri.remove(item.mediaUri)
                                            } else {
                                                selectedItemsByUri[item.mediaUri] = item.sizeBytes
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = VaultBorderSubtle)
                Surface(color = VaultSurface, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val totalBytes = selectedItemsByUri.values.sum()
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (zh) "已选 ${selectedItemsByUri.size} 项媒体"
                                else "${selectedItemsByUri.size} item(s) selected",
                                style = MaterialTheme.typography.titleSmall,
                                color = CalcTextPrimary
                            )
                            if (selectedItemsByUri.isNotEmpty()) {
                                Text(
                                    text = formatByteSize(totalBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CalcTextSecondary
                                )
                            }
                        }
                        Button(
                            onClick = { onConfirmUris(selectedItemsByUri.keys.toList()) },
                            enabled = selectedItemsByUri.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMoveAndWipeMode) CalcAccentCopper else VaultAccentPrimary,
                                contentColor = Color(0xFF06202B)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("local_media_confirm_button")
                        ) {
                            Text(
                                text = if (zh) "导入 (${selectedItemsByUri.size})"
                                else "Import (${selectedItemsByUri.size})",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalMediaGridCell(
    item: AllFilesPermissionHelper.LocalMediaItem,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    val thumb by produceState<ImageBitmap?>(initialValue = null, item.mediaUri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.loadThumbnail(item.mediaUri, Size(240, 240), null)
                    .asImageBitmap()
            }.getOrNull() ?: runCatching {
                if (!item.isVideo) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeFile(item.file.absolutePath, opts)?.asImageBitmap()
                } else {
                    null
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(VaultSurfaceElevated)
            .then(
                if (selected) Modifier.border(2.dp, VaultAccentPrimary, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(onClick = onToggle)
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb!!,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = if (item.isVideo) Icons.Default.Movie else Icons.Default.Image,
                contentDescription = null,
                tint = CalcTextMuted,
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.Center)
            )
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VaultAccentPrimary.copy(alpha = 0.22f))
            )
        }

        Icon(
            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) VaultAccentPrimary else Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .size(20.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
        )

        if (item.isVideo) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = formatDurationMs(item.durationMs),
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private fun formatDurationMs(durationMs: Long): String {
    val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format(Locale.US, "%d:%02d", mins, secs)
}
