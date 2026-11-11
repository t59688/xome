package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.crypto.VaultExternalUiLockPolicy
import com.example.crypto.VaultSessionManager
import com.example.ui.calculator.DisguisedCalculatorScreen
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.stringsForLanguage
import com.example.ui.theme.CalcChassisBg
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.update.AppUpdateHost
import com.example.update.pollForAppUpdates
import kotlinx.coroutines.launch
import com.example.ui.vault.VaultSpaceScreen
import com.example.ui.viewer.InMemoryDocumentViewerDialog
import com.example.ui.viewer.InMemoryImageViewerOverlay
import com.example.viewmodel.XomeViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: XomeViewModel by viewModels()
    private val externalUiLockPolicy = VaultExternalUiLockPolicy()
    private var viewerNeedsDarkStatusBarIcons: Boolean? = null
    private val lockHandler = Handler(Looper.getMainLooper())
    private val externalUiTimeout = Runnable {
        externalUiLockPolicy.onResultOrFailure()
        VaultSessionManager.lock()
    }
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                finishExternalVaultUi()
                VaultSessionManager.lock()
            }
        }
    }

    private fun startExternalVaultUi() {
        if (VaultSessionManager.activeSession.value != null) {
            externalUiLockPolicy.onLaunch()
        }
    }

    private fun finishExternalVaultUi() {
        externalUiLockPolicy.onResultOrFailure()
        lockHandler.removeCallbacks(externalUiTimeout)
    }

    override fun onResume() {
        super.onResume()
        applyDarkSystemBars()
        // A resume without a delivered activity result is not an authorized picker return.
        if (externalUiLockPolicy.shouldLockOnResume()) {
            finishExternalVaultUi()
            VaultSessionManager.lock()
        }
    }

    override fun onStop() {
        if (externalUiLockPolicy.shouldLockOnStop()) {
            VaultSessionManager.lock()
        } else {
            // Prevent an abandoned system picker from retaining a live key indefinitely.
            lockHandler.removeCallbacks(externalUiTimeout)
            lockHandler.postDelayed(externalUiTimeout, EXTERNAL_UI_GRACE_MS)
        }
        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver(screenOffReceiver)
        finishExternalVaultUi()
        VaultSessionManager.lock()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                pollForAppUpdates { viewModel.updates.check() }
            }
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        applyDarkSystemBars()
        setContent {
            val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
            val flagSecureEnabled by viewModel.flagSecureWindowEnabled.collectAsStateWithLifecycle()

            LaunchedEffect(activeSession, flagSecureEnabled) {
                if (activeSession != null && flagSecureEnabled) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CalcChassisBg
                ) {
                    XomeRootHost(
                        viewModel = viewModel,
                        onExternalActivityStarted = ::startExternalVaultUi,
                        onExternalActivityFinished = ::finishExternalVaultUi,
                        onViewerStatusBarContrastChanged = ::onViewerStatusBarContrastChanged
                    )
                }
            }
        }
    }

    private fun onViewerStatusBarContrastChanged(darkIcons: Boolean?) {
        viewerNeedsDarkStatusBarIcons = darkIcons
        applyDarkSystemBars()
    }

    private fun applyDarkSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = viewerNeedsDarkStatusBarIcons ?: false
            isAppearanceLightNavigationBars = false
        }
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
    }

    private companion object {
        const val EXTERNAL_UI_GRACE_MS = 5 * 60 * 1000L
    }
}

@Composable
fun XomeRootHost(
    viewModel: XomeViewModel,
    onExternalActivityStarted: () -> Unit,
    onExternalActivityFinished: () -> Unit,
    onViewerStatusBarContrastChanged: (Boolean?) -> Unit
) {
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val calcState by viewModel.calcState.collectAsStateWithLifecycle()
    val needsOnboarding by viewModel.needsOnboarding.collectAsStateWithLifecycle()
    val showSetupDialog by viewModel.showSetupDialog.collectAsStateWithLifecycle()
    val isProvisioningOnboarding by viewModel.isProvisioningOnboarding.collectAsStateWithLifecycle()
    val onboardingError by viewModel.onboardingError.collectAsStateWithLifecycle()
    val isRecoveringSpace by viewModel.isRecoveringSpace.collectAsStateWithLifecycle()
    val recoveryError by viewModel.recoveryError.collectAsStateWithLifecycle()
    val unsealedRecoverableSpaces by viewModel.unsealedRecoverableSpaces.collectAsStateWithLifecycle()
    val totalSpaces by viewModel.totalProvisionedSpaces.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val securityReport by viewModel.environmentSecurityReport.collectAsStateWithLifecycle()
    val flagSecureEnabled by viewModel.flagSecureWindowEnabled.collectAsStateWithLifecycle()

    val updateState by viewModel.updates.state.collectAsStateWithLifecycle()
    var showUpdates by rememberSaveable { mutableStateOf(false) }

    val spaceMeta by viewModel.currentSpaceMeta.collectAsStateWithLifecycle()
    val folders by viewModel.currentFolders.collectAsStateWithLifecycle()
    val files by viewModel.currentFiles.collectAsStateWithLifecycle()
    val breadcrumbs by viewModel.folderBreadcrumbs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val totalFilesCount by viewModel.totalFileCount.collectAsStateWithLifecycle()
    val totalPlainBytes by viewModel.totalPlainBytes.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val pendingDeleteHandles by viewModel.pendingDeleteConfirmation.collectAsStateWithLifecycle()
    val pendingSystemDeleteIntent by viewModel.pendingSystemDeleteIntent.collectAsStateWithLifecycle()
    val pendingSourceReselect by viewModel.pendingSourceReselect.collectAsStateWithLifecycle()

    val activeImageViewerFileId by viewModel.activeImageViewerFileId.collectAsStateWithLifecycle()
    val activeDocViewerFile by viewModel.activeDocumentViewerFile.collectAsStateWithLifecycle()
    val decryptedDocText by viewModel.decryptedDocumentText.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalAppStrings provides stringsForLanguage(appLanguage)) {
        Crossfade(targetState = activeSession, label = "root_vault_switch") { session ->
            if (session == null) {
                DisguisedCalculatorScreen(
                    calcState = calcState,
                    needsOnboarding = needsOnboarding,
                    showSetupDialog = showSetupDialog,
                    isProvisioningOnboarding = isProvisioningOnboarding,
                    onboardingError = onboardingError,
                    isRecoveringSpace = isRecoveringSpace,
                    recoveryError = recoveryError,
                    unsealedRecoverableSpaces = unsealedRecoverableSpaces,
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
                    onToggleLanguage = viewModel::toggleAppLanguage,
                    onProvisionPrimarySpace = viewModel::provisionInitialPrimarySpace,
                    onExtractKeyfileDigest = viewModel::extractKeyfileDigest,
                    onRecoverSpaceWithKeyfile = viewModel::recoverExistingSpaceWithKeyfile,
                    onDismissOnboarding = viewModel::dismissOnboardingToCalculator,
                    onOpenOnboarding = viewModel::openOnboardingSetup,
                    onImportSelfHealingArchive = viewModel::importSelfHealingBundleFromUri,
                    updateAvailable = updateState.hasUpdate,
                    onOpenUpdates = { showUpdates = true }
                )
            } else {
                val imageFilesInFolder = files.filter { it.mimeType.startsWith("image/") }
                when {
                    activeImageViewerFileId != null -> {
                        InMemoryImageViewerOverlay(
                            imageFiles = imageFilesInFolder,
                            initialFileId = activeImageViewerFileId!!,
                            onClose = viewModel::closeImageViewer,
                            onExportFile = viewModel::exportFileFromViewer,
                            onShredFile = viewModel::shredVaultFile,
                            onStatusBarContrastChanged = onViewerStatusBarContrastChanged
                        )
                    }
                    activeDocViewerFile != null -> {
                        InMemoryDocumentViewerDialog(
                            fileEntity = activeDocViewerFile!!,
                            decryptedContent = decryptedDocText ?: "",
                            onClose = viewModel::closeDocumentViewer,
                            onExportFile = viewModel::exportFileFromViewer,
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
                            pendingDeleteHandles = pendingDeleteHandles,
                            pendingSystemDeleteIntent = pendingSystemDeleteIntent,
                            pendingSourceReselect = pendingSourceReselect,
                            onSourceReselected = viewModel::onSourceReselected,
                            statusBanner = statusBanner,
                            securityReport = securityReport,
                            flagSecureEnabled = flagSecureEnabled,
                            isStreamingEmulator = viewModel.isStreamingEmulator,
                            updateRepository = viewModel.updates,
                            onToggleFlagSecure = viewModel::setFlagSecureWindowEnabled,
                            onToggleLanguage = viewModel::toggleAppLanguage,
                            onSelectLanguage = viewModel::setAppLanguage,
                            onNavigateToFolder = viewModel::navigateToFolder,
                            onNavigateUp = viewModel::navigateUpFolder,
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onCreateFolder = viewModel::createNewFolder,
                            onDeleteFolder = viewModel::deleteFolder,
                            onImportUris = viewModel::importSelectedUris,
                            onConfirmDeleteOriginals = viewModel::confirmDeleteOriginalFiles,
                            onCancelDeleteOriginals = viewModel::cancelDeleteOriginalFiles,
                            onSystemDeleteIntentCompleted = viewModel::onSystemDeleteIntentCompleted,
                            onCreateEncryptedNote = viewModel::createNewEncryptedNote,
                            onOpenFileViewer = viewModel::openFileInMemoryViewer,
                            onExportFileToPublic = viewModel::exportFileToPublicStorage,
                            onShredFile = viewModel::shredVaultFile,
                            onRenameFile = viewModel::renameVaultFile,
                            onChangeSequence = viewModel::changeCurrentSpaceSequence,
                            onRotateKeyfile = viewModel::rotateCurrentSpaceKeyfile,
                            onVerifyCurrentSequence = viewModel::verifyCurrentSpaceSequence,
                            onCreateDecoySpace = viewModel::createIndependentDecoySpace,
                            onExtractKeyfileDigest = viewModel::extractKeyfileDigest,
                            onExportSelfHealingArchive = viewModel::exportSelfHealingBundleToUri,
                            onImportSelfHealingArchive = viewModel::importSelfHealingBundleFromUri,
                            onLockVault = viewModel::lockVaultImmediately,
                            onDismissBanner = viewModel::dismissBanner,
                            onExternalActivityStarted = onExternalActivityStarted,
                            onExternalActivityFinished = onExternalActivityFinished
                        )
                    }
                }
            }
        }
        AppUpdateHost(
            repository = viewModel.updates,
            showSettings = showUpdates,
            onOpen = { showUpdates = true },
            onClose = { showUpdates = false }
        )
    }
}
