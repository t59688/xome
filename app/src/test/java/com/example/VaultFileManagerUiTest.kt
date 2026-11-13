package com.example

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.RootSecurityDetector
import com.example.crypto.VaultCryptoEngine
import com.example.crypto.VaultSessionManager
import com.example.data.VaultDatabase
import com.example.data.VaultFileEntity
import com.example.data.VaultFolderEntity
import com.example.data.VaultSpaceMetaEntity
import com.example.storage.AllFilesPermissionHelper
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.EnglishAppStrings
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.vault.VaultSpaceScreen
import com.example.update.UpdateRepository
import com.example.viewmodel.XomeViewModel
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class VaultFileManagerUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var database: VaultDatabase
    private lateinit var session: VaultSessionManager.ActiveVaultSession
    private lateinit var updateRepository: UpdateRepository

    private val folder10 = VaultFolderEntity(id = 10L, parentId = null, folderName = "Work")
    private val folder20 = VaultFolderEntity(id = 20L, parentId = 10L, folderName = "Archive")
    private val file1 = VaultFileEntity(
        id = 1L,
        folderId = null,
        originalName = "alpha.txt",
        mimeType = "text/plain",
        originalSize = 120L,
        blobFilename = "blob-1.dat",
        encryptedThumbnail = null,
        originalUriPath = ""
    )
    private val file2 = VaultFileEntity(
        id = 2L,
        folderId = null,
        originalName = "beta.jpg",
        mimeType = "image/jpeg",
        originalSize = 2400L,
        blobFilename = "blob-2.dat",
        encryptedThumbnail = null,
        originalUriPath = ""
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val container = File(context.cacheDir, "ui_test_space.xome")
        session = VaultSessionManager.ActiveVaultSession(
            spaceId = "ui_test_space",
            containerFile = container,
            containerInfo = VaultCryptoEngine.SpaceContainerInfo("ui_test_space", container, ByteArray(16)),
            kdfProfile = VaultCryptoEngine.Argon2Profile.COMPACT,
            rawMasterKey = ByteArray(32) { 1 },
            database = database
        )
        updateRepository = UpdateRepository(context) { null }
    }

    @After
    fun tearDown() {
        runCatching { database.close() }
    }

    private fun mountScreen(
        onOpenFileViewer: (VaultFileEntity) -> Unit = {},
        onExportFileToPublic: (VaultFileEntity) -> Unit = {},
        onExportFilesToPublic: (List<VaultFileEntity>) -> Unit = {},
        onCopyFiles: (List<VaultFileEntity>, Long?) -> Unit = { _, _ -> },
        onMoveFiles: (List<VaultFileEntity>, Long?) -> Unit = { _, _ -> },
        onShredFile: (VaultFileEntity) -> Unit = {},
        onShredFiles: (List<VaultFileEntity>) -> Unit = {},
        onRenameFile: (VaultFileEntity, String) -> Unit = { _, _ -> },
        onRenameFolder: (VaultFolderEntity, String) -> Unit = { _, _ -> },
        onDeleteFolder: (VaultFolderEntity) -> Unit = {}
    ) {
        compose.setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalAppStrings provides EnglishAppStrings) {
                    VaultSpaceScreen(
                        session = session,
                        spaceMeta = VaultSpaceMetaEntity(
                            spaceId = "ui_test_space",
                            spaceAlias = "Primary",
                            isDecoy = false
                        ),
                        folders = listOf(folder10),
                        files = listOf(file1, file2),
                        breadcrumbs = emptyList(),
                        allFolders = listOf(folder10, folder20),
                        searchQuery = "",
                        totalSpacesCount = 1,
                        totalFilesCount = 2,
                        totalPlainBytes = 2520L,
                        importProgress = XomeViewModel.ImportProgressState(),
                        pendingDeleteHandles = emptyList(),
                        pendingSystemDeleteIntent = null,
                        statusBanner = null,
                        securityReport = RootSecurityDetector.EnvironmentSecurityReport(
                            isRootOrHookDetected = false,
                            suBinaryFound = false,
                            magiskDetected = false,
                            xposedDetected = false,
                            testKeysBuild = false,
                            findings = emptyList()
                        ),
                        flagSecureEnabled = false,
                        isStreamingEmulator = false,
                        updateRepository = updateRepository,
                        onToggleFlagSecure = {},
                        onToggleLanguage = {},
                        onSelectLanguage = { _: AppLanguage -> },
                        onNavigateToFolder = {},
                        onNavigateUp = { false },
                        onSearchQueryChange = {},
                        onCreateFolder = {},
                        onRenameFolder = onRenameFolder,
                        onDeleteFolder = onDeleteFolder,
                        onImportUris = { _, _ -> },
                        onConfirmDeleteOriginals = {},
                        onCancelDeleteOriginals = {},
                        onSystemDeleteIntentCompleted = {},
                        onCreateEncryptedNote = { _, _ -> },
                        onOpenFileViewer = onOpenFileViewer,
                        onExportFileToPublic = onExportFileToPublic,
                        onExportFilesToPublic = onExportFilesToPublic,
                        onCopyFiles = onCopyFiles,
                        onMoveFiles = onMoveFiles,
                        onShredFile = onShredFile,
                        onShredFiles = onShredFiles,
                        onRenameFile = onRenameFile,
                        onChangeSequence = { _, _, _, cb -> cb(true, "") },
                        onRotateKeyfile = { _, _, cb -> cb(true, "") },
                        onVerifyCurrentSequence = { _, cb -> cb(true) },
                        onCreateDecoySpace = { _, _, _, _, _, _, _, cb -> cb(true, "") },
                        onExtractKeyfileDigest = { Result.failure(IllegalStateException("unused")) },
                        onExportSelfHealingArchive = {},
                        onImportSelfHealingArchive = {},
                        onLockVault = {},
                        onDismissBanner = {},
                        onExternalActivityStarted = {},
                        onExternalActivityFinished = {}
                    )
                }
            }
        }
    }

    @Test
    fun `long press enters selection mode, toggles select all, and Back exits selection mode`() {
        mountScreen()

        compose.onNodeWithTag("file_item_1").performTouchInput { longClick() }
        compose.onNodeWithTag("vault_selection_topbar").assertIsDisplayed()
        compose.onNodeWithTag("vault_batch_action_bar").assertIsDisplayed()
        compose.onNodeWithTag("vault_fab_add").assertDoesNotExist()
        compose.onNodeWithTag("selection_count_text").assertTextContains("1 selected")

        compose.onNodeWithTag("file_item_2").performClick()
        compose.onNodeWithTag("selection_count_text").assertTextContains("2 selected")

        // Select All -> all currently selected -> button toggles to Deselect All -> click clears
        compose.onNodeWithTag("selection_select_all_button").performClick()
        compose.onNodeWithTag("selection_count_text").assertTextContains("0 selected")
        compose.onNodeWithTag("selection_select_all_button").performClick()
        compose.onNodeWithTag("selection_count_text").assertTextContains("2 selected")

        // System back exits selection mode instead of closing vault
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("vault_selection_topbar").assertDoesNotExist()
        compose.onNodeWithTag("vault_fab_add").assertIsDisplayed()
    }

    @Test
    fun `batch copy move export and delete invoke callbacks with exact selected items`() {
        var copiedArgs: Pair<List<Long>, Long?>? = null
        var movedArgs: Pair<List<Long>, Long?>? = null
        var exportedIds: List<Long>? = null
        var shreddedIds: List<Long>? = null

        mountScreen(
            onCopyFiles = { list, targetId -> copiedArgs = list.map { it.id } to targetId },
            onMoveFiles = { list, targetId -> movedArgs = list.map { it.id } to targetId },
            onExportFilesToPublic = { list -> exportedIds = list.map { it.id } },
            onShredFiles = { list -> shreddedIds = list.map { it.id } }
        )

        // Enter multi-select via top-bar checklist button and select both files
        compose.onNodeWithTag("vault_multiselect_button").performClick()
        compose.onNodeWithTag("selection_select_all_button").performClick()

        // Batch Copy -> even same folder (Root) keeps Copy Here enabled, then choose child folder 20
        compose.onNodeWithTag("batch_copy_button").performClick()
        compose.onNodeWithTag("picker_confirm_button").assertIsEnabled()
        compose.onNodeWithTag("picker_folder_20").performClick()
        compose.onNodeWithTag("picker_confirm_button").performClick()
        assertEquals(listOf(1L, 2L) to 20L, copiedArgs)
        compose.onNodeWithTag("vault_selection_topbar").assertDoesNotExist()

        // Batch Move -> Root (current location) disables Move Here, selecting folder 10 enables it
        compose.onNodeWithTag("vault_multiselect_button").performClick()
        compose.onNodeWithTag("file_item_1").performClick()
        compose.onNodeWithTag("batch_move_button").performClick()
        compose.onNodeWithTag("picker_confirm_button").assertIsNotEnabled()
        compose.onNodeWithTag("picker_folder_10").performClick()
        compose.onNodeWithTag("picker_confirm_button").assertIsEnabled().performClick()
        assertEquals(listOf(1L) to 10L, movedArgs)

        // Batch Export
        compose.onNodeWithTag("vault_multiselect_button").performClick()
        compose.onNodeWithTag("selection_select_all_button").performClick()
        compose.onNodeWithTag("batch_export_button").performClick()
        assertEquals(listOf(1L, 2L), exportedIds)
        compose.onNodeWithTag("vault_selection_topbar").assertDoesNotExist()

        // Batch Delete requires confirmation dialog
        compose.onNodeWithTag("vault_multiselect_button").performClick()
        compose.onNodeWithTag("file_item_2").performClick()
        compose.onNodeWithTag("batch_delete_button").performClick()
        compose.onNodeWithTag("confirm_shred_button").performClick()
        assertEquals(listOf(2L), shreddedIds)
    }

    @Test
    fun `single file and folder action menus trigger rename copy move export multi-select and delete`() {
        var openedId: Long? = null
        var renamedFile: Pair<Long, String>? = null
        var copiedSingle: Pair<List<Long>, Long?>? = null
        var movedSingle: Pair<List<Long>, Long?>? = null
        var exportedSingleId: Long? = null
        var shreddedSingleId: Long? = null
        var renamedFolder: Pair<Long, String>? = null
        var deletedFolderId: Long? = null

        mountScreen(
            onOpenFileViewer = { openedId = it.id },
            onRenameFile = { f, name -> renamedFile = f.id to name },
            onCopyFiles = { list, targetId -> copiedSingle = list.map { it.id } to targetId },
            onMoveFiles = { list, targetId -> movedSingle = list.map { it.id } to targetId },
            onExportFileToPublic = { exportedSingleId = it.id },
            onShredFile = { shreddedSingleId = it.id },
            onRenameFolder = { folder, name -> renamedFolder = folder.id to name },
            onDeleteFolder = { deletedFolderId = it.id }
        )

        // Single open
        compose.onNodeWithTag("file_more_1").performClick()
        compose.onNodeWithTag("single_action_open").performClick()
        assertEquals(1L, openedId)

        // Single rename
        compose.onNodeWithTag("file_more_1").performClick()
        compose.onNodeWithTag("single_action_rename").performClick()
        compose.onNodeWithTag("rename_input_field")
            .performSemanticsAction(SemanticsActions.SetText) { it.invoke(AnnotatedString("renamed_alpha.txt")) }
        compose.onNodeWithTag("rename_confirm_button").performClick()
        assertEquals(1L to "renamed_alpha.txt", renamedFile)

        // Single copy & move
        compose.onNodeWithTag("file_more_1").performClick()
        compose.onNodeWithTag("single_action_copy").performClick()
        compose.onNodeWithTag("picker_folder_10").performClick()
        compose.onNodeWithTag("picker_confirm_button").performClick()
        assertEquals(listOf(1L) to 10L, copiedSingle)

        compose.onNodeWithTag("file_more_1").performClick()
        compose.onNodeWithTag("single_action_move").performClick()
        compose.onNodeWithTag("picker_folder_20").performClick()
        compose.onNodeWithTag("picker_confirm_button").performClick()
        assertEquals(listOf(1L) to 20L, movedSingle)

        // Single export
        compose.onNodeWithTag("file_more_1").performClick()
        compose.onNodeWithTag("single_action_export").performClick()
        assertEquals(1L, exportedSingleId)

        // Single -> enter multi-select with that file selected
        compose.onNodeWithTag("file_more_2").performClick()
        compose.onNodeWithTag("single_action_select").performClick()
        compose.onNodeWithTag("vault_selection_topbar").assertIsDisplayed()
        compose.onNodeWithTag("selection_count_text").assertTextContains("1 selected")
        compose.onNodeWithTag("selection_exit_button").performClick()

        // Single delete with confirmation
        compose.onNodeWithTag("file_more_1").performClick()
        compose.onNodeWithTag("single_action_delete").performClick()
        compose.onNodeWithTag("confirm_shred_button").performClick()
        assertEquals(1L, shreddedSingleId)

        // Folder rename & delete
        compose.onNodeWithTag("folder_more_10").performClick()
        compose.onNodeWithTag("folder_action_rename").performClick()
        compose.onNodeWithTag("rename_input_field")
            .performSemanticsAction(SemanticsActions.SetText) { it.invoke(AnnotatedString("Projects")) }
        compose.onNodeWithTag("rename_confirm_button").performClick()
        assertEquals(10L to "Projects", renamedFolder)

        compose.onNodeWithTag("folder_more_10").performClick()
        compose.onNodeWithTag("folder_action_delete").performClick()
        compose.onNodeWithTag("confirm_shred_button").performClick()
        assertEquals(10L, deletedFolderId)
        assertTrue(shreddedSingleId == 1L && deletedFolderId == 10L)
    }

    @Test
    fun `missing all files access prompts permission before move mode, and granted opens local browser`() {
        AllFilesPermissionHelper.testAllFilesAccessOverride = false
        try {
            mountScreen()
            compose.onNodeWithTag("all_files_permission_banner").assertIsDisplayed()

            compose.onNodeWithTag("vault_fab_add").performClick()
            compose.onNodeWithTag("import_all_files_status_card").assertIsDisplayed()
            compose.onNodeWithTag("import_move_mode_switch").performClick()
            compose.onNodeWithTag("grant_all_files_dialog_button").assertIsDisplayed()
        } finally {
            AllFilesPermissionHelper.testAllFilesAccessOverride = null
        }
    }

    @Test
    fun `granted all files access hides banner, allows move mode, and opens built-in local file browser`() {
        AllFilesPermissionHelper.testAllFilesAccessOverride = true
        try {
            mountScreen()
            compose.onNodeWithTag("all_files_permission_banner").assertDoesNotExist()

            compose.onNodeWithTag("vault_fab_add").performClick()
            compose.onNodeWithTag("import_move_mode_switch").performClick()
            compose.onNodeWithTag("grant_all_files_dialog_button").assertDoesNotExist()
            compose.onNodeWithTag("import_system_saf_button").assertIsDisplayed()

            compose.onNodeWithTag("import_saf_files_button").performClick()
            compose.onNodeWithTag("local_file_browser_dialog").assertIsDisplayed()
            compose.onNodeWithTag("local_browser_confirm_button").assertIsDisplayed()
        } finally {
            AllFilesPermissionHelper.testAllFilesAccessOverride = null
        }
    }

    @Test
    fun `granted all files access opens built-in local media picker and displays confirmation button`() {
        AllFilesPermissionHelper.testAllFilesAccessOverride = true
        try {
            mountScreen()
            compose.onNodeWithTag("vault_fab_add").performClick()
            compose.onNodeWithTag("import_photos_button").performClick()
            compose.onNodeWithTag("local_media_picker_dialog").assertIsDisplayed()
            compose.onNodeWithTag("local_media_confirm_button").assertIsDisplayed()
        } finally {
            AllFilesPermissionHelper.testAllFilesAccessOverride = null
        }
    }
}
