package com.example.viewmodel

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.calculator.ScientificCalculatorEngine
import com.example.crypto.RandomAccessDecryptedStream
import com.example.crypto.RootSecurityDetector
import com.example.crypto.VaultCryptoEngine
import com.example.crypto.VaultSessionManager
import com.example.crypto.VaultSequencePolicy
import com.example.crypto.VaultUnlockThrottle
import com.example.data.VaultFileEntity
import com.example.data.VaultFolderEntity
import com.example.data.VaultSpaceMetaEntity
import com.example.storage.VaultStorageManager
import com.example.ui.i18n.AppLanguage
import com.example.update.UpdateRepository
import java.io.File
import java.security.MessageDigest
import java.util.Arrays
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class XomeViewModel(application: Application) : AndroidViewModel(application) {

    data class CalculatorUiState(
        val expression: String = "",
        val livePreview: String = "",
        val lastResult: String = "",
        val isDegreeMode: Boolean = true,
        val isScientificExpanded: Boolean = true,
        val isHistoryVisible: Boolean = false,
        val history: List<ScientificCalculatorEngine.CalcHistoryEntry> = emptyList()
    )

    data class ImportProgressState(
        val isImporting: Boolean = false,
        val currentIndex: Int = 0,
        val totalCount: Int = 0,
        val currentFileName: String = "",
        val isMoveMode: Boolean = false
    )

    data class StatusBanner(
        val message: String,
        val isError: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val appContext = application.applicationContext
    // Automatic checks are owned by the foreground Activity lifecycle.
    val updates = UpdateRepository(appContext)
    private val uiPrefs = appContext.getSharedPreferences("xome_ui_prefs", android.content.Context.MODE_PRIVATE)

    private val _appLanguage = MutableStateFlow(loadSavedLanguage())
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    val isStreamingEmulator: Boolean = detectEmulatorEnvironment()

    // On a physical phone, FLAG_SECURE defaults to true when inside the vault.
    // On a cloud streaming emulator (which uses virtual display / video capture to render in browser),
    // FLAG_SECURE causes SurfaceFlinger to output a solid black frame, so we default it to false on
    // streaming emulators while allowing manual toggle in the Security tab.
    private val _flagSecureWindowEnabled = MutableStateFlow(!isStreamingEmulator)
    val flagSecureWindowEnabled: StateFlow<Boolean> = _flagSecureWindowEnabled.asStateFlow()

    // Onboarding & Self-Healing state
    private val _needsOnboarding = MutableStateFlow(false)
    val needsOnboarding: StateFlow<Boolean> = _needsOnboarding.asStateFlow()

    private val _showSetupDialog = MutableStateFlow(false)
    val showSetupDialog: StateFlow<Boolean> = _showSetupDialog.asStateFlow()

    private val _isProvisioningOnboarding = MutableStateFlow(false)
    val isProvisioningOnboarding: StateFlow<Boolean> = _isProvisioningOnboarding.asStateFlow()

    private val _onboardingError = MutableStateFlow<String?>(null)
    val onboardingError: StateFlow<String?> = _onboardingError.asStateFlow()

    private val _totalProvisionedSpaces = MutableStateFlow(0)
    val totalProvisionedSpaces: StateFlow<Int> = _totalProvisionedSpaces.asStateFlow()

    // Disguised Calculator state
    private val _calcState = MutableStateFlow(CalculatorUiState())
    val calcState: StateFlow<CalculatorUiState> = _calcState.asStateFlow()
    private val hiddenUnlockEpoch = AtomicLong(0L)
    private var hiddenUnlockJob: Job? = null
    private val hiddenUnlockMutex = Mutex()
    private val unlockThrottle = VaultUnlockThrottle(
        appContext.getSharedPreferences("xome_unlock_throttle", android.content.Context.MODE_PRIVATE)
    )

    private fun invalidateHiddenUnlock() {
        hiddenUnlockEpoch.incrementAndGet()
        hiddenUnlockJob?.cancel()
        hiddenUnlockJob = null
    }

    // Active Vault Session state
    val activeSession: StateFlow<VaultSessionManager.ActiveVaultSession?> =
        VaultSessionManager.activeSession

    private val _currentFolderId = MutableStateFlow<Long?>(null)
    val currentFolderId: StateFlow<Long?> = _currentFolderId.asStateFlow()

    private val _folderBreadcrumbs = MutableStateFlow<List<VaultFolderEntity>>(emptyList())
    val folderBreadcrumbs: StateFlow<List<VaultFolderEntity>> = _folderBreadcrumbs.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _importProgress = MutableStateFlow(ImportProgressState())
    val importProgress: StateFlow<ImportProgressState> = _importProgress.asStateFlow()

    // Post-import confirmation state for deleting original files when in Move/Delete mode
    private val _pendingDeleteConfirmation =
        MutableStateFlow<List<VaultStorageManager.DeletableSourceHandle>>(emptyList())
    val pendingDeleteConfirmation: StateFlow<List<VaultStorageManager.DeletableSourceHandle>> =
        _pendingDeleteConfirmation.asStateFlow()

    private val _pendingSystemDeleteIntent = MutableStateFlow<IntentSender?>(null)
    val pendingSystemDeleteIntent: StateFlow<IntentSender?> =
        _pendingSystemDeleteIntent.asStateFlow()

    private var systemDeleteHandles = emptyList<VaultStorageManager.DeletableSourceHandle>()
    private var unresolvedDeleteHandles = emptyList<VaultStorageManager.DeletableSourceHandle>()
    private val reselectAttemptedUris = mutableSetOf<Uri>()
    private var verifiedDeletedCount = 0
    private val _pendingSourceReselect = MutableStateFlow<VaultStorageManager.DeletableSourceHandle?>(null)
    val pendingSourceReselect = _pendingSourceReselect.asStateFlow()

    private val _statusBanner = MutableStateFlow<StatusBanner?>(null)
    val statusBanner: StateFlow<StatusBanner?> = _statusBanner.asStateFlow()

    // In-Memory Viewers
    private val _activeImageViewerFileId = MutableStateFlow<Long?>(null)
    val activeImageViewerFileId: StateFlow<Long?> = _activeImageViewerFileId.asStateFlow()

    private val _activeDocumentViewerFile = MutableStateFlow<VaultFileEntity?>(null)
    val activeDocumentViewerFile: StateFlow<VaultFileEntity?> = _activeDocumentViewerFile.asStateFlow()

    private val _decryptedDocumentText = MutableStateFlow<String?>(null)
    val decryptedDocumentText: StateFlow<String?> = _decryptedDocumentText.asStateFlow()

    private val _environmentSecurityReport = MutableStateFlow(
        RootSecurityDetector.EnvironmentSecurityReport(
            isRootOrHookDetected = false,
            suBinaryFound = false,
            magiskDetected = false,
            xposedDetected = false,
            testKeysBuild = false,
            findings = emptyList()
        )
    )
    val environmentSecurityReport: StateFlow<RootSecurityDetector.EnvironmentSecurityReport> =
        _environmentSecurityReport.asStateFlow()

    val currentSpaceMeta: StateFlow<VaultSpaceMetaEntity?> = activeSession
        .flatMapLatest { session ->
            session?.database?.vaultDao()?.observeSpaceMeta() ?: flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentFolders: StateFlow<List<VaultFolderEntity>> = combine(
        activeSession,
        _currentFolderId,
        _searchQuery
    ) { session, folderId, query ->
        Triple(session, folderId, query)
    }.flatMapLatest { (session, folderId, query) ->
        if (session == null) {
            flowOf(emptyList())
        } else if (query.isBlank()) {
            session.database.vaultDao().observeFolders(folderId)
        } else {
            session.database.vaultDao().observeAllFolders()
        }
    }.combine(_searchQuery) { list, query ->
        if (query.isBlank()) list
        else list.filter { it.folderName.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentFiles: StateFlow<List<VaultFileEntity>> = combine(
        activeSession,
        _currentFolderId,
        _searchQuery
    ) { session, folderId, query ->
        Triple(session, folderId, query)
    }.flatMapLatest { (session, folderId, query) ->
        if (session == null) {
            flowOf(emptyList())
        } else if (query.isBlank()) {
            session.database.vaultDao().observeFiles(folderId)
        } else {
            session.database.vaultDao().observeAllFiles()
        }
    }.combine(_searchQuery) { list, query ->
        if (query.isBlank()) list
        else list.filter { it.originalName.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalFileCount: StateFlow<Int> = activeSession
        .flatMapLatest { session ->
            session?.database?.vaultDao()?.observeTotalFileCount() ?: flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalPlainBytes: StateFlow<Long> = activeSession
        .flatMapLatest { session ->
            session?.database?.vaultDao()?.observeTotalPlainBytes() ?: flowOf(0L)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val report = RootSecurityDetector.inspectEnvironment()
            _environmentSecurityReport.value = report
            val headers = VaultStorageManager.listSpaceHeaderFiles(appContext)
            _totalProvisionedSpaces.value = headers.size
            _needsOnboarding.value = headers.isEmpty()
        }

        viewModelScope.launch {
            VaultSessionManager.activeSession.collect { session ->
                if (session == null) {
                    // Vault operation messages must never be visible on the disguised calculator.
                    _statusBanner.value = null
                    _currentFolderId.value = null
                    _folderBreadcrumbs.value = emptyList()
                    _searchQuery.value = ""
                    _activeImageViewerFileId.value = null
                    _activeDocumentViewerFile.value = null
                    _decryptedDocumentText.value = null
                    _pendingDeleteConfirmation.value = emptyList()
                    _pendingSourceReselect.value = null
                    _pendingSystemDeleteIntent.value = null
                    systemDeleteHandles = emptyList()
                    unresolvedDeleteHandles = emptyList()
                    reselectAttemptedUris.clear()
                } else {
                    withContext(Dispatchers.IO) {
                        _totalProvisionedSpaces.value =
                            VaultStorageManager.listSpaceHeaderFiles(appContext).size
                    }
                }
            }
        }
    }

    private fun loadSavedLanguage(): AppLanguage {
        val code = uiPrefs.getString("app_lang", AppLanguage.ZH.code)
        return if (code == AppLanguage.EN.code) AppLanguage.EN else AppLanguage.ZH
    }

    fun setAppLanguage(language: AppLanguage) {
        _appLanguage.value = language
        uiPrefs.edit().putString("app_lang", language.code).apply()
    }

    fun toggleAppLanguage() {
        setAppLanguage(_appLanguage.value.next())
    }

    private fun isZh(): Boolean = _appLanguage.value == AppLanguage.ZH

    fun setFlagSecureWindowEnabled(enabled: Boolean) {
        _flagSecureWindowEnabled.value = enabled
        val msg = if (isZh()) {
            if (enabled) "已开启防截屏保护（云端模拟器下开启会显示黑屏）"
            else "已关闭防截屏保护（适配云端模拟器预览）"
        } else {
            if (enabled) "Screenshot protection enabled"
            else "Screenshot protection disabled for emulator preview"
        }
        showBanner(msg)
    }

    fun refreshSelfHealingStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val headers = VaultStorageManager.listSpaceHeaderFiles(appContext)
            _totalProvisionedSpaces.value = headers.size
            _needsOnboarding.value = headers.isEmpty()
        }
    }

    fun dismissOnboardingToCalculator() {
        _showSetupDialog.value = false
    }

    fun openOnboardingSetup() {
        _onboardingError.value = null
        _showSetupDialog.value = true
    }

    fun provisionInitialPrimarySpace(
        rawSequence: String,
        confirmSequence: String,
        spaceAlias: String = "Main Space",
        enterCalculatorFirst: Boolean = false
    ) {
        val cleanSeq = VaultSequencePolicy.normalize(rawSequence)
        val cleanConfirm = VaultSequencePolicy.normalize(confirmSequence)

        if (!VaultSequencePolicy.isValidNewSequence(cleanSeq)) {
            _onboardingError.value = if (isZh()) {
                "解锁序列需为 6 至 16 位，且同时包含数字与计算器符号"
            } else {
                "Use 6–16 characters combining digits and calculator symbols."
            }
            return
        }
        if (cleanSeq != cleanConfirm) {
            _onboardingError.value = if (isZh()) "两次输入的序列不一致" else "Confirmation sequence does not match."
            return
        }

        _onboardingError.value = null
        _isProvisioningOnboarding.value = true

        viewModelScope.launch {
            val pinChars = cleanSeq.toCharArray()
            try {
                val defaultAlias = if (isZh()) "主空间" else "Main Space"
                VaultStorageManager.provisionNewSpace(
                    context = appContext,
                    pinChars = pinChars,
                    spaceAlias = spaceAlias.ifBlank { defaultAlias },
                    isDecoy = false,
                    seedSampleFiles = true,
                    activateImmediately = !enterCalculatorFirst
                )
                refreshSelfHealingStatus()
                _needsOnboarding.value = false
                _showSetupDialog.value = false
                if (!enterCalculatorFirst) {
                    showBanner(if (isZh()) "私密空间已创建" else "Private space created")
                } else {
                    showBanner(
                        if (isZh()) "设置完成，输入你的解锁序列并按等号即可进入"
                        else "Setup complete. Enter your sequence followed by equals to unlock."
                    )
                }
            } catch (e: Exception) {
                _onboardingError.value = "Failed: ${e.message}"
            } finally {
                Arrays.fill(pinChars, '\u0000')
                _isProvisioningOnboarding.value = false
            }
        }
    }

    // ------------------------------------------------------------------------
    // Disguised Scientific Calculator & Blind-Match Interceptor
    // ------------------------------------------------------------------------

    fun onCalculatorInput(token: String) {
        invalidateHiddenUnlock()
        val current = _calcState.value
        val baseExpr = if (current.expression.isEmpty() &&
            current.lastResult.isNotEmpty() &&
            token in listOf("+", "−", "×", "÷", "^", "%")
        ) {
            current.lastResult
        } else {
            current.expression
        }

        val updatedExpr = baseExpr + token
        val preview = computeLivePreview(updatedExpr, current.isDegreeMode)
        _calcState.value = current.copy(
            expression = updatedExpr,
            livePreview = preview,
            lastResult = if (baseExpr == current.lastResult) "" else current.lastResult
        )
    }

    fun onCalculatorClear() {
        invalidateHiddenUnlock()
        _calcState.value = _calcState.value.copy(
            expression = "",
            livePreview = "",
            lastResult = ""
        )
    }

    fun onCalculatorBackspace() {
        invalidateHiddenUnlock()
        val current = _calcState.value
        if (current.expression.isEmpty()) return
        val dropCount = when {
            current.expression.endsWith("sqrt(") -> 5
            current.expression.endsWith("sin(") ||
                current.expression.endsWith("cos(") ||
                current.expression.endsWith("tan(") ||
                current.expression.endsWith("log(") -> 4
            current.expression.endsWith("ln(") -> 3
            else -> 1
        }
        val updated = current.expression.dropLast(dropCount.coerceAtMost(current.expression.length))
        _calcState.value = current.copy(
            expression = updated,
            livePreview = computeLivePreview(updated, current.isDegreeMode)
        )
    }

    fun toggleDegreeRadianMode() {
        val current = _calcState.value
        val nextDeg = !current.isDegreeMode
        _calcState.value = current.copy(
            isDegreeMode = nextDeg,
            livePreview = computeLivePreview(current.expression, nextDeg)
        )
    }

    fun toggleScientificPad() {
        val current = _calcState.value
        _calcState.value = current.copy(isScientificExpanded = !current.isScientificExpanded)
    }

    fun toggleCalculatorHistory() {
        val current = _calcState.value
        _calcState.value = current.copy(isHistoryVisible = !current.isHistoryVisible)
    }

    fun clearCalculatorHistory() {
        _calcState.value = _calcState.value.copy(history = emptyList())
    }

    fun onCalculatorEqualsPressed() {
        val current = _calcState.value
        val rawExpr = current.expression.trim()
        if (rawExpr.isEmpty()) return
        invalidateHiddenUnlock()
        val normalizedCandidate = VaultSequencePolicy.normalize(rawExpr)
        val couldBeCredential = VaultSequencePolicy.isValidLegacySequence(normalizedCandidate)
        // Do not expose a candidate in history while verification is pending.
        performNormalMathEvaluation(rawExpr, recordHistory = !couldBeCredential)
        if (!couldBeCredential) return

        val attemptEpoch = hiddenUnlockEpoch.get()
        hiddenUnlockJob = viewModelScope.launch {
            hiddenUnlockMutex.withLock {
                try {
                    val hasSpaces = withContext(Dispatchers.IO) {
                        VaultStorageManager.hasExistingSpaces(appContext)
                    }

                    if (hiddenUnlockEpoch.get() != attemptEpoch) return@launch
                    if (!hasSpaces) {
                        recordCalculation(rawExpr)
                        return@launch
                    }
                    if (!withContext(Dispatchers.IO) { unlockThrottle.tryAcquire() }) return@launch

                    val canActivate = { hiddenUnlockEpoch.get() == attemptEpoch }
                    if (!canActivate()) return@launch
                    val unlockedSession = VaultStorageManager.tryBlindUnlock(
                        context = appContext,
                        rawSequence = normalizedCandidate,
                        canActivate = canActivate
                    ) ?: if (normalizedCandidate != rawExpr && canActivate()) {
                        VaultStorageManager.tryBlindUnlock(appContext, rawExpr, canActivate)
                    } else {
                        null
                    }

                    if (unlockedSession != null && canActivate()) {
                        withContext(Dispatchers.IO) { unlockThrottle.onSuccess() }
                        _calcState.value = _calcState.value.copy(
                            expression = "",
                            livePreview = "",
                            lastResult = "",
                            history = emptyList(),
                            isHistoryVisible = false
                        )
                    } else if (canActivate()) {
                        recordCalculation(rawExpr)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A corrupt header or storage error must not reveal the hidden matching flow.
                }
            }
        }
    }

    private fun recordCalculation(rawExpr: String) {
        val current = _calcState.value
        val result = ScientificCalculatorEngine.evaluateExpression(rawExpr, current.isDegreeMode).getOrNull()
            ?: return
        _calcState.value = current.copy(history = (listOf(
            ScientificCalculatorEngine.CalcHistoryEntry(expression = rawExpr, result = ScientificCalculatorEngine.formatNumber(result))
        ) + current.history).take(30))
    }

    private fun performNormalMathEvaluation(rawExpr: String, recordHistory: Boolean = true) {
        val current = _calcState.value
        val evalResult = ScientificCalculatorEngine.evaluateExpression(rawExpr, current.isDegreeMode)
        evalResult.fold(
            onSuccess = { num ->
                val formatted = ScientificCalculatorEngine.formatNumber(num)
                val newHistory = (listOf(
                    ScientificCalculatorEngine.CalcHistoryEntry(
                        expression = rawExpr,
                        result = formatted
                    )
                ) + current.history).take(30).takeIf { recordHistory } ?: current.history

                _calcState.value = current.copy(
                    expression = formatted,
                    livePreview = "",
                    lastResult = formatted,
                    history = newHistory
                )
            },
            onFailure = {
                _calcState.value = current.copy(
                    livePreview = "Syntax Error"
                )
            }
        )
    }

    private fun computeLivePreview(expr: String, isDegreeMode: Boolean): String {
        if (expr.isBlank()) return ""
        val hasOperator = expr.any { it in listOf('+', '−', '-', '×', '*', '÷', '/', '^', '%', '!', '√', '(', 'π', 'e') }
        if (!hasOperator) return ""
        return ScientificCalculatorEngine.evaluateExpression(expr, isDegreeMode)
            .map { ScientificCalculatorEngine.formatNumber(it) }
            .getOrDefault("")
    }

    // ------------------------------------------------------------------------
    // Virtual File System & File Import with Final Delete Confirmation
    // ------------------------------------------------------------------------

    fun navigateToFolder(folder: VaultFolderEntity?) {
        viewModelScope.launch {
            if (folder == null) {
                _currentFolderId.value = null
                _folderBreadcrumbs.value = emptyList()
            } else {
                _currentFolderId.value = folder.id
                val crumbs = mutableListOf<VaultFolderEntity>()
                val dao = activeSession.value?.database?.vaultDao()
                var cursor: VaultFolderEntity? = folder
                while (cursor != null) {
                    crumbs.add(0, cursor)
                    val pid = cursor.parentId
                    cursor = if (pid != null && dao != null) dao.getFolderById(pid) else null
                }
                _folderBreadcrumbs.value = crumbs
            }
        }
    }

    fun navigateUpFolder(): Boolean {
        val crumbs = _folderBreadcrumbs.value
        if (crumbs.isEmpty()) return false
        val parent = crumbs.dropLast(1).lastOrNull()
        navigateToFolder(parent)
        return true
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun createNewFolder(folderName: String) {
        val clean = folderName.trim()
        if (clean.isEmpty()) return
        val session = activeSession.value ?: return
        viewModelScope.launch {
            session.database.vaultDao().insertFolder(
                VaultFolderEntity(
                    parentId = _currentFolderId.value,
                    folderName = clean
                )
            )
            showBanner(if (isZh()) "已创建文件夹「$clean」" else "Created folder \"$clean\"")
        }
    }

    fun deleteFolder(folder: VaultFolderEntity) {
        val session = activeSession.value ?: return
        viewModelScope.launch {
            val dao = session.database.vaultDao()
            val filesInside = dao.getFilesInFolderSync(folder.id)
            filesInside.forEach { file ->
                VaultStorageManager.shredVaultFile(appContext, file)
            }
            dao.deleteFolderById(folder.id)
            showBanner(
                if (isZh()) "已删除文件夹「${folder.folderName}」"
                else "Deleted folder \"${folder.folderName}\""
            )
        }
    }

    fun importSelectedUris(uris: List<Uri>, moveAndWipeOriginal: Boolean) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importProgress.value = ImportProgressState(
                isImporting = true,
                currentIndex = 0,
                totalCount = uris.size,
                currentFileName = if (isZh()) "正在准备加密..." else "Preparing encryption...",
                isMoveMode = moveAndWipeOriginal
            )

            val result = VaultStorageManager.importUrisToVault(
                context = appContext,
                uris = uris,
                targetFolderId = _currentFolderId.value,
                moveAndWipeOriginal = moveAndWipeOriginal,
                onProgress = { idx, total, name ->
                    _importProgress.value = ImportProgressState(
                        isImporting = true,
                        currentIndex = idx,
                        totalCount = total,
                        currentFileName = name,
                        isMoveMode = moveAndWipeOriginal
                    )
                }
            )

            _importProgress.value = ImportProgressState(isImporting = false)

            if (result.errors.isNotEmpty() && result.importedCount == 0) {
                showBanner(
                    if (isZh()) "导入失败: ${result.errors.first()}"
                    else "Import failed: ${result.errors.first()}",
                    isError = true
                )
            } else if (moveAndWipeOriginal && result.pendingDeleteHandles.isNotEmpty()) {
                // Show final confirmation dialog before deleting original files!
                _pendingDeleteConfirmation.value = result.pendingDeleteHandles
            } else {
                showBanner(
                    if (isZh()) "已加密导入 ${result.importedCount} 个文件"
                    else "Encrypted and imported ${result.importedCount} file(s)"
                )
            }
        }
    }

    /**
     * Invoked when the user confirms deleting the original files in the post-import dialog.
     */
    fun confirmDeleteOriginalFiles() {
        val session = activeSession.value ?: return
        val handles = _pendingDeleteConfirmation.value
        _pendingDeleteConfirmation.value = emptyList()
        if (handles.isEmpty()) return
        verifiedDeletedCount = 0
        unresolvedDeleteHandles = emptyList()
        reselectAttemptedUris.clear()
        viewModelScope.launch {
            val outcome = VaultStorageManager.executeConfirmedOriginalDeletion(appContext, handles)
            if (activeSession.value === session) acceptDeletionOutcome(outcome)
        }
    }

    private fun acceptDeletionOutcome(outcome: VaultStorageManager.SourceDeletionOutcome, guideReselect: Boolean = true) {
        verifiedDeletedCount += outcome.deletedImmediatelyCount
        unresolvedDeleteHandles = (unresolvedDeleteHandles + outcome.unresolvedHandles)
            .distinctBy { it.originalUri }
        systemDeleteHandles = outcome.pendingHandles
        _pendingSystemDeleteIntent.value = outcome.pendingSystemDeleteIntentSender
        if (outcome.pendingSystemDeleteIntentSender == null) {
            _pendingSourceReselect.value = if (guideReselect) unresolvedDeleteHandles.firstOrNull {
                it.originalUri !in reselectAttemptedUris && VaultStorageManager.canReselectOriginal(appContext, it)
            } else null
            reportOriginalDeletion()
        }
    }

    private fun reportOriginalDeletion() {
        val remaining = unresolvedDeleteHandles.size
        showBanner(
            if (isZh()) "已验证删除 $verifiedDeletedCount 个原文件；$remaining 个未确认删除，加密副本已保存" +
                (if (remaining > 0) "。可在来源应用检查并手动删除原文件" else "")
            else "Verified $verifiedDeletedCount original(s) deleted; $remaining unconfirmed. Encrypted copies saved" +
                (if (remaining > 0) ". Check and delete originals in their source app if needed" else ""),
            isError = remaining > 0
        )
    }

    fun onSourceReselected(uri: Uri?) {
        val session = activeSession.value ?: return
        val handle = _pendingSourceReselect.value ?: return
        _pendingSourceReselect.value = null
        if (uri == null) {
            reportOriginalDeletion()
            return
        }
        viewModelScope.launch {
            val same = withContext(Dispatchers.IO) {
                VaultStorageManager.isSameOriginal(appContext, handle, uri)
            }
            if (activeSession.value !== session) return@launch
            if (!same) {
                showBanner(
                    if (isZh()) "无法确认是同一原文件，未删除。请重选原文件；云端提供方可能不支持删除"
                    else "Cannot verify the same original; nothing deleted. Reselect the original; cloud providers may not support deletion",
                    isError = true
                )
                _pendingSourceReselect.value = handle
                return@launch
            }
            unresolvedDeleteHandles = unresolvedDeleteHandles.filterNot { it.originalUri == handle.originalUri }
            reselectAttemptedUris.add(handle.originalUri)
            reselectAttemptedUris.add(uri)
            val granted = handle.copy(originalUri = uri)
            val outcome = VaultStorageManager.executeConfirmedOriginalDeletion(appContext, listOf(granted))
            if (activeSession.value === session) acceptDeletionOutcome(outcome)
        }
    }

    /**
     * Invoked when the user chooses to keep the original files in the post-import confirmation dialog.
     */
    fun cancelDeleteOriginalFiles() {
        val count = _pendingDeleteConfirmation.value.size
        _pendingDeleteConfirmation.value = emptyList()
        if (count > 0) {
            showBanner(
                if (isZh()) "已加密导入 $count 个文件（已保留原文件）"
                else "Imported $count file(s) (original files kept)"
            )
        }
    }

    /**
     * Callback after Android's `MediaStore.createDeleteRequest` system sheet finishes.
     */
    fun onSystemDeleteIntentCompleted(approved: Boolean) {
        val session = activeSession.value ?: return
        val handles = systemDeleteHandles
        systemDeleteHandles = emptyList()
        _pendingSystemDeleteIntent.value = null
        if (handles.isEmpty()) return
        viewModelScope.launch {
            // RESULT_OK alone never proves deletion. Android 10 grants permission only.
            val outcome = if (approved && Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                VaultStorageManager.executeConfirmedOriginalDeletion(appContext, handles)
            } else {
                VaultStorageManager.verifyOriginalDeletion(appContext, handles)
            }
            if (activeSession.value === session) acceptDeletionOutcome(outcome, guideReselect = approved)
        }
    }

    fun createNewEncryptedNote(title: String, content: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val created = VaultStorageManager.createEncryptedNoteInVault(
                context = appContext,
                folderId = _currentFolderId.value,
                title = title.trim(),
                content = content
            )
            if (created != null) {
                showBanner(
                    if (isZh()) "已保存笔记「${created.originalName}」"
                    else "Saved note \"${created.originalName}\""
                )
            }
        }
    }

    fun shredVaultFile(fileEntity: VaultFileEntity) {
        viewModelScope.launch {
            VaultStorageManager.shredVaultFile(appContext, fileEntity)
            if (_activeImageViewerFileId.value == fileEntity.id) {
                _activeImageViewerFileId.value = null
            }
            if (_activeDocumentViewerFile.value?.id == fileEntity.id) {
                _activeDocumentViewerFile.value = null
                _decryptedDocumentText.value = null
            }
            showBanner(
                if (isZh()) "已彻底删除「${fileEntity.originalName}」"
                else "Deleted \"${fileEntity.originalName}\""
            )
        }
    }

    fun renameVaultFile(fileEntity: VaultFileEntity, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        val session = activeSession.value ?: return
        viewModelScope.launch {
            session.database.vaultDao().renameFile(fileEntity.id, clean)
            showBanner(if (isZh()) "已重命名为「$clean」" else "Renamed to \"$clean\"")
        }
    }

    fun exportFileFromViewer(
        fileEntity: VaultFileEntity,
        onComplete: (Result<String>) -> Unit
    ) {
        viewModelScope.launch {
            val result = try {
                VaultStorageManager.exportEncryptedFileToPublicDownloads(appContext, fileEntity)
            } catch (e: Exception) {
                Result.failure(e)
            }
            onComplete(result)
        }
    }

    fun exportFileToPublicStorage(fileEntity: VaultFileEntity) {
        viewModelScope.launch {
            val res = VaultStorageManager.exportEncryptedFileToPublicDownloads(appContext, fileEntity)
            res.fold(
                onSuccess = { path ->
                    showBanner(
                        if (isZh()) "已导出至 $path"
                        else "Exported to $path"
                    )
                },
                onFailure = { err ->
                    showBanner(
                        if (isZh()) "导出失败: ${err.message}"
                        else "Export failed: ${err.message}",
                        isError = true
                    )
                }
            )
        }
    }

    // ------------------------------------------------------------------------
    // In-Memory Streaming Image & Document Viewers
    // ------------------------------------------------------------------------

    fun openFileInMemoryViewer(fileEntity: VaultFileEntity) {
        if (fileEntity.mimeType.startsWith("image/")) {
            _activeImageViewerFileId.value = fileEntity.id
        } else {
            openDocumentInMemory(fileEntity)
        }
    }

    fun closeImageViewer() {
        _activeImageViewerFileId.value = null
    }

    fun closeDocumentViewer() {
        _activeDocumentViewerFile.value = null
        _decryptedDocumentText.value = null
    }

    private fun openDocumentInMemory(fileEntity: VaultFileEntity) {
        val session = activeSession.value ?: return
        _activeDocumentViewerFile.value = fileEntity
        _decryptedDocumentText.value = if (isZh()) "正在加载..." else "Loading..."

        viewModelScope.launch(Dispatchers.IO) {
            val masterKey = session.borrowMasterKeyCopy()
            try {
                val tree = VaultStorageManager.resolveVaultTree(appContext)
                val blobFile = File(tree.blobsDir, fileEntity.blobFilename)
                RandomAccessDecryptedStream(blobFile, masterKey).use { stream ->
                    val previewBytes = stream.readRange(0L, 262_144)
                    val isTextLike = fileEntity.mimeType.startsWith("text/") ||
                        fileEntity.originalName.endsWith(".md", true) ||
                        fileEntity.originalName.endsWith(".txt", true) ||
                        fileEntity.originalName.endsWith(".json", true) ||
                        fileEntity.originalName.endsWith(".csv", true) ||
                        fileEntity.originalName.endsWith(".log", true)

                    val rendered = if (isTextLike) {
                        String(previewBytes, Charsets.UTF_8)
                    } else {
                        buildHexInspectionDump(fileEntity, blobFile, stream.totalChunks, previewBytes)
                    }
                    withContext(Dispatchers.Main) {
                        _decryptedDocumentText.value = rendered
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _decryptedDocumentText.value = "Error: ${e.message}"
                }
            } finally {
                Arrays.fill(masterKey, 0.toByte())
            }
        }
    }

    private fun buildHexInspectionDump(
        entity: VaultFileEntity,
        blobFile: File,
        totalChunks: Int,
        plainSample: ByteArray
    ): String {
        val sb = StringBuilder()
        sb.appendLine("File : ${entity.originalName}")
        sb.appendLine("Type : ${entity.mimeType}")
        sb.appendLine("Size : ${entity.originalSize} Bytes ($totalChunks chunks)")
        sb.appendLine("--------------------------------------------")
        val limit = minOf(plainSample.size, 512)
        for (offset in 0 until limit step 16) {
            val lineEnd = minOf(offset + 16, limit)
            val hexPart = (offset until lineEnd).joinToString(" ") { "%02X".format(plainSample[it]) }
            val asciiPart = (offset until lineEnd).map {
                val b = plainSample[it].toInt() and 0xFF
                if (b in 32..126) b.toChar() else '.'
            }.joinToString("")
            sb.appendLine("%04X: %-48s  %s".format(offset, hexPart, asciiPart))
        }
        return sb.toString()
    }

    // ------------------------------------------------------------------------
    // Zero Re-encryption Password Change & Multi-Space Creation
    // ------------------------------------------------------------------------

    /** Verify the current space credential without opening a second session or modifying data. */
    fun verifyCurrentSpaceSequence(
        sequence: String,
        onComplete: (Boolean) -> Unit
    ) {
        val currentSession = activeSession.value
        if (currentSession == null || sequence.trim().length !in 4..16) {
            onComplete(false)
            return
        }
        viewModelScope.launch {
            val chars = sequence.trim().toCharArray()
            val normalizedChars = VaultSequencePolicy.normalize(sequence).toCharArray()
            val valid = try {
                withContext(Dispatchers.Default) {
                    val header = VaultCryptoEngine.parseSpaceHeader(currentSession.headerFile)
                        ?: return@withContext false
                    val unwrapped = VaultCryptoEngine.tryUnlockSpaceHeader(header, normalizedChars)
                        ?: (if (!chars.contentEquals(normalizedChars)) VaultCryptoEngine.tryUnlockSpaceHeader(header, chars) else null)
                        ?: return@withContext false
                    try {
                        val currentKey = currentSession.borrowMasterKeyCopy()
                        try {
                            MessageDigest.isEqual(unwrapped, currentKey)
                        } finally {
                            Arrays.fill(currentKey, 0.toByte())
                        }
                    } finally {
                        Arrays.fill(unwrapped, 0.toByte())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            } finally {
                Arrays.fill(chars, '\u0000')
                Arrays.fill(normalizedChars, '\u0000')
            }
            onComplete(valid && activeSession.value === currentSession)
        }
    }

    fun changeCurrentSpaceSequence(
        oldSequence: String,
        newSequence: String,
        confirmSequence: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val session = activeSession.value ?: run {
            onComplete(false, "No active session.")
            return
        }
        val cleanOld = oldSequence.trim()
        val cleanNew = VaultSequencePolicy.normalize(newSequence)
        val cleanConfirm = VaultSequencePolicy.normalize(confirmSequence)

        if (!VaultSequencePolicy.isValidNewSequence(cleanNew)) {
            onComplete(
                false,
                if (isZh()) "新序列需为 6 至 16 位，且同时包含数字与计算器符号" else "Use 6–16 characters combining digits and calculator symbols."
            )
            return
        }
        if (cleanNew != cleanConfirm) {
            onComplete(
                false,
                if (isZh()) "两次输入的新序列不一致" else "Confirmation sequence does not match."
            )
            return
        }

        viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            val oldChars = cleanOld.toCharArray()
            val normalizedOldChars = VaultSequencePolicy.normalize(cleanOld).toCharArray()
            val newChars = cleanNew.toCharArray()
            try {
                val usedElsewhere = withContext(Dispatchers.Default) {
                    VaultStorageManager.isSequenceAlreadyUsed(appContext, cleanNew)
                }
                if (usedElsewhere && cleanOld != cleanNew) {
                    onComplete(
                        false,
                        if (isZh()) "该序列已被其他空间使用" else "That sequence is already used by another space."
                    )
                    return@launch
                }

                val ok = withContext(Dispatchers.Default) {
                    VaultCryptoEngine.changeSpacePinZeroReencryption(
                        headerFile = session.headerFile,
                        oldPinChars = oldChars,
                        newPinChars = newChars
                    ) || (!oldChars.contentEquals(normalizedOldChars) &&
                        VaultCryptoEngine.changeSpacePinZeroReencryption(session.headerFile, normalizedOldChars, newChars))
                }
                val elapsed = System.currentTimeMillis() - startMs
                if (ok) {
                    val msg = if (isZh()) {
                        "解锁序列已更新 (${elapsed}ms)"
                    } else {
                        "Sequence updated (${elapsed}ms)"
                    }
                    showBanner(msg)
                    onComplete(true, msg)
                } else {
                    onComplete(
                        false,
                        if (isZh()) "当前解锁序列不正确" else "Current sequence is incorrect."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onComplete(false, if (isZh()) "修改失败，请重试" else "Could not update the sequence. Try again.")
            } finally {
                Arrays.fill(oldChars, '\u0000')
                Arrays.fill(normalizedOldChars, '\u0000')
                Arrays.fill(newChars, '\u0000')
            }
        }
    }

    fun createIndependentDecoySpace(
        newSequence: String,
        confirmSequence: String,
        spaceAlias: String,
        isDecoy: Boolean,
        includeSampleFiles: Boolean,
        switchToNewSpaceImmediately: Boolean = false,
        onComplete: (Boolean, String) -> Unit
    ) {
        val cleanSeq = VaultSequencePolicy.normalize(newSequence)
        val cleanConfirm = VaultSequencePolicy.normalize(confirmSequence)

        if (!VaultSequencePolicy.isValidNewSequence(cleanSeq)) {
            onComplete(
                false,
                if (isZh()) "解锁序列需为 6 至 16 位，且同时包含数字与计算器符号" else "Use 6–16 characters combining digits and calculator symbols."
            )
            return
        }
        if (cleanSeq != cleanConfirm) {
            onComplete(
                false,
                if (isZh()) "两次输入的确认序列不一致" else "Sequence confirmation does not match."
            )
            return
        }

        viewModelScope.launch {
            val pinChars = cleanSeq.toCharArray()
            try {
                val alreadyUsed = VaultStorageManager.isSequenceAlreadyUsed(appContext, cleanSeq)
                if (alreadyUsed) {
                    onComplete(
                        false,
                        if (isZh()) "该序列已被现有空间使用，请设置不同序列" else "This sequence is already used by another space."
                    )
                    return@launch
                }
                VaultStorageManager.provisionNewSpace(
                    context = appContext,
                    pinChars = pinChars,
                    spaceAlias = spaceAlias.ifBlank {
                        if (isZh()) (if (isDecoy) "访客空间" else "私密空间")
                        else (if (isDecoy) "Guest Space" else "Private Space")
                    },
                    isDecoy = isDecoy,
                    seedSampleFiles = includeSampleFiles,
                    activateImmediately = switchToNewSpaceImmediately
                )
                refreshSelfHealingStatus()
                val msg = if (isZh()) {
                    "独立空间已创建"
                } else {
                    "Independent space created"
                }
                showBanner(msg)
                onComplete(true, msg)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onComplete(false, if (isZh()) "创建失败，请重试" else "Could not create the space. Try again.")
            } finally {
                Arrays.fill(pinChars, '\u0000')
            }
        }
    }

    fun exportSelfHealingBundleToUri(targetUri: Uri) {
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(targetUri)?.use { out ->
                        VaultStorageManager.exportVaultSelfHealingBundle(appContext, out)
                    } ?: 0
                }
                showBanner(
                    if (isZh()) "已导出备份包（共 $count 项）"
                    else "Exported backup archive ($count items)"
                )
            } catch (e: Exception) {
                showBanner("Export failed: ${e.message}", isError = true)
            }
        }
    }

    fun importSelfHealingBundleFromUri(sourceUri: Uri) {
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                        VaultStorageManager.importVaultSelfHealingBundle(appContext, input)
                    } ?: 0
                }
                refreshSelfHealingStatus()
                showBanner(
                    if (isZh()) "已恢复 $count 项加密数据，在计算器输入原密码并按 = 即可解锁"
                    else "Restored $count items. Enter your sequence + = on calculator to unlock."
                )
            } catch (e: Exception) {
                showBanner("Restore failed: ${e.message}", isError = true)
            }
        }
    }

    fun lockVaultImmediately() {
        VaultSessionManager.lock()
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    private fun showBanner(message: String, isError: Boolean = false) {
        _statusBanner.value = StatusBanner(message = message, isError = isError)
    }

    companion object {
        fun detectEmulatorEnvironment(): Boolean {
            return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.lowercase().contains("vbox")
                || Build.FINGERPRINT.lowercase().contains("test-keys")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for")
                || Build.MODEL.contains("sdk_gphone")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.HARDWARE.contains("cuttlefish")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator"))
        }
    }
}
