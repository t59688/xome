package com.example.ui.vault

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.RootSecurityDetector
import com.example.crypto.VaultCryptoEngine
import com.example.crypto.VaultSessionManager
import com.example.data.VaultFileEntity
import com.example.data.VaultFolderEntity
import com.example.data.VaultSpaceMetaEntity
import com.example.ui.theme.CalcAccentOrange
import com.example.ui.theme.CalcClearKeyText
import com.example.ui.theme.CalcTextMuted
import com.example.ui.theme.CalcTextPrimary
import com.example.ui.theme.CalcTextSecondary
import com.example.ui.theme.VaultAmberWarning
import com.example.ui.theme.VaultBorderSubtle
import com.example.ui.theme.VaultCyanPrimary
import com.example.ui.theme.VaultEmeraldSuccess
import com.example.ui.theme.VaultPurpleDecoy
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceElevated
import com.example.ui.theme.VaultVoidBg
import com.example.viewmodel.XomeViewModel
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
    statusBanner: XomeViewModel.StatusBanner?,
    securityReport: RootSecurityDetector.EnvironmentSecurityReport,
    onNavigateToFolder: (VaultFolderEntity?) -> Unit,
    onNavigateUp: () -> Boolean,
    onSearchQueryChange: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (VaultFolderEntity) -> Unit,
    onImportUris: (List<android.net.Uri>, Boolean) -> Unit,
    onCreateEncryptedNote: (String, String) -> Unit,
    onOpenFileViewer: (VaultFileEntity) -> Unit,
    onExportFileToPublic: (VaultFileEntity) -> Unit,
    onShredFile: (VaultFileEntity) -> Unit,
    onRenameFile: (VaultFileEntity, String) -> Unit,
    onChangeSequence: (String, String, String, (Boolean, String) -> Unit) -> Unit,
    onCreateDecoySpace: (String, String, String, Boolean, Boolean, (Boolean, String) -> Unit) -> Unit,
    onExportSelfHealingArchive: (android.net.Uri) -> Unit,
    onImportSelfHealingArchive: (android.net.Uri) -> Unit,
    onLockVault: () -> Unit,
    onDismissBanner: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Files, 1 = Security & Spaces
    var showImportSheet by remember { mutableStateOf(false) }
    var isMoveAndWipeMode by rememberSaveable { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewNoteDialog by remember { mutableStateOf(false) }
    var selectedFileForActions by remember { mutableStateOf<VaultFileEntity?>(null) }

    // System back button handling: if in a subfolder, pop folder; if in Security tab, return to Files tab; else lock vault!
    BackHandler {
        if (selectedTab == 1) {
            selectedTab = 0
        } else if (!onNavigateUp()) {
            onLockVault()
        }
    }

    // Zero-permission Android Photo Picker (Multi-select images & videos)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 25)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportUris(uris, isMoveAndWipeMode)
        }
    }

    // System SAF Multi-Document Picker (All formats: Excel, PDF, ZIP, images, videos, etc.)
    val safDocumentsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportUris(uris, isMoveAndWipeMode)
        }
    }

    // SAF Self-Healing Archive Export / Import launchers
    val exportArchiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            onExportSelfHealingArchive(uri)
        }
    }

    val importArchiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportSelfHealingArchive(uri)
        }
    }

    Scaffold(
        containerColor = VaultVoidBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            VaultTopHeaderBar(
                session = session,
                spaceMeta = spaceMeta,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
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
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Encrypted Files") },
                    label = { Text("Encrypted VFS") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VaultCyanPrimary,
                        selectedTextColor = VaultCyanPrimary,
                        indicatorColor = VaultCyanPrimary.copy(alpha = 0.16f),
                        unselectedIconColor = CalcTextSecondary,
                        unselectedTextColor = CalcTextSecondary
                    ),
                    modifier = Modifier.testTag("vault_nav_files")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Security, contentDescription = "Security and Spaces") },
                    label = { Text("Spaces & Crypto") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VaultCyanPrimary,
                        selectedTextColor = VaultCyanPrimary,
                        indicatorColor = VaultCyanPrimary.copy(alpha = 0.16f),
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
                    containerColor = VaultCyanPrimary,
                    contentColor = Color(0xFF001F26),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.testTag("vault_fab_add")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Import or create")
                        Text(
                            text = "Encrypt / Add",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
            // Optional Status Banner
            AnimatedVisibility(visible = statusBanner != null) {
                statusBanner?.let { banner ->
                    Surface(
                        color = if (banner.isError) CalcClearKeyText.copy(alpha = 0.16f) else VaultEmeraldSuccess.copy(alpha = 0.16f),
                        border = BorderStroke(
                            1.dp,
                            if (banner.isError) CalcClearKeyText.copy(alpha = 0.5f) else VaultEmeraldSuccess.copy(alpha = 0.5f)
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

            // Active Streaming Encryption Progress Bar
            AnimatedVisibility(visible = importProgress.isImporting) {
                Surface(
                    color = VaultSurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, VaultCyanPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (importProgress.isMoveMode) {
                                    "MOVING & SHREDDING ORIGINALS (${importProgress.currentIndex}/${importProgress.totalCount})"
                                } else {
                                    "STREAMING 64KB AES-256-GCM (${importProgress.currentIndex}/${importProgress.totalCount})"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = VaultCyanPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = importProgress.currentFileName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CalcTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = VaultCyanPrimary,
                            trackColor = VaultBorderSubtle
                        )
                    }
                }
            }

            if (selectedTab == 0) {
                // Breadcrumb Path Bar
                FolderBreadcrumbBar(
                    breadcrumbs = breadcrumbs,
                    onNavigateToRoot = { onNavigateToFolder(null) },
                    onNavigateToCrumb = { onNavigateToFolder(it) },
                    onNewFolderClick = { showNewFolderDialog = true },
                    onNewNoteClick = { showNewNoteDialog = true }
                )

                // Main Virtual File System Content
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (folders.isNotEmpty()) {
                        item {
                            Text(
                                text = "ENCRYPTED DIRECTORIES (${folders.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = CalcTextMuted
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

                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "AES-256-GCM CHUNKED BLOBS (${files.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = CalcTextMuted
                        )
                    }

                    if (files.isEmpty()) {
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
                    onChangeSequence = onChangeSequence,
                    onCreateDecoySpace = onCreateDecoySpace,
                    onExportSelfHealingArchive = {
                        exportArchiveLauncher.launch("sys_vault_data_${session.spaceId}.xomevault.zip")
                    },
                    onImportSelfHealingArchive = {
                        importArchiveLauncher.launch(arrayOf("*/*"))
                    }
                )
            }
        }
    }

    // Import & Authoring Modal Bottom Sheet
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
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                onPickAnyFilesSaf = {
                    showImportSheet = false
                    safDocumentsLauncher.launch(arrayOf("*/*"))
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

    // File Action Sheet (Rename, Decrypt & Export to Public Storage, Shred)
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
    session: VaultSessionManager.ActiveVaultSession,
    spaceMeta: VaultSpaceMetaEntity?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onLockVault: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VaultSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = spaceMeta?.spaceAlias ?: "Encrypted Space",
                        style = MaterialTheme.typography.titleLarge,
                        color = CalcTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        color = if (spaceMeta?.isDecoy == true) VaultPurpleDecoy.copy(alpha = 0.2f) else VaultCyanPrimary.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${session.spaceId}.header",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (spaceMeta?.isDecoy == true) VaultPurpleDecoy else VaultCyanPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "FLAG_SECURE ACTIVE • SQLCIPHER AES-256 • AUTO-LOCK ON STOP",
                    style = MaterialTheme.typography.labelSmall,
                    color = VaultEmeraldSuccess
                )
            }

            Surface(
                onClick = onLockVault,
                color = CalcClearKeyText.copy(alpha = 0.16f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CalcClearKeyText.copy(alpha = 0.45f)),
                modifier = Modifier.testTag("vault_instant_lock_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock Vault Immediately",
                        tint = CalcClearKeyText,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "LOCK",
                        style = MaterialTheme.typography.labelLarge,
                        color = CalcClearKeyText,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Search field inside SQLCipher VFS
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search encrypted filenames inside SQLCipher...") },
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
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VaultCyanPrimary,
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

@Composable
private fun FolderBreadcrumbBar(
    breadcrumbs: List<VaultFolderEntity>,
    onNavigateToRoot: () -> Unit,
    onNavigateToCrumb: (VaultFolderEntity) -> Unit,
    onNewFolderClick: () -> Unit,
    onNewNoteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                color = if (breadcrumbs.isEmpty()) VaultCyanPrimary.copy(alpha = 0.16f) else VaultSurfaceElevated,
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
                        contentDescription = "Root folder",
                        tint = if (breadcrumbs.isEmpty()) VaultCyanPrimary else CalcTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "/root",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (breadcrumbs.isEmpty()) VaultCyanPrimary else CalcTextSecondary
                    )
                }
            }

            breadcrumbs.forEachIndexed { idx, crumb ->
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = CalcTextMuted,
                    modifier = Modifier.size(18.dp)
                )
                val isLast = idx == breadcrumbs.lastIndex
                Surface(
                    onClick = { onNavigateToCrumb(crumb) },
                    color = if (isLast) VaultCyanPrimary.copy(alpha = 0.16f) else VaultSurfaceElevated,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = crumb.folderName,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isLast) VaultCyanPrimary else CalcTextSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(
                onClick = onNewFolderClick,
                modifier = Modifier.testTag("vault_new_folder_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = "New encrypted folder",
                    tint = VaultCyanPrimary
                )
            }
            IconButton(
                onClick = onNewNoteClick,
                modifier = Modifier.testTag("vault_new_note_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                    contentDescription = "New encrypted note",
                    tint = CalcAccentOrange
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
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, VaultBorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("folder_item_${folder.folderName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
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
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(VaultCyanPrimary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = VaultCyanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = folder.folderName,
                        style = MaterialTheme.typography.titleMedium,
                        color = CalcTextPrimary
                    )
                    Text(
                        text = "SQLCipher Directory Node #${folder.id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CalcTextMuted
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = "Delete folder",
                    tint = CalcTextMuted,
                    modifier = Modifier.size(20.dp)
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
        SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(fileEntity.importedAt))
    }

    Surface(
        onClick = onClick,
        color = VaultSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, VaultBorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("file_item_${fileEntity.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // In-Memory Decrypted Thumbnail or Format Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
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
                        tint = VaultCyanPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = fileEntity.originalName,
                    style = MaterialTheme.typography.titleMedium,
                    color = CalcTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = VaultEmeraldSuccess.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "AES-256-GCM",
                            style = MaterialTheme.typography.labelSmall,
                            color = VaultEmeraldSuccess,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    Text(
                        text = "${formatByteSize(fileEntity.originalSize)} • $dateStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = CalcTextSecondary
                    )
                }
                Text(
                    text = "blobs/${fileEntity.blobFilename.take(16)}….dat",
                    style = MaterialTheme.typography.labelSmall,
                    color = CalcTextMuted
                )
            }

            IconButton(
                onClick = onMoreActionsClick,
                modifier = Modifier.testTag("file_more_${fileEntity.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "File actions",
                    tint = CalcTextSecondary
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
    Surface(
        color = VaultSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, VaultBorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(VaultCyanPrimary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = VaultCyanPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "Zero-Leakage Encrypted Directory",
                style = MaterialTheme.typography.titleMedium,
                color = CalcTextPrimary
            )
            Text(
                text = "Import photos, videos, PDFs, Excel sheets, or archives. Every file is split into 64KB chunks encrypted with a unique 256-bit DEK and stored in blobs/*.dat.",
                style = MaterialTheme.typography.bodyMedium,
                color = CalcTextSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onImportClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VaultCyanPrimary,
                        contentColor = Color(0xFF001F26)
                    )
                ) {
                    Text("Import Files / Media", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onNewNoteClick,
                    border = BorderStroke(1.dp, VaultBorderSubtle)
                ) {
                    Text("New Encrypted Note", color = CalcTextPrimary)
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Streaming 64KB AES-256-GCM Ingestion",
            style = MaterialTheme.typography.headlineMedium,
            color = CalcTextPrimary
        )

        // Copy vs Move Toggle Card
        Surface(
            color = VaultSurfaceElevated,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                1.dp,
                if (isMoveAndWipeMode) CalcAccentOrange.copy(alpha = 0.6f) else VaultBorderSubtle
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isMoveAndWipeMode) "Mode: MOVE (Delete System Original)" else "Mode: COPY (Keep System Original)",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isMoveAndWipeMode) CalcAccentOrange else VaultEmeraldSuccess
                    )
                    Text(
                        text = if (isMoveAndWipeMode) {
                            "Deletes original document & refreshes MediaScanner ONLY after .dat fsync and SQLCipher commit succeed."
                        } else {
                            "Encrypts stream into blobs/UUID.dat while preserving original source file."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = CalcTextSecondary
                    )
                }
                Switch(
                    checked = isMoveAndWipeMode,
                    onCheckedChange = onToggleMoveMode,
                    colors = SwitchDefaults.colors(checkedThumbColor = CalcAccentOrange),
                    modifier = Modifier.testTag("import_move_mode_switch")
                )
            }
        }

        Button(
            onClick = onPickPhotosAndVideos,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VaultCyanPrimary,
                contentColor = Color(0xFF001F26)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("import_photos_button")
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Gallery Fast Multi-Picker (Photos & Videos)", fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = onPickAnyFilesSaf,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VaultSurfaceElevated,
                contentColor = CalcTextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("import_saf_files_button")
        ) {
            Icon(Icons.Default.UploadFile, contentDescription = null, tint = CalcAccentOrange)
            Spacer(modifier = Modifier.width(10.dp))
            Text("SAF Document Picker (PDF, Excel, ZIP, All Formats)", fontWeight = FontWeight.SemiBold)
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
                    .height(48.dp)
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = VaultCyanPrimary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Folder", color = CalcTextPrimary)
            }

            OutlinedButton(
                onClick = onCreateEncryptedNote,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, VaultBorderSubtle),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, tint = CalcAccentOrange)
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Secret Note", color = CalcTextPrimary)
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
    var editedName by remember(fileEntity.id) { mutableStateOf(fileEntity.originalName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultSurface,
        title = {
            Text(
                text = "Encrypted Blob Operations",
                style = MaterialTheme.typography.titleLarge,
                color = CalcTextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Physical Blob: blobs/${fileEntity.blobFilename}\nPlain Size: ${formatByteSize(fileEntity.originalSize)} (${fileEntity.mimeType})",
                    style = MaterialTheme.typography.labelSmall,
                    color = CalcTextSecondary
                )
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("Encrypted Filename (SQLCipher)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onRename(editedName) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rename")
                    }
                    OutlinedButton(
                        onClick = onExportToDownloads,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = VaultCyanPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restore", color = VaultCyanPrimary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenInMemory,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultCyanPrimary,
                    contentColor = Color(0xFF001F26)
                )
            ) {
                Text("Stream in RAM Viewer", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onShred) {
                Text("CSPRNG Shred", color = CalcClearKeyText, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultSurface,
        title = { Text("Create Encrypted Directory", color = CalcTextPrimary) },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("Directory Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(folderName) },
                enabled = folderName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CreateEncryptedNoteDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("Classified_Memo.md") }
    var content by remember {
        mutableStateOf("# Classified Vault Record\n\nAuthored directly in RAM and encrypted via 64KB AES-256-GCM chunks.")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultSurface,
        title = { Text("Author Encrypted Note (RAM -> .dat)", color = CalcTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Filename") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Secret Content (Plaintext never touches disk)") },
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
                Text("Encrypt to .dat")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
