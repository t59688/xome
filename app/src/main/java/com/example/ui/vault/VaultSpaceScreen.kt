package com.example.ui.vault

import android.app.Activity
import android.content.IntentSender
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.crypto.RootSecurityDetector
import com.example.crypto.VaultCryptoEngine
import com.example.crypto.VaultSessionManager
import com.example.data.VaultFileEntity
import com.example.data.VaultFolderEntity
import com.example.data.VaultSpaceMetaEntity
import com.example.storage.VaultStorageManager
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CalcAccentCopper
import com.example.ui.theme.CalcClearKeyText
import com.example.ui.theme.CalcTextMuted
import com.example.ui.theme.CalcTextPrimary
import com.example.ui.theme.CalcTextSecondary
import com.example.ui.theme.VaultBorderSubtle
import com.example.ui.theme.VaultAccentPrimary
import com.example.ui.theme.VaultEmeraldSuccess
import com.example.ui.theme.VaultPurpleDecoy
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceElevated
import com.example.ui.theme.VaultVoidBg
import com.example.viewmodel.XomeViewModel
import com.example.update.UpdateRepository
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VaultSpaceScreen(
    session: VaultSessionManager.ActiveVaultSession,
    spaceMeta: VaultSpaceMetaEntity?,
    folders: List<VaultFolderEntity>,
    files: List<VaultFileEntity>,
    breadcrumbs: List<VaultFolderEntity>,
    searchQuery: String,
    totalSpacesCount: Int,
    totalFilesCount: Int,
    totalPlainBytes: Long,
    importProgress: XomeViewModel.ImportProgressState,
    pendingDeleteHandles: List<VaultStorageManager.DeletableSourceHandle>,
    pendingSystemDeleteIntent: IntentSender?,
    statusBanner: XomeViewModel.StatusBanner?,
    securityReport: RootSecurityDetector.EnvironmentSecurityReport,
    flagSecureEnabled: Boolean,
    isStreamingEmulator: Boolean,
    updateRepository: UpdateRepository,
    onToggleFlagSecure: (Boolean) -> Unit,
    onToggleLanguage: () -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onNavigateToFolder: (VaultFolderEntity?) -> Unit,
    onNavigateUp: () -> Boolean,
    onSearchQueryChange: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (VaultFolderEntity) -> Unit,
    onImportUris: (List<android.net.Uri>, Boolean) -> Unit,
    onConfirmDeleteOriginals: () -> Unit,
    onCancelDeleteOriginals: () -> Unit,
    onSystemDeleteIntentCompleted: (Boolean) -> Unit,
    onCreateEncryptedNote: (String, String) -> Unit,
    onOpenFileViewer: (VaultFileEntity) -> Unit,
    onExportFileToPublic: (VaultFileEntity) -> Unit,
    onShredFile: (VaultFileEntity) -> Unit,
    onRenameFile: (VaultFileEntity, String) -> Unit,
    onChangeSequence: (String, String, String, (Boolean, String) -> Unit) -> Unit,
    onVerifyCurrentSequence: (String, (Boolean) -> Unit) -> Unit,
    onCreateDecoySpace: (String, String, String, Boolean, Boolean, Boolean, (Boolean, String) -> Unit) -> Unit,
    onExportSelfHealingArchive: (android.net.Uri) -> Unit,
    onImportSelfHealingArchive: (android.net.Uri) -> Unit,
    onLockVault: () -> Unit,
    onDismissBanner: () -> Unit,
    onExternalActivityStarted: () -> Unit,
    onExternalActivityFinished: () -> Unit,
    pendingSourceReselect: VaultStorageManager.DeletableSourceHandle? = null,
    onSourceReselected: (android.net.Uri?) -> Unit = {}
) {
    val strings = LocalAppStrings.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showImportSheet by remember { mutableStateOf(false) }
    var isMoveAndWipeMode by rememberSaveable { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewNoteDialog by remember { mutableStateOf(false) }
    var selectedFileForActions by remember { mutableStateOf<VaultFileEntity?>(null) }

    BackHandler {
        if (selectedTab == 1) {
            selectedTab = 0
        } else if (!onNavigateUp()) {
            onLockVault()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 25)
    ) { uris ->
        onExternalActivityFinished()
        if (uris.isNotEmpty()) {
            onImportUris(uris, isMoveAndWipeMode)
        }
    }

    val safDocumentsLauncher = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.OpenMultipleDocuments() {
            override fun createIntent(context: android.content.Context, input: Array<String>) =
                super.createIntent(context, input).addFlags(
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
        }
    ) { uris ->
        onExternalActivityFinished()
        if (uris.isNotEmpty()) {
            onImportUris(uris, isMoveAndWipeMode)
        }
    }

    val sourceReselectLauncher = rememberLauncherForActivityResult(
        contract = object : androidx.activity.result.contract.ActivityResultContract<android.net.Uri, android.net.Uri?>() {
            override fun createIntent(context: android.content.Context, input: android.net.Uri) =
                android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
                    .putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, input)
                    .addFlags(
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            override fun parseResult(resultCode: Int, intent: android.content.Intent?): android.net.Uri? =
                if (resultCode == Activity.RESULT_OK) intent?.data else null
        }
    ) { uri ->
        onExternalActivityFinished()
        onSourceReselected(uri)
    }

    if (pendingSourceReselect != null) {
        val zh = strings.language == AppLanguage.ZH
        AlertDialog(
            onDismissRequest = { onSourceReselected(null) },
            title = { Text(if (zh) "授权删除原文件" else "Original file permission") },
            text = {
                Text(if (zh) "尚未确认删除：${pendingSourceReselect.displayName}\n\n" +
                    "请在系统文件中重新选择同一个原文件以授权删除，不要选择同名副本。云端或只读文件可能需要在原应用中手动删除。\n\n加密副本已保存，无需所有文件访问权限。"
                    else "Deletion unconfirmed: ${pendingSourceReselect.displayName}\n\n" +
                        "Reselect this exact original in Files to grant deletion. A same-name copy is not the original. Cloud or read-only sources may require deletion in their own app.\n\nEncrypted copy saved; no all-files permission needed.")
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        sourceReselectLauncher.launchWithVaultGuard(
                            pendingSourceReselect.originalUri,
                            onExternalActivityStarted, onExternalActivityFinished
                        )
                    } catch (_: Exception) {
                        onSourceReselected(null)
                    }
                }) { Text(if (zh) "选择原文件" else "Select original") }
            },
            dismissButton = {
                TextButton(onClick = { onSourceReselected(null) }) { Text(if (zh) "保留原文件" else "Keep originals") }
            }
        )
    }

    val systemDeleteIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        onExternalActivityFinished()
        onSystemDeleteIntentCompleted(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(pendingSystemDeleteIntent) {
        if (pendingSystemDeleteIntent != null) {
            try {
                systemDeleteIntentLauncher.launchWithVaultGuard(
                    IntentSenderRequest.Builder(pendingSystemDeleteIntent).build(),
                    onExternalActivityStarted,
                    onExternalActivityFinished
                )
            } catch (_: Exception) {
                onSystemDeleteIntentCompleted(false)
            }
        }
    }

    val exportArchiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        onExternalActivityFinished()
        if (uri != null) {
            onExportSelfHealingArchive(uri)
        }
    }

    val importArchiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        onExternalActivityFinished()
        if (uri != null) {
            onImportSelfHealingArchive(uri)
        }
    }

    Scaffold(
        containerColor = VaultVoidBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            VaultTopHeaderBar(
                spaceMeta = spaceMeta,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onToggleLanguage = onToggleLanguage,
                onLockVault = onLockVault
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = VaultSurface,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Folder, contentDescription = strings.navEncryptedVfs) },
                    label = { Text(strings.navEncryptedVfs, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VaultAccentPrimary,
                        selectedTextColor = VaultAccentPrimary,
                        indicatorColor = VaultAccentPrimary.copy(alpha = 0.14f),
                        unselectedIconColor = CalcTextSecondary,
                        unselectedTextColor = CalcTextSecondary
                    ),
                    modifier = Modifier.testTag("vault_nav_files")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = strings.navSpacesAndCrypto) },
                    label = { Text(strings.navSpacesAndCrypto, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VaultAccentPrimary,
                        selectedTextColor = VaultAccentPrimary,
                        indicatorColor = VaultAccentPrimary.copy(alpha = 0.14f),
                        unselectedIconColor = CalcTextSecondary,
                        unselectedTextColor = CalcTextSecondary
                    ),
                    modifier = Modifier.testTag("vault_nav_security")
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showImportSheet = true },
                    containerColor = VaultAccentPrimary,
                    contentColor = Color(0xFF06202B),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("vault_fab_add")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = strings.fabEncryptAdd,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
        modifier = Modifier.testTag("vault_space_root")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = statusBanner != null) {
                statusBanner?.let { banner ->
                    Surface(
                        color = if (banner.isError) CalcClearKeyText.copy(alpha = 0.14f) else VaultEmeraldSuccess.copy(alpha = 0.14f),
                        border = BorderStroke(
                            1.dp,
                            if (banner.isError) CalcClearKeyText.copy(alpha = 0.4f) else VaultEmeraldSuccess.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { onDismissBanner() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = banner.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (banner.isError) CalcClearKeyText else VaultEmeraldSuccess,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = CalcTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = importProgress.isImporting) {
                Surface(
                    color = VaultSurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, VaultAccentPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (importProgress.isMoveMode) {
                                strings.movingProgressTitle(importProgress.currentIndex, importProgress.totalCount)
                            } else {
                                strings.streamingProgressTitle(importProgress.currentIndex, importProgress.totalCount)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = VaultAccentPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = importProgress.currentFileName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CalcTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = VaultAccentPrimary,
                            trackColor = VaultBorderSubtle
                        )
                    }
                }
            }

            if (selectedTab == 0) {
                FolderBreadcrumbBar(
                    breadcrumbs = breadcrumbs,
                    onNavigateToRoot = { onNavigateToFolder(null) },
                    onNavigateToCrumb = { onNavigateToFolder(it) },
                    onNewFolderClick = { showNewFolderDialog = true },
                    onNewNoteClick = { showNewNoteDialog = true }
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (folders.isNotEmpty()) {
                        item {
                            Text(
                                text = strings.encryptedDirectoriesHeader(folders.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = CalcTextMuted,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                        items(folders, key = { "folder_${it.id}" }) { folder ->
                            EncryptedFolderRow(
                                folder = folder,
                                onClick = { onNavigateToFolder(folder) },
                                onDelete = { onDeleteFolder(folder) }
                            )
                        }
                    }

                    if (files.isNotEmpty() || folders.isEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = strings.encryptedBlobsHeader(files.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = CalcTextMuted,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }

                    if (files.isEmpty() && folders.isEmpty()) {
                        item {
                            EmptyVaultDirectoryCard(
                                onImportClick = { showImportSheet = true },
                                onNewNoteClick = { showNewNoteDialog = true }
                            )
                        }
                    } else {
                        items(files, key = { "file_${it.id}" }) { fileEntity ->
                            EncryptedFileCard(
                                session = session,
                                fileEntity = fileEntity,
                                onClick = { onOpenFileViewer(fileEntity) },
                                onMoreActionsClick = { selectedFileForActions = fileEntity }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(88.dp))
                    }
                }
            } else {
                VaultSecuritySettingsTab(
                    session = session,
                    spaceMeta = spaceMeta,
                    totalSpacesCount = totalSpacesCount,
                    totalFilesCount = totalFilesCount,
                    totalPlainBytes = totalPlainBytes,
                    securityReport = securityReport,
                    flagSecureEnabled = flagSecureEnabled,
                    isStreamingEmulator = isStreamingEmulator,
                    updateRepository = updateRepository,
                    onToggleFlagSecure = onToggleFlagSecure,
                    onSelectLanguage = onSelectLanguage,
                    onChangeSequence = onChangeSequence,
                    onVerifyCurrentSequence = onVerifyCurrentSequence,
                    onCreateDecoySpace = onCreateDecoySpace,
                    onExportSelfHealingArchive = {
                        exportArchiveLauncher.launchWithVaultGuard(
                            "xome_backup_${session.spaceId}.zip",
                            onExternalActivityStarted,
                            onExternalActivityFinished
                        )
                    },
                    onImportSelfHealingArchive = {
                        importArchiveLauncher.launchWithVaultGuard(
                            arrayOf("*/*"),
                            onExternalActivityStarted,
                            onExternalActivityFinished
                        )
                    }
                )
            }
        }
    }

    if (showImportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showImportSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VaultSurface
        ) {
            VaultImportBottomSheetContent(
                isMoveAndWipeMode = isMoveAndWipeMode,
                onToggleMoveMode = { isMoveAndWipeMode = it },
                onPickPhotosAndVideos = {
                    showImportSheet = false
                    if (isMoveAndWipeMode) {
                        // In Delete Original (Move) mode, use OpenMultipleDocuments for media so SAF grants
                        // write/delete flags directly on the document URI, backed by MediaStore fallback.
                        safDocumentsLauncher.launchWithVaultGuard(
                            arrayOf("image/*", "video/*"),
                            onExternalActivityStarted,
                            onExternalActivityFinished
                        )
                    } else {
                        photoPickerLauncher.launchWithVaultGuard(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                            onExternalActivityStarted,
                            onExternalActivityFinished
                        )
                    }
                },
                onPickAnyFilesSaf = {
                    showImportSheet = false
                    safDocumentsLauncher.launchWithVaultGuard(
                        arrayOf("*/*"),
                        onExternalActivityStarted,
                        onExternalActivityFinished
                    )
                },
                onCreateNewFolder = {
                    showImportSheet = false
                    showNewFolderDialog = true
                },
                onCreateEncryptedNote = {
                    showImportSheet = false
                    showNewNoteDialog = true
                }
            )
        }
    }

    // Final Confirmation Dialog before deleting original files in Move Mode
    if (pendingDeleteHandles.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onCancelDeleteOriginals,
            containerColor = VaultSurface,
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = CalcClearKeyText,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = strings.confirmDeleteOriginalsTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = CalcTextPrimary
                )
            },
            text = {
                Text(
                    text = strings.confirmDeleteOriginalsMessage(
                        pendingDeleteHandles.size,
                        pendingDeleteHandles.map { it.displayName }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CalcTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDeleteOriginals,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CalcClearKeyText,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_delete_originals_button")
                ) {
                    Text(
                        text = strings.confirmDeleteBtn,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onCancelDeleteOriginals,
                    modifier = Modifier.testTag("keep_originals_button")
                ) {
                    Text(
                        text = strings.keepOriginalsBtn,
                        color = CalcTextSecondary,
                        maxLines = 1
                    )
                }
            }
        )
    }

    selectedFileForActions?.let { targetFile ->
        FileActionsDialog(
            fileEntity = targetFile,
            onDismiss = { selectedFileForActions = null },
            onOpenInMemory = {
                selectedFileForActions = null
                onOpenFileViewer(targetFile)
            },
            onRename = { newName ->
                selectedFileForActions = null
                onRenameFile(targetFile, newName)
            },
            onExportToDownloads = {
                selectedFileForActions = null
                onExportFileToPublic(targetFile)
            },
            onShred = {
                selectedFileForActions = null
                onShredFile(targetFile)
            }
        )
    }

    if (showNewFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name ->
                showNewFolderDialog = false
                onCreateFolder(name)
            }
        )
    }

    if (showNewNoteDialog) {
        CreateEncryptedNoteDialog(
            onDismiss = { showNewNoteDialog = false },
            onConfirm = { title, content ->
                showNewNoteDialog = false
                onCreateEncryptedNote(title, content)
            }
        )
    }
}

@Composable
private fun VaultTopHeaderBar(
    spaceMeta: VaultSpaceMetaEntity?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleLanguage: () -> Unit,
    onLockVault: () -> Unit
) {
    val strings = LocalAppStrings.current
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VaultSurface)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (spaceMeta?.isDecoy == true) VaultPurpleDecoy else VaultEmeraldSuccess
                        )
                )
                Text(
                    text = spaceMeta?.spaceAlias ?: strings.encryptedSpaceDefaultTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = CalcTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = {
                        isSearchExpanded = !isSearchExpanded
                        if (!isSearchExpanded && searchQuery.isNotEmpty()) {
                            onSearchQueryChange("")
                        }
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSearchExpanded || searchQuery.isNotEmpty()) {
                                VaultAccentPrimary.copy(alpha = 0.16f)
                            } else {
                                VaultSurfaceElevated
                            }
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = strings.searchPlaceholder,
                        tint = if (isSearchExpanded || searchQuery.isNotEmpty()) VaultAccentPrimary else CalcTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onToggleLanguage,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(VaultSurfaceElevated)
                        .testTag("vault_lang_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = "Switch Language",
                        tint = CalcTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onLockVault,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CalcClearKeyText.copy(alpha = 0.14f))
                        .testTag("vault_instant_lock_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = strings.lockBtn,
                        tint = CalcClearKeyText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        AnimatedVisibility(visible = isSearchExpanded || searchQuery.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(strings.searchPlaceholder) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = CalcTextSecondary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = CalcTextSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VaultAccentPrimary,
                    unfocusedBorderColor = VaultBorderSubtle,
                    focusedContainerColor = VaultVoidBg,
                    unfocusedContainerColor = VaultVoidBg
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vault_search_input")
            )
        }
    }
}

@Composable
private fun FolderBreadcrumbBar(
    breadcrumbs: List<VaultFolderEntity>,
    onNavigateToRoot: () -> Unit,
    onNavigateToCrumb: (VaultFolderEntity) -> Unit,
    onNewFolderClick: () -> Unit,
    onNewNoteClick: () -> Unit
) {
    val strings = LocalAppStrings.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onNavigateToRoot,
                color = if (breadcrumbs.isEmpty()) VaultAccentPrimary.copy(alpha = 0.14f) else VaultSurfaceElevated,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("breadcrumb_root")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = strings.rootBreadcrumb,
                        tint = if (breadcrumbs.isEmpty()) VaultAccentPrimary else CalcTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    if (breadcrumbs.isEmpty()) {
                        Text(
                            text = strings.rootBreadcrumb,
                            style = MaterialTheme.typography.labelLarge,
                            color = VaultAccentPrimary
                        )
                    }
                }
            }

            breadcrumbs.forEachIndexed { idx, crumb ->
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = CalcTextMuted,
                    modifier = Modifier.size(16.dp)
                )
                val isLast = idx == breadcrumbs.lastIndex
                Surface(
                    onClick = { onNavigateToCrumb(crumb) },
                    color = if (isLast) VaultAccentPrimary.copy(alpha = 0.14f) else VaultSurfaceElevated,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = crumb.folderName,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isLast) VaultAccentPrimary else CalcTextSecondary,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(
                onClick = onNewFolderClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("vault_new_folder_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = strings.newFolderBtn,
                    tint = CalcTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onNewNoteClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("vault_new_note_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                    contentDescription = strings.newSecretNoteBtn,
                    tint = CalcTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EncryptedFolderRow(
    folder: VaultFolderEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = VaultSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("folder_item_${folder.folderName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(VaultAccentPrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = VaultAccentPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = folder.folderName,
                    style = MaterialTheme.typography.titleMedium,
                    color = CalcTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete folder",
                    tint = CalcTextMuted,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun EncryptedFileCard(
    session: VaultSessionManager.ActiveVaultSession,
    fileEntity: VaultFileEntity,
    onClick: () -> Unit,
    onMoreActionsClick: () -> Unit
) {
    val cacheKey = "${session.spaceId}_thumb_${fileEntity.id}"
    var thumbBitmap by remember(cacheKey) {
        mutableStateOf(VaultSessionManager.getCachedThumbnail(cacheKey))
    }

    LaunchedEffect(cacheKey, fileEntity.encryptedThumbnail) {
        if (thumbBitmap == null && fileEntity.encryptedThumbnail != null) {
            withContext(Dispatchers.Default) {
                val masterKey = try {
                    session.borrowMasterKeyCopy()
                } catch (_: Exception) {
                    null
                } ?: return@withContext

                try {
                    val plainThumb = VaultCryptoEngine.decryptThumbnail(
                        masterKey = masterKey,
                        encryptedBlob = fileEntity.encryptedThumbnail
                    )
                    if (plainThumb != null) {
                        val bmp = BitmapFactory.decodeByteArray(plainThumb, 0, plainThumb.size)
                        Arrays.fill(plainThumb, 0.toByte())
                        if (bmp != null) {
                            val imgBmp = bmp.asImageBitmap()
                            VaultSessionManager.putCachedThumbnail(cacheKey, imgBmp)
                            thumbBitmap = imgBmp
                        }
                    }
                } finally {
                    Arrays.fill(masterKey, 0.toByte())
                }
            }
        }
    }

    val dateStr = remember(fileEntity.importedAt) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(fileEntity.importedAt))
    }

    Surface(
        onClick = onClick,
        color = VaultSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("file_item_${fileEntity.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VaultSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                if (thumbBitmap != null) {
                    Image(
                        bitmap = thumbBitmap!!,
                        contentDescription = fileEntity.originalName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val icon = when {
                        fileEntity.mimeType.startsWith("image/") -> Icons.Default.Image
                        fileEntity.mimeType.startsWith("video/") -> Icons.Default.Movie
                        fileEntity.mimeType.contains("sheet") || fileEntity.originalName.endsWith(".xlsx") -> Icons.Default.TableChart
                        fileEntity.mimeType.contains("zip") -> Icons.Default.FolderZip
                        fileEntity.mimeType.startsWith("text/") || fileEntity.originalName.endsWith(".md") -> Icons.Default.Description
                        else -> Icons.AutoMirrored.Filled.InsertDriveFile
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = VaultAccentPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = fileEntity.originalName,
                    style = MaterialTheme.typography.titleMedium,
                    color = CalcTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatByteSize(fileEntity.originalSize)} · $dateStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = CalcTextSecondary,
                    maxLines = 1
                )
            }

            IconButton(
                onClick = onMoreActionsClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("file_more_${fileEntity.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Actions",
                    tint = CalcTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyVaultDirectoryCard(
    onImportClick: () -> Unit,
    onNewNoteClick: () -> Unit
) {
    val strings = LocalAppStrings.current

    Surface(
        color = VaultSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, VaultBorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(VaultAccentPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = VaultAccentPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = strings.emptyVaultTitle,
                style = MaterialTheme.typography.titleMedium,
                color = CalcTextPrimary
            )
            Text(
                text = strings.emptyVaultSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = CalcTextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onImportClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VaultAccentPrimary,
                        contentColor = Color(0xFF06202B)
                    )
                ) {
                    Text(strings.importFilesMediaBtn, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onNewNoteClick,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, VaultBorderSubtle)
                ) {
                    Text(strings.newEncryptedNoteBtn, color = CalcTextPrimary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun VaultImportBottomSheetContent(
    isMoveAndWipeMode: Boolean,
    onToggleMoveMode: (Boolean) -> Unit,
    onPickPhotosAndVideos: () -> Unit,
    onPickAnyFilesSaf: () -> Unit,
    onCreateNewFolder: () -> Unit,
    onCreateEncryptedNote: () -> Unit
) {
    val strings = LocalAppStrings.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = strings.importSheetTitle,
            style = MaterialTheme.typography.titleLarge,
            color = CalcTextPrimary
        )

        Surface(
            color = VaultSurfaceElevated,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                1.dp,
                if (isMoveAndWipeMode) CalcAccentCopper.copy(alpha = 0.55f) else VaultBorderSubtle
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isMoveAndWipeMode) strings.modeMoveTitle else strings.modeCopyTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isMoveAndWipeMode) CalcAccentCopper else CalcTextPrimary
                    )
                    Text(
                        text = if (isMoveAndWipeMode) strings.modeMoveDesc else strings.modeCopyDesc,
                        style = MaterialTheme.typography.labelSmall,
                        color = CalcTextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Switch(
                    checked = isMoveAndWipeMode,
                    onCheckedChange = onToggleMoveMode,
                    colors = SwitchDefaults.colors(checkedThumbColor = CalcAccentCopper),
                    modifier = Modifier.testTag("import_move_mode_switch")
                )
            }
        }

        Button(
            onClick = onPickPhotosAndVideos,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VaultAccentPrimary,
                contentColor = Color(0xFF06202B)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("import_photos_button")
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(strings.galleryMultiPickerBtn, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }

        Button(
            onClick = onPickAnyFilesSaf,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VaultSurfaceElevated,
                contentColor = CalcTextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("import_saf_files_button")
        ) {
            Icon(Icons.Default.UploadFile, contentDescription = null, tint = VaultAccentPrimary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(strings.safDocumentPickerBtn, fontWeight = FontWeight.Medium, maxLines = 1)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onCreateNewFolder,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, VaultBorderSubtle),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = VaultAccentPrimary, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.newFolderBtn, color = CalcTextPrimary, maxLines = 1)
            }

            OutlinedButton(
                onClick = onCreateEncryptedNote,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, VaultBorderSubtle),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, tint = CalcAccentCopper, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.newSecretNoteBtn, color = CalcTextPrimary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FileActionsDialog(
    fileEntity: VaultFileEntity,
    onDismiss: () -> Unit,
    onOpenInMemory: () -> Unit,
    onRename: (String) -> Unit,
    onExportToDownloads: () -> Unit,
    onShred: () -> Unit
) {
    val strings = LocalAppStrings.current
    var editedName by remember(fileEntity.id) { mutableStateOf(fileEntity.originalName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultSurface,
        title = {
            Text(
                text = strings.blobOperationsTitle,
                style = MaterialTheme.typography.titleLarge,
                color = CalcTextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = strings.blobInfoSummary(
                        fileEntity.blobFilename,
                        formatByteSize(fileEntity.originalSize),
                        fileEntity.mimeType
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = CalcTextSecondary
                )
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text(strings.encryptedFilenameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onRename(editedName) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.renameBtn, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onExportToDownloads,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = VaultAccentPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.restoreBtn, color = VaultAccentPrimary, maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenInMemory,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultAccentPrimary,
                    contentColor = Color(0xFF06202B)
                )
            ) {
                Text(strings.streamInRamViewerBtn, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        },
        dismissButton = {
            TextButton(onClick = onShred) {
                Text(strings.csprngShredBtn, color = CalcClearKeyText, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    )
}

@Composable
private fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    var folderName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultSurface,
        title = { Text(strings.createFolderDialogTitle, color = CalcTextPrimary) },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text(strings.directoryNameLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(folderName) },
                enabled = folderName.isNotBlank()
            ) {
                Text(strings.createBtn, maxLines = 1)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelBtn, maxLines = 1)
            }
        }
    )
}

@Composable
private fun CreateEncryptedNoteDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val strings = LocalAppStrings.current
    var title by remember { mutableStateOf("Note.md") }
    var content by remember { mutableStateOf(strings.defaultNoteContent) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultSurface,
        title = { Text(strings.authorEncryptedNoteTitle, color = CalcTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(strings.filenameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(strings.secretContentLabel) },
                    minLines = 5,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, content) },
                enabled = title.isNotBlank()
            ) {
                Text(strings.encryptToDatBtn, maxLines = 1)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelBtn, maxLines = 1)
            }
        }
    )
}

/** Keep the vault session only across a known, result-producing Android system UI. */
private fun <I> ActivityResultLauncher<I>.launchWithVaultGuard(
    input: I,
    onExternalActivityStarted: () -> Unit,
    onExternalActivityFinished: () -> Unit
) {
    onExternalActivityStarted()
    try {
        launch(input)
    } catch (e: Exception) {
        onExternalActivityFinished()
        throw e
    }
}
