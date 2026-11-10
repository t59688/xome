package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.crypto.VaultSessionManager
import com.example.ui.calculator.DisguisedCalculatorScreen
import com.example.ui.theme.CalcChassisBg
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.vault.VaultSpaceScreen
import com.example.ui.viewer.InMemoryDocumentViewerDialog
import com.example.ui.viewer.InMemoryImageViewerOverlay
import com.example.viewmodel.XomeViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: XomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Module 6.1: Mandatory system-level screenshot, screen-recording & recents preview protection
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // Module 6.2: Instant lifecycle lock on Home button, task switch, or screen-off
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                VaultSessionManager.lock()
            }
        })

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CalcChassisBg
                ) {
                    XomeRootHost(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun XomeRootHost(viewModel: XomeViewModel) {
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val calcState by viewModel.calcState.collectAsStateWithLifecycle()
    val needsOnboarding by viewModel.needsOnboarding.collectAsStateWithLifecycle()
    val isProvisioningOnboarding by viewModel.isProvisioningOnboarding.collectAsStateWithLifecycle()
    val onboardingError by viewModel.onboardingError.collectAsStateWithLifecycle()
    val totalSpaces by viewModel.totalProvisionedSpaces.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val spaceMeta by viewModel.currentSpaceMeta.collectAsStateWithLifecycle()
    val folders by viewModel.currentFolders.collectAsStateWithLifecycle()
    val files by viewModel.currentFiles.collectAsStateWithLifecycle()
    val breadcrumbs by viewModel.folderBreadcrumbs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val totalFilesCount by viewModel.totalFileCount.collectAsStateWithLifecycle()
    val totalPlainBytes by viewModel.totalPlainBytes.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()

    val activeImageViewerFileId by viewModel.activeImageViewerFileId.collectAsStateWithLifecycle()
    val activeDocViewerFile by viewModel.activeDocumentViewerFile.collectAsStateWithLifecycle()
    val decryptedDocText by viewModel.decryptedDocumentText.collectAsStateWithLifecycle()

    Crossfade(targetState = activeSession, label = "root_vault_switch") { session ->
        if (session == null) {
            DisguisedCalculatorScreen(
                calcState = calcState,
                needsOnboarding = needsOnboarding,
                isProvisioningOnboarding = isProvisioningOnboarding,
                onboardingError = onboardingError,
                totalProvisionedSpaces = totalSpaces,
                statusBanner = statusBanner,
                onInputToken = viewModel::onCalculatorInput,
                onClear = viewModel::onCalculatorClear,
                onBackspace = viewModel::onCalculatorBackspace,
                onEquals = viewModel::onCalculatorEqualsPressed,
                onToggleDegRad = viewModel::toggleDegreeRadianMode,
                onToggleSciPad = viewModel::toggleScientificPad,
                onToggleHistory = viewModel::toggleCalculatorHistory,
                onClearHistory = viewModel::clearCalculatorHistory,
                onProvisionPrimarySpace = viewModel::provisionInitialPrimarySpace,
                onDismissOnboarding = viewModel::dismissOnboardingToCalculator,
                onOpenOnboarding = viewModel::openOnboardingSetup,
                onImportSelfHealingArchive = viewModel::importSelfHealingBundleFromUri
            )
        } else {
            val imageFilesInFolder = files.filter { it.mimeType.startsWith("image/") }
            when {
                activeImageViewerFileId != null -> {
                    InMemoryImageViewerOverlay(
                        imageFiles = imageFilesInFolder,
                        initialFileId = activeImageViewerFileId!!,
                        onClose = viewModel::closeImageViewer,
                        onExportFile = viewModel::exportFileToPublicStorage,
                        onShredFile = viewModel::shredVaultFile
                    )
                }
                activeDocViewerFile != null -> {
                    InMemoryDocumentViewerDialog(
                        fileEntity = activeDocViewerFile!!,
                        decryptedContent = decryptedDocText ?: "",
                        onClose = viewModel::closeDocumentViewer,
                        onExportFile = viewModel::exportFileToPublicStorage,
                        onShredFile = viewModel::shredVaultFile
                    )
                }
                else -> {
                    VaultSpaceScreen(
                        session = session,
                        spaceMeta = spaceMeta,
                        folders = folders,
                        files = files,
                        breadcrumbs = breadcrumbs,
                        searchQuery = searchQuery,
                        totalSpacesCount = totalSpaces,
                        totalFilesCount = totalFilesCount,
                        totalPlainBytes = totalPlainBytes,
                        importProgress = importProgress,
                        statusBanner = statusBanner,
                        securityReport = viewModel.environmentSecurityReport,
                        onNavigateToFolder = viewModel::navigateToFolder,
                        onNavigateUp = viewModel::navigateUpFolder,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onCreateFolder = viewModel::createNewFolder,
                        onDeleteFolder = viewModel::deleteFolder,
                        onImportUris = viewModel::importSelectedUris,
                        onCreateEncryptedNote = viewModel::createNewEncryptedNote,
                        onOpenFileViewer = viewModel::openFileInMemoryViewer,
                        onExportFileToPublic = viewModel::exportFileToPublicStorage,
                        onShredFile = viewModel::shredVaultFile,
                        onRenameFile = viewModel::renameVaultFile,
                        onChangeSequence = viewModel::changeCurrentSpaceSequence,
                        onCreateDecoySpace = viewModel::createIndependentDecoySpace,
                        onExportSelfHealingArchive = viewModel::exportSelfHealingBundleToUri,
                        onImportSelfHealingArchive = viewModel::importSelfHealingBundleFromUri,
                        onLockVault = viewModel::lockVaultImmediately,
                        onDismissBanner = viewModel::dismissBanner
                    )
                }
            }
        }
    }
}
