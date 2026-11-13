package com.example.ui.vault

import android.app.Activity
import android.content.IntentSender
import android.graphics.BitmapFactory
import android.os.Build
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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.crypto.RootSecurityDetector
import com.example.crypto.VaultCryptoEngine
import com.example.crypto.VaultSessionManager
import com.example.data.VaultFileEntity
import com.example.data.VaultFolderEntity
import com.example.data.VaultSpaceMetaEntity
import com.example.storage.AllFilesPermissionHelper
import com.example.storage.VaultStorageManager
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CalcAccentCopper
import com.example.ui.theme.CalcClearKeyText
import com.example.ui.theme.CalcTextMuted
import com.example.ui.theme.CalcTextPrimary
import com.example.ui.theme.CalcTextSecondary
import com.example.ui.theme.VaultAmberWarning
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

private enum class FolderPickerMode {
    COPY,
    MOVE
}

internal data class FlattenedFolderNode(
    val folder: VaultFolderEntity,
    val depth: Int
)

internal fun buildFlattenedFolderTree(allFolders: List<VaultFolderEntity>): List<FlattenedFolderNode> {
    if (allFolders.isEmpty()) return emptyList()
    val childrenByParent = allFolders
        .sortedBy { it.folderName.lowercase() }
        .groupBy { it.parentId }
    val result = mutableListOf<FlattenedFolderNode>()
    val visited = HashSet<Long>(allFolders.size)

    fun dfs(parentId: Long?, depth: Int) {
        childrenByParent[parentId].orEmpty().forEach { folder ->
            if (visited.add(folder.id)) {
                result.add(FlattenedFolderNode(folder = folder, depth = depth))
                dfs(folder.id, depth + 1)
            }
        }
    }

    dfs(parentId = null, depth = 0)
    // Attach any orphaned cycles/nodes deterministically at top depth so no folder is hidden.
    allFolders
        .sortedBy { it.folderName.lowercase() }
        .forEach { folder ->
            if (visited.add(folder.id)) {
                result.add(FlattenedFolderNode(folder = folder, depth = 0))
                dfs(folder.id, 1)
            }
        }
    return result
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VaultSpaceScreen(
    session: VaultSessionManager.ActiveVaultSession,
    spaceMeta: VaultSpaceMetaEntity?,
    folders: List<VaultFolderEntity>,
    files: List<VaultFileEntity>,
    breadcrumbs: List<VaultFolderEntity>,
    allFolders: List<VaultFolderEntity> = emptyList(),
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
    onRenameFolder: (VaultFolderEntity, String) -> Unit = { _, _ -> },
    onDeleteFolder: (VaultFolderEntity) -> Unit,
    onImportUris: (List<android.net.Uri>, Boolean) -> Unit,
    onConfirmDeleteOriginals: () -> Unit,
    onCancelDeleteOriginals: () -> Unit,
    onSystemDeleteIntentCompleted: (Boolean) -> Unit,
    onCreateEncryptedNote: (String, String) -> Unit,
    onOpenFileViewer: (VaultFileEntity) -> Unit,
    onExportFileToPublic: (VaultFileEntity) -> Unit,
    onExportFilesToPublic: (List<VaultFileEntity>) -> Unit = {},
    onCopyFiles: (List<VaultFileEntity>, Long?) -> Unit = { _, _ -> },
    onMoveFiles: (List<VaultFileEntity>, Long?) -> Unit = { _, _ -> },
    onShredFile: (VaultFileEntity) -> Unit,
    onShredFiles: (List<VaultFileEntity>) -> Unit = {},
    onRenameFile: (VaultFileEntity, String) -> Unit,
    onChangeSequence: (String, String, String, (Boolean, String) -> Unit) -> Unit,
    onRotateKeyfile: (String, ByteArray?, (Boolean, String) -> Unit) -> Unit,
    onVerifyCurrentSequence: (String, (Boolean) -> Unit) -> Unit,
    onCreateDecoySpace: (String, String, ByteArray?, String, Boolean, Boolean, Boolean, (Boolean, String) -> Unit) -> Unit,
    onExtractKeyfileDigest: suspend (android.net.Uri) -> Result<com.example.viewmodel.XomeViewModel.KeyfileSelection>,
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
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showImportSheet by remember { mutableStateOf(false) }
    var isMoveAndWipeMode by rememberSaveable { mutableStateOf(false) }
    var hasAllFilesAccess by remember { mutableStateOf(AllFilesPermissionHelper.hasAllFilesAccess(context)) }
    var permissionBannerDismissed by rememberSaveable { mutableStateOf(false) }
    var showPermissionRequiredDialog by remember { mutableStateOf(false) }
    var pendingAfterPermissionGranted by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showLocalFileBrowser by remember { mutableStateOf(false) }
    var showLocalMediaPicker by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewNoteDialog by remember { mutableStateOf(false) }
    var selectedFileForActions by remember { mutableStateOf<VaultFileEntity?>(null) }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedFileIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var fileForRename by remember { mutableStateOf<VaultFileEntity?>(null) }
    var folderForActions by remember { mutableStateOf<VaultFolderEntity?>(null) }
    var folderForRename by remember { mutableStateOf<VaultFolderEntity?>(null) }
    var folderPendingDelete by remember { mutableStateOf<VaultFolderEntity?>(null) }
    var pendingPickerRequest by remember { mutableStateOf<Pair<FolderPickerMode, List<VaultFileEntity>>?>(null) }
    var pendingShredRequest by remember { mutableStateOf<Pair<Boolean, List<VaultFileEntity>>?>(null) }

    val currentFolderId = breadcrumbs.lastOrNull()?.id
    val selectedFiles = remember(files, selectedFileIds) {
        files.filter { it.id in selectedFileIds }
    }
    val allCurrentFilesSelected = files.isNotEmpty() && selectedFileIds.containsAll(files.map { it.id })

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedFileIds = emptySet()
    }

    fun toggleFileSelection(fileId: Long) {
        selectedFileIds = if (fileId in selectedFileIds) {
            selectedFileIds - fileId
        } else {
            selectedFileIds + fileId
        }
    }

    LaunchedEffect(files) {
        val visibleIds = files.map { it.id }.toSet()
        val pruned = selectedFileIds.intersect(visibleIds)
        if (pruned != selectedFileIds) {
            selectedFileIds = pruned
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab != 0 && isSelectionMode) {
            exitSelectionMode()
        }
    }

    BackHandler {
        when {
            selectedTab == 1 -> selectedTab = 0
            isSelectionMode -> exitSelectionMode()
            !onNavigateUp() -> onLockVault()
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

    val allFilesSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onExternalActivityFinished()
        val granted = AllFilesPermissionHelper.hasAllFilesAccess(context)
        hasAllFilesAccess = granted
        val action = pendingAfterPermissionGranted
        pendingAfterPermissionGranted = null
        if (granted) {
            action?.invoke()
        }
    }

    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onExternalActivityFinished()
        val granted = AllFilesPermissionHelper.hasAllFilesAccess(context)
        hasAllFilesAccess = granted
        val action = pendingAfterPermissionGranted
        pendingAfterPermissionGranted = null
        if (granted) {
            action?.invoke()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAllFilesAccess = AllFilesPermissionHelper.hasAllFilesAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestAllFilesPermission(afterGranted: (() -> Unit)? = null) {
        if (AllFilesPermissionHelper.hasAllFilesAccess(context)) {
            hasAllFilesAccess = true
            afterGranted?.invoke()
            return
        }
        pendingAfterPermissionGranted = afterGranted
        if (AllFilesPermissionHelper.needsLegacyRuntimePermissions()) {
            legacyStoragePermissionLauncher.launchWithVaultGuard(
                AllFilesPermissionHelper.legacyStoragePermissions(),
                onExternalActivityStarted,
                onExternalActivityFinished
            )
        } else {
            allFilesSettingsLauncher.launchWithVaultGuard(
                AllFilesPermissionHelper.createAllFilesSettingsIntent(context),
                onExternalActivityStarted,
                onExternalActivityFinished
            )
        }
    }

    if (pendingSourceReselect != null) {
        val zh = strings.language == AppLanguage.ZH
        AlertDialog(
            onDismissRequest = { onSourceReselected(null) },
            title = { Text(if (zh) "授权删除原文件" else "Original file permission") },
            text = {
                Text(
                    if (zh) {
                        "尚未确认删除：${pendingSourceReselect.displayName}\n\n" +
                            if (!hasAllFilesAccess) {
                                "当前尚未授予「全部文件管理权限」，系统提供方可能拒绝删除。建议立即开启全部文件权限，或在系统文件中重选同一个原文件以授权删除。"
                            } else {
                                "请在系统文件中重新选择同一个原文件以授权删除，不要选择同名副本。云端或只读提供方可能需要在原应用中手动删除。"
                            }
                    } else {
                        "Deletion unconfirmed: ${pendingSourceReselect.displayName}\n\n" +
                            if (!hasAllFilesAccess) {
                                "All-files access is currently disabled, so system providers may block deletion. Grant all-files access, or reselect this exact original in Files."
                            } else {
                                "Reselect this exact original in Files to grant deletion. A same-name copy is not the original. Cloud or read-only sources may require deletion in their own app."
                            }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!hasAllFilesAccess) {
                        onSourceReselected(null)
                        requestAllFilesPermission()
                    } else {
                        try {
                            sourceReselectLauncher.launchWithVaultGuard(
                                pendingSourceReselect.originalUri,
                                onExternalActivityStarted, onExternalActivityFinished
                            )
                        } catch (_: Exception) {
                            onSourceReselected(null)
                        }
                    }
                }) {
                    Text(
                        if (!hasAllFilesAccess) strings.grantAllFilesBtn
                        else if (zh) "选择原文件" else "Select original"
                    )
                }
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

    var pendingKeyfileCallback by remember { mutableStateOf<((android.net.Uri?) -> Unit)?>(null) }
    val keyfilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        onExternalActivityFinished()
        val callback = pendingKeyfileCallback
        pendingKeyfileCallback = null
        callback?.invoke(uri)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = VaultVoidBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (selectedTab == 0 && isSelectionMode) {
                SelectionTopHeaderBar(
                    selectedCount = selectedFiles.size,
                    hasFiles = files.isNotEmpty(),
                    allSelected = allCurrentFilesSelected,
                    onExitSelection = { exitSelectionMode() },
                    onToggleSelectAll = {
                        selectedFileIds = if (allCurrentFilesSelected) {
                            emptySet()
                        } else {
                            files.map { it.id }.toSet()
                        }
                    }
                )
            } else {
                VaultTopHeaderBar(
                    spaceMeta = spaceMeta,
                    showMultiSelectAction = selectedTab == 0,
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onEnterMultiSelect = { isSelectionMode = true },
                    onToggleLanguage = onToggleLanguage,
                    onLockVault = onLockVault
                )
            }
        },
        bottomBar = {
            if (selectedTab == 0 && isSelectionMode) {
                VaultBatchActionBar(
                    hasSelection = selectedFiles.isNotEmpty(),
                    onCopyClick = {
                        pendingPickerRequest = FolderPickerMode.COPY to selectedFiles
                    },
                    onMoveClick = {
                        pendingPickerRequest = FolderPickerMode.MOVE to selectedFiles
                    },
                    onExportClick = {
                        val targets = selectedFiles
                        exitSelectionMode()
                        onExportFilesToPublic(targets)
                    },
                    onDeleteClick = {
                        pendingShredRequest = true to selectedFiles
                    }
                )
            } else {
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
            }
        },
        floatingActionButton = {
            if (selectedTab == 0 && !isSelectionMode) {
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
                    onNavigateToRoot = {
                        exitSelectionMode()
                        onNavigateToFolder(null)
                    },
                    onNavigateToCrumb = {
                        exitSelectionMode()
                        onNavigateToFolder(it)
                    },
                    onNewFolderClick = { showNewFolderDialog = true },
                    onNewNoteClick = { showNewNoteDialog = true }
                )

                AnimatedVisibility(visible = !hasAllFilesAccess && !permissionBannerDismissed && !isSelectionMode) {
                    Surface(
                        color = VaultAmberWarning.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, VaultAmberWarning.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("all_files_permission_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = VaultAmberWarning,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = strings.allFilesAccessMissingSub,
                                style = MaterialTheme.typography.labelSmall,
                                color = CalcTextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { requestAllFilesPermission() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("banner_grant_all_files_button")
                            ) {
                                Text(
                                    text = strings.grantAllFilesBtn,
                                    color = VaultAccentPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                            IconButton(
                                onClick = { permissionBannerDismissed = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = CalcTextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("vault_lazy_list"),
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
                                onClick = {
                                    exitSelectionMode()
                                    onNavigateToFolder(folder)
                                },
                                onMoreActionsClick = { folderForActions = folder }
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
                            val isSelected = fileEntity.id in selectedFileIds
                            EncryptedFileCard(
                                session = session,
                                fileEntity = fileEntity,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelectionMode) {
                                        toggleFileSelection(fileEntity.id)
                                    } else {
                                        onOpenFileViewer(fileEntity)
                                    }
                                },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isSelectionMode = true
                                    selectedFileIds = selectedFileIds + fileEntity.id
                                },
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
                    onRotateKeyfile = onRotateKeyfile,
                    onVerifyCurrentSequence = onVerifyCurrentSequence,
                    onCreateDecoySpace = onCreateDecoySpace,
                    onExtractKeyfileDigest = onExtractKeyfileDigest,
                    onLaunchKeyfilePicker = { onSelected ->
                        pendingKeyfileCallback = onSelected
                        keyfilePickerLauncher.launchWithVaultGuard(
                            arrayOf("*/*"),
                            onExternalActivityStarted,
                            onExternalActivityFinished
                        )
                    },
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
                    },
                    hasAllFilesAccess = hasAllFilesAccess,
                    onRequestAllFilesAccess = { requestAllFilesPermission() }
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
                hasAllFilesAccess = hasAllFilesAccess,
                isMoveAndWipeMode = isMoveAndWipeMode,
                onRequestAllFilesAccess = { requestAllFilesPermission() },
                onToggleMoveMode = { enabled ->
                    if (enabled && !hasAllFilesAccess) {
                        pendingAfterPermissionGranted = { isMoveAndWipeMode = true }
                        showPermissionRequiredDialog = true
                    } else {
                        isMoveAndWipeMode = enabled
                    }
                },
                onPickPhotosAndVideos = {
                    showImportSheet = false
                    if (hasAllFilesAccess) {
                        showLocalMediaPicker = true
                    } else if (isMoveAndWipeMode) {
                        pendingAfterPermissionGranted = {
                            isMoveAndWipeMode = true
                            showLocalMediaPicker = true
                        }
                        showPermissionRequiredDialog = true
                    } else {
                        photoPickerLauncher.launchWithVaultGuard(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                            onExternalActivityStarted,
                            onExternalActivityFinished
                        )
                    }
                },
                onPickAnyFilesLocalOrSaf = {
                    showImportSheet = false
                    if (hasAllFilesAccess) {
                        showLocalFileBrowser = true
                    } else if (isMoveAndWipeMode) {
                        pendingAfterPermissionGranted = {
                            isMoveAndWipeMode = true
                            showLocalFileBrowser = true
                        }
                        showPermissionRequiredDialog = true
                    } else {
                        safDocumentsLauncher.launchWithVaultGuard(
                            arrayOf("*/*"),
                            onExternalActivityStarted,
                            onExternalActivityFinished
                        )
                    }
                },
                onPickSystemSaf = {
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
            onRenameClick = {
                selectedFileForActions = null
                fileForRename = targetFile
            },
            onCopyClick = {
                selectedFileForActions = null
                pendingPickerRequest = FolderPickerMode.COPY to listOf(targetFile)
            },
            onMoveClick = {
                selectedFileForActions = null
                pendingPickerRequest = FolderPickerMode.MOVE to listOf(targetFile)
            },
            onExportToDownloads = {
                selectedFileForActions = null
                onExportFileToPublic(targetFile)
            },
            onSelectMultipleClick = {
                selectedFileForActions = null
                isSelectionMode = true
                selectedFileIds = selectedFileIds + targetFile.id
            },
            onDeleteClick = {
                selectedFileForActions = null
                pendingShredRequest = false to listOf(targetFile)
            }
        )
    }

    fileForRename?.let { targetFile ->
        RenameTextDialog(
            title = strings.renameFileTitle,
            fieldLabel = strings.encryptedFilenameLabel,
            initialValue = targetFile.originalName,
            onDismiss = { fileForRename = null },
            onConfirm = { newName ->
                fileForRename = null
                onRenameFile(targetFile, newName)
            }
        )
    }

    folderForActions?.let { targetFolder ->
        FolderActionsDialog(
            folder = targetFolder,
            onDismiss = { folderForActions = null },
            onOpenClick = {
                folderForActions = null
                exitSelectionMode()
                onNavigateToFolder(targetFolder)
            },
            onRenameClick = {
                folderForActions = null
                folderForRename = targetFolder
            },
            onDeleteClick = {
                folderForActions = null
                folderPendingDelete = targetFolder
            }
        )
    }

    folderForRename?.let { targetFolder ->
        RenameTextDialog(
            title = strings.renameFolderTitle,
            fieldLabel = strings.directoryNameLabel,
            initialValue = targetFolder.folderName,
            onDismiss = { folderForRename = null },
            onConfirm = { newName ->
                folderForRename = null
                onRenameFolder(targetFolder, newName)
            }
        )
    }

    folderPendingDelete?.let { targetFolder ->
        ConfirmDestructiveDialog(
            title = strings.confirmDeleteFolderTitle,
            message = strings.confirmDeleteFolderMessage(targetFolder.folderName),
            confirmLabel = strings.confirmShredBtn,
            onDismiss = { folderPendingDelete = null },
            onConfirm = {
                folderPendingDelete = null
                onDeleteFolder(targetFolder)
            }
        )
    }

    pendingPickerRequest?.let { (mode, targets) ->
        DestinationFolderPickerDialog(
            mode = mode,
            files = targets,
            allFolders = allFolders,
            currentFolderId = currentFolderId,
            onDismiss = { pendingPickerRequest = null },
            onConfirm = { targetFolderId ->
                pendingPickerRequest = null
                if (isSelectionMode) {
                    exitSelectionMode()
                }
                when (mode) {
                    FolderPickerMode.COPY -> onCopyFiles(targets, targetFolderId)
                    FolderPickerMode.MOVE -> onMoveFiles(targets, targetFolderId)
                }
            }
        )
    }

    pendingShredRequest?.let { (fromSelectionMode, targets) ->
        ConfirmDestructiveDialog(
            title = strings.confirmShredFilesTitle,
            message = strings.confirmShredFilesMessage(targets.size),
            confirmLabel = strings.confirmShredBtn,
            onDismiss = { pendingShredRequest = null },
            onConfirm = {
                pendingShredRequest = null
                if (fromSelectionMode) {
                    exitSelectionMode()
                    onShredFiles(targets)
                } else {
                    onShredFile(targets.first())
                }
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

    if (showPermissionRequiredDialog) {
        AllFilesPermissionRequiredDialog(
            onDismiss = {
                showPermissionRequiredDialog = false
                pendingAfterPermissionGranted = null
            },
            onConfirmGrant = {
                showPermissionRequiredDialog = false
                val after = pendingAfterPermissionGranted ?: { isMoveAndWipeMode = true }
                requestAllFilesPermission(after)
            },
            onContinueWithoutDelete = if (isMoveAndWipeMode) {
                {
                    showPermissionRequiredDialog = false
                    pendingAfterPermissionGranted = null
                    isMoveAndWipeMode = false
                }
            } else {
                null
            }
        )
    }

    if (showLocalFileBrowser) {
        LocalFileBrowserDialog(
            isMoveAndWipeMode = isMoveAndWipeMode,
            onDismiss = { showLocalFileBrowser = false },
            onConfirmUris = { uris ->
                showLocalFileBrowser = false
                if (uris.isNotEmpty()) {
                    onImportUris(uris, isMoveAndWipeMode)
                }
            }
        )
    }

    if (showLocalMediaPicker) {
        LocalMediaPickerDialog(
            isMoveAndWipeMode = isMoveAndWipeMode,
            onDismiss = { showLocalMediaPicker = false },
            onConfirmUris = { uris ->
                showLocalMediaPicker = false
                if (uris.isNotEmpty()) {
                    onImportUris(uris, isMoveAndWipeMode)
                }
            }
        )
    }
}
}

@Composable
private fun SelectionTopHeaderBar(
    selectedCount: Int,
    hasFiles: Boolean,
    allSelected: Boolean,
    onExitSelection: () -> Unit,
    onToggleSelectAll: () -> Unit
) {
    val strings = LocalAppStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VaultSurface)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("vault_selection_topbar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            IconButton(
                onClick = onExitSelection,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("selection_exit_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = strings.cancelBtn,
                    tint = CalcTextPrimary
                )
            }
            Text(
                text = strings.selectedCountLabel(selectedCount),
                style = MaterialTheme.typography.titleLarge,
                color = CalcTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("selection_count_text")
            )
        }

        TextButton(
            onClick = onToggleSelectAll,
            enabled = hasFiles,
            modifier = Modifier.testTag("selection_select_all_button")
        ) {
            Icon(
                imageVector = Icons.Default.SelectAll,
                contentDescription = null,
                tint = if (hasFiles) VaultAccentPrimary else CalcTextMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (allSelected) strings.deselectAllBtn else strings.selectAllBtn,
                color = if (hasFiles) VaultAccentPrimary else CalcTextMuted,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun VaultBatchActionBar(
    hasSelection: Boolean,
    onCopyClick: () -> Unit,
    onMoveClick: () -> Unit,
    onExportClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    Surface(
        color = VaultSurface,
        border = BorderStroke(1.dp, VaultBorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vault_batch_action_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
            BatchActionButton(
                icon = Icons.Default.ContentCopy,
                label = strings.copyToBtn,
                enabled = hasSelection,
                tint = VaultAccentPrimary,
                testTag = "batch_copy_button",
                onClick = onCopyClick,
                modifier = Modifier.weight(1f)
            )
            BatchActionButton(
                icon = Icons.AutoMirrored.Filled.DriveFileMove,
                label = strings.moveToBtn,
                enabled = hasSelection,
                tint = VaultAccentPrimary,
                testTag = "batch_move_button",
                onClick = onMoveClick,
                modifier = Modifier.weight(1f)
            )
            BatchActionButton(
                icon = Icons.Default.Download,
                label = strings.restoreBtn,
                enabled = hasSelection,
                tint = VaultEmeraldSuccess,
                testTag = "batch_export_button",
                onClick = onExportClick,
                modifier = Modifier.weight(1f)
            )
            BatchActionButton(
                icon = Icons.Default.DeleteForever,
                label = strings.csprngShredBtn,
                enabled = hasSelection,
                tint = CalcClearKeyText,
                testTag = "batch_delete_button",
                onClick = onDeleteClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
}

@Composable
private fun BatchActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val effectiveTint = if (enabled) tint else CalcTextMuted
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = effectiveTint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) CalcTextPrimary else CalcTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VaultTopHeaderBar(
    spaceMeta: VaultSpaceMetaEntity?,
    showMultiSelectAction: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onEnterMultiSelect: () -> Unit,
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
                if (showMultiSelectAction) {
                    IconButton(
                        onClick = onEnterMultiSelect,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(VaultSurfaceElevated)
                            .testTag("vault_multiselect_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = strings.multiSelectBtn,
                            tint = CalcTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

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
    onMoreActionsClick: () -> Unit
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
                onClick = onMoreActionsClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("folder_more_${folder.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Folder actions",
                    tint = CalcTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EncryptedFileCard(
    session: VaultSessionManager.ActiveVaultSession,
    fileEntity: VaultFileEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
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
        color = if (isSelected) VaultAccentPrimary.copy(alpha = 0.09f) else VaultSurface,
        border = if (isSelected) BorderStroke(1.2.dp, VaultAccentPrimary.copy(alpha = 0.7f)) else null,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
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

            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = VaultAccentPrimary,
                        checkmarkColor = Color(0xFF06202B),
                        uncheckedColor = CalcTextSecondary
                    ),
                    modifier = Modifier.testTag("file_checkbox_${fileEntity.id}")
                )
            } else {
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
    hasAllFilesAccess: Boolean,
    isMoveAndWipeMode: Boolean,
    onRequestAllFilesAccess: () -> Unit,
    onToggleMoveMode: (Boolean) -> Unit,
    onPickPhotosAndVideos: () -> Unit,
    onPickAnyFilesLocalOrSaf: () -> Unit,
    onPickSystemSaf: () -> Unit,
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
            onClick = { if (!hasAllFilesAccess) onRequestAllFilesAccess() },
            enabled = !hasAllFilesAccess,
            color = if (hasAllFilesAccess) VaultEmeraldSuccess.copy(alpha = 0.12f) else VaultAmberWarning.copy(alpha = 0.14f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                1.dp,
                if (hasAllFilesAccess) VaultEmeraldSuccess.copy(alpha = 0.4f) else VaultAmberWarning.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("import_all_files_status_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (hasAllFilesAccess) Icons.Default.CheckCircle else Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = if (hasAllFilesAccess) VaultEmeraldSuccess else VaultAmberWarning,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (hasAllFilesAccess) strings.allFilesAccessImportGrantedTip else strings.allFilesAccessImportMissingTip,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasAllFilesAccess) VaultEmeraldSuccess else VaultAmberWarning,
                    modifier = Modifier.weight(1f)
                )
                if (!hasAllFilesAccess) {
                    Text(
                        text = strings.grantAllFilesBtn,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = VaultAccentPrimary,
                        maxLines = 1
                    )
                }
            }
        }

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
            onClick = onPickAnyFilesLocalOrSaf,
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

        if (hasAllFilesAccess) {
            TextButton(
                onClick = onPickSystemSaf,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("import_system_saf_button")
            ) {
                Text(
                    text = strings.safSystemPickerBtn,
                    style = MaterialTheme.typography.labelMedium,
                    color = CalcTextSecondary,
                    maxLines = 1
                )
            }
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
private fun ActionMenuRow(
    icon: ImageVector,
    label: String,
    tint: Color = CalcTextPrimary,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = VaultSurfaceElevated.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(19.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = tint,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FileActionsDialog(
    fileEntity: VaultFileEntity,
    onDismiss: () -> Unit,
    onOpenInMemory: () -> Unit,
    onRenameClick: () -> Unit,
    onCopyClick: () -> Unit,
    onMoveClick: () -> Unit,
    onExportToDownloads: () -> Unit,
    onSelectMultipleClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultSurface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = fileEntity.originalName,
                    style = MaterialTheme.typography.titleMedium,
                    color = CalcTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = strings.blobInfoSummary(
                        fileEntity.blobFilename,
                        formatByteSize(fileEntity.originalSize),
                        fileEntity.mimeType
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = CalcTextSecondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HorizontalDivider(color = VaultBorderSubtle)
                Spacer(modifier = Modifier.height(2.dp))
                ActionMenuRow(
                    icon = Icons.Default.FolderOpen,
                    label = strings.streamInRamViewerBtn,
                    tint = VaultAccentPrimary,
                    testTag = "single_action_open",
                    onClick = onOpenInMemory
                )
                ActionMenuRow(
                    icon = Icons.Default.DriveFileRenameOutline,
                    label = strings.renameBtn,
                    testTag = "single_action_rename",
                    onClick = onRenameClick
                )
                ActionMenuRow(
                    icon = Icons.Default.ContentCopy,
                    label = strings.copyToBtn,
                    testTag = "single_action_copy",
                    onClick = onCopyClick
                )
                ActionMenuRow(
                    icon = Icons.AutoMirrored.Filled.DriveFileMove,
                    label = strings.moveToBtn,
                    testTag = "single_action_move",
                    onClick = onMoveClick
                )
                ActionMenuRow(
                    icon = Icons.Default.Download,
                    label = strings.restoreBtn,
                    tint = VaultEmeraldSuccess,
                    testTag = "single_action_export",
                    onClick = onExportToDownloads
                )
                ActionMenuRow(
                    icon = Icons.Default.Checklist,
                    label = strings.multiSelectBtn,
                    testTag = "single_action_select",
                    onClick = onSelectMultipleClick
                )
                ActionMenuRow(
                    icon = Icons.Default.DeleteForever,
                    label = strings.csprngShredBtn,
                    tint = CalcClearKeyText,
                    testTag = "single_action_delete",
                    onClick = onDeleteClick
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelBtn, maxLines = 1)
            }
        }
    )
}

@Composable
private fun FolderActionsDialog(
    folder: VaultFolderEntity,
    onDismiss: () -> Unit,
    onOpenClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultSurface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = folder.folderName,
                    style = MaterialTheme.typography.titleMedium,
                    color = CalcTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = strings.folderActionsTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = CalcTextSecondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HorizontalDivider(color = VaultBorderSubtle)
                Spacer(modifier = Modifier.height(2.dp))
                ActionMenuRow(
                    icon = Icons.Default.FolderOpen,
                    label = strings.openFolderBtn,
                    tint = VaultAccentPrimary,
                    testTag = "folder_action_open",
                    onClick = onOpenClick
                )
                ActionMenuRow(
                    icon = Icons.Default.DriveFileRenameOutline,
                    label = strings.renameBtn,
                    testTag = "folder_action_rename",
                    onClick = onRenameClick
                )
                ActionMenuRow(
                    icon = Icons.Default.DeleteForever,
                    label = strings.csprngShredBtn,
                    tint = CalcClearKeyText,
                    testTag = "folder_action_delete",
                    onClick = onDeleteClick
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelBtn, maxLines = 1)
            }
        }
    )
}

@Composable
private fun RenameTextDialog(
    title: String,
    fieldLabel: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    var text by remember(initialValue) { mutableStateOf(initialValue) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = VaultSurface,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, VaultBorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = CalcTextPrimary
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(fieldLabel) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_input_field")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(strings.cancelBtn, maxLines = 1)
                    }
                    Button(
                        onClick = { onConfirm(text.trim()) },
                        enabled = text.isNotBlank(),
                        modifier = Modifier.testTag("rename_confirm_button")
                    ) {
                        Text(strings.saveBtn, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDestructiveDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultSurface,
        title = { Text(title, color = CalcTextPrimary) },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = CalcTextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CalcClearKeyText,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("confirm_shred_button")
            ) {
                Text(confirmLabel, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_shred_button")
            ) {
                Text(strings.cancelBtn, maxLines = 1)
            }
        }
    )
}

@Composable
private fun DestinationFolderPickerDialog(
    mode: FolderPickerMode,
    files: List<VaultFileEntity>,
    allFolders: List<VaultFolderEntity>,
    currentFolderId: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit
) {
    val strings = LocalAppStrings.current
    val treeNodes = remember(allFolders) { buildFlattenedFolderTree(allFolders) }
    var chosenFolderId by remember { mutableStateOf(currentFolderId) }
    val canConfirm = mode == FolderPickerMode.COPY || files.any { it.folderId != chosenFolderId }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultSurface,
        title = {
            Text(
                text = strings.chooseDestinationTitle(mode == FolderPickerMode.MOVE),
                style = MaterialTheme.typography.titleLarge,
                color = CalcTextPrimary
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item(key = "root_target") {
                    PickerFolderRow(
                        label = strings.rootFolderLabel,
                        isRoot = true,
                        depth = 0,
                        isSelected = chosenFolderId == null,
                        isCurrentLocation = currentFolderId == null,
                        currentTagLabel = strings.currentFolderTag,
                        testTag = "picker_folder_root",
                        onClick = { chosenFolderId = null }
                    )
                }
                items(treeNodes, key = { "pick_${it.folder.id}" }) { node ->
                    PickerFolderRow(
                        label = node.folder.folderName,
                        isRoot = false,
                        depth = node.depth + 1,
                        isSelected = chosenFolderId == node.folder.id,
                        isCurrentLocation = currentFolderId == node.folder.id,
                        currentTagLabel = strings.currentFolderTag,
                        testTag = "picker_folder_${node.folder.id}",
                        onClick = { chosenFolderId = node.folder.id }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(chosenFolderId) },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultAccentPrimary,
                    contentColor = Color(0xFF06202B)
                ),
                modifier = Modifier.testTag("picker_confirm_button")
            ) {
                Text(
                    text = if (mode == FolderPickerMode.MOVE) strings.moveHereBtn else strings.copyHereBtn,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
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
private fun PickerFolderRow(
    label: String,
    isRoot: Boolean,
    depth: Int,
    isSelected: Boolean,
    isCurrentLocation: Boolean,
    currentTagLabel: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) VaultAccentPrimary.copy(alpha = 0.14f) else VaultSurfaceElevated.copy(alpha = 0.45f),
        border = if (isSelected) BorderStroke(1.dp, VaultAccentPrimary.copy(alpha = 0.7f)) else null,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = (12 + depth.coerceAtMost(5) * 14).dp,
                    end = 12.dp,
                    top = 9.dp,
                    bottom = 9.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isRoot) Icons.Default.Home else Icons.Default.Folder,
                contentDescription = null,
                tint = if (isSelected) VaultAccentPrimary else CalcTextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = CalcTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isCurrentLocation) {
                Text(
                    text = currentTagLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = CalcTextMuted
                )
            }
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) VaultAccentPrimary else CalcTextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
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
