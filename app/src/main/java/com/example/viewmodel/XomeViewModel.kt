package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.calculator.ScientificCalculatorEngine
import com.example.crypto.RandomAccessDecryptedStream
import com.example.crypto.RootSecurityDetector
import com.example.crypto.VaultCryptoEngine
import com.example.crypto.VaultSessionManager
import com.example.data.VaultFileEntity
import com.example.data.VaultFolderEntity
import com.example.data.VaultSpaceMetaEntity
import com.example.storage.VaultStorageManager
import java.io.File
import java.util.Arrays
import kotlinx.coroutines.Dispatchers
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
        val history: List<ScientificCalculatorEngine.CalcHistoryEntry> = emptyList(),
        val isVerifyingBlindSequence: Boolean = false
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

    // Onboarding & Self-Healing state
    private val _needsOnboarding = MutableStateFlow(false)
    val needsOnboarding: StateFlow<Boolean> = _needsOnboarding.asStateFlow()

    private val _isProvisioningOnboarding = MutableStateFlow(false)
    val isProvisioningOnboarding: StateFlow<Boolean> = _isProvisioningOnboarding.asStateFlow()

    private val _onboardingError = MutableStateFlow<String?>(null)
    val onboardingError: StateFlow<String?> = _onboardingError.asStateFlow()

    private val _totalProvisionedSpaces = MutableStateFlow(0)
    val totalProvisionedSpaces: StateFlow<Int> = _totalProvisionedSpaces.asStateFlow()

    // Disguised Calculator state
    private val _calcState = MutableStateFlow(CalculatorUiState())
    val calcState: StateFlow<CalculatorUiState> = _calcState.asStateFlow()

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

    private val _statusBanner = MutableStateFlow<StatusBanner?>(null)
    val statusBanner: StateFlow<StatusBanner?> = _statusBanner.asStateFlow()

    // In-Memory Viewers
    private val _activeImageViewerFileId = MutableStateFlow<Long?>(null)
    val activeImageViewerFileId: StateFlow<Long?> = _activeImageViewerFileId.asStateFlow()

    private val _activeDocumentViewerFile = MutableStateFlow<VaultFileEntity?>(null)
    val activeDocumentViewerFile: StateFlow<VaultFileEntity?> = _activeDocumentViewerFile.asStateFlow()

    private val _decryptedDocumentText = MutableStateFlow<String?>(null)
    val decryptedDocumentText: StateFlow<String?> = _decryptedDocumentText.asStateFlow()

    val environmentSecurityReport: RootSecurityDetector.EnvironmentSecurityReport =
        RootSecurityDetector.inspectEnvironment()

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
        refreshSelfHealingStatus()
        // Observe lock events to reset internal navigation states immediately
        viewModelScope.launch {
            VaultSessionManager.activeSession.collect { session ->
                if (session == null) {
                    _currentFolderId.value = null
                    _folderBreadcrumbs.value = emptyList()
                    _searchQuery.value = ""
                    _activeImageViewerFileId.value = null
                    _activeDocumentViewerFile.value = null
                    _decryptedDocumentText.value = null
                } else {
                    _totalProvisionedSpaces.value = VaultStorageManager.listSpaceHeaderFiles(appContext).size
                }
            }
        }
    }

    /**
     * Module 1 Self-Healing Check:
     * Scans `/sdcard/Documents/.sys_vault_data/spaces/<hex>.header`.
     * - If headers exist: skips onboarding and enters disguised Calculator directly.
     * - If no headers exist: shows initial primary vault sequence setup.
     */
    fun refreshSelfHealingStatus() {
        val headers = VaultStorageManager.listSpaceHeaderFiles(appContext)
        _totalProvisionedSpaces.value = headers.size
        _needsOnboarding.value = headers.isEmpty()
    }

    fun dismissOnboardingToCalculator() {
        _needsOnboarding.value = false
    }

    fun openOnboardingSetup() {
        _onboardingError.value = null
        _needsOnboarding.value = true
    }

    /**
     * Completes first-launch setup by generating `.nomedia`, `spaces/<hex>.header`,
     * and encrypted SQLCipher `db/<hex>.db`.
     */
    fun provisionInitialPrimarySpace(
        rawSequence: String,
        confirmSequence: String,
        spaceAlias: String = "Primary Sovereign Vault",
        enterCalculatorFirst: Boolean = false
    ) {
        val cleanSeq = rawSequence.trim()
        val cleanConfirm = confirmSequence.trim()

        if (cleanSeq.length !in 4..16) {
            _onboardingError.value = "Unlock sequence must be 4 to 16 characters (e.g. 1314 or 9988+11)."
            return
        }
        if (cleanSeq != cleanConfirm) {
            _onboardingError.value = "Confirmation sequence does not match."
            return
        }

        _onboardingError.value = null
        _isProvisioningOnboarding.value = true

        viewModelScope.launch {
            val pinChars = cleanSeq.toCharArray()
            try {
                VaultStorageManager.provisionNewSpace(
                    context = appContext,
                    pinChars = pinChars,
                    spaceAlias = spaceAlias.ifBlank { "Primary Sovereign Vault" },
                    isDecoy = false,
                    seedSampleFiles = true,
                    activateImmediately = !enterCalculatorFirst
                )
                refreshSelfHealingStatus()
                _needsOnboarding.value = false
                if (!enterCalculatorFirst) {
                    showBanner("Primary Vault provisioned (Argon2id + 128B Envelope Header).")
                }
            } catch (e: Exception) {
                _onboardingError.value = "Failed to initialize cryptographic space: ${e.message}"
            } finally {
                Arrays.fill(pinChars, '\u0000')
                _isProvisioningOnboarding.value = false
            }
        }
    }

    // ------------------------------------------------------------------------
    // Module 2: Disguised Scientific Calculator & Blind-Match Interceptor
    // ------------------------------------------------------------------------

    fun onCalculatorInput(token: String) {
        val current = _calcState.value
        if (current.isVerifyingBlindSequence) return

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
        _calcState.value = _calcState.value.copy(
            expression = "",
            livePreview = "",
            lastResult = ""
        )
    }

    fun onCalculatorBackspace() {
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

    /**
     * Triggered when user presses `=` on the disguised calculator:
     * 1. Normalizes raw input sequence (e.g. `1314` or `9988+11`).
     * 2. If length is 4..16 and `.header` files exist, runs Argon2id + AES-GCM tag check
     *    across all `spaces/<hex>.header` in a background coroutine.
     * 3. If a space unlocks: wipes calculator stack and navigates to the unlocked Private Space.
     * 4. If all spaces fail verification: shows NO error message, immediately evaluates the
     *    arithmetic expression and displays the normal math result!
     */
    fun onCalculatorEqualsPressed() {
        val current = _calcState.value
        val rawExpr = current.expression.trim()
        if (rawExpr.isEmpty() || current.isVerifyingBlindSequence) return

        // Normalize display minus/multiply/divide symbols so sequences like 9988+11 match cleanly
        val normalizedCandidate = rawExpr
            .replace("−", "-")
            .replace("×", "*")
            .replace("÷", "/")

        val shouldCheckHeaders = normalizedCandidate.length in 4..16 &&
            VaultStorageManager.hasExistingSpaces(appContext)

        if (!shouldCheckHeaders) {
            performNormalMathEvaluation(rawExpr)
            return
        }

        _calcState.value = current.copy(isVerifyingBlindSequence = true)
        viewModelScope.launch {
            val unlockedSession = VaultStorageManager.tryBlindUnlock(
                context = appContext,
                rawSequence = normalizedCandidate
            ) ?: if (normalizedCandidate != rawExpr) {
                VaultStorageManager.tryBlindUnlock(appContext, rawExpr)
            } else {
                null
            }

            if (unlockedSession != null) {
                // Verification succeeded! Wipe calculator input stack completely
                _calcState.value = _calcState.value.copy(
                    expression = "",
                    livePreview = "",
                    lastResult = "",
                    isVerifyingBlindSequence = false
                )
            } else {
                // Plausible Deniability: All headers failed verification -> perform normal math silently
                _calcState.value = _calcState.value.copy(isVerifyingBlindSequence = false)
                performNormalMathEvaluation(rawExpr)
            }
        }
    }

    private fun performNormalMathEvaluation(rawExpr: String) {
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
                ) + current.history).take(30)

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
        // Only show live preview when expression contains an operator or function
        val hasOperator = expr.any { it in listOf('+', '−', '-', '×', '*', '÷', '/', '^', '%', '!', '√', '(', 'π', 'e') }
        if (!hasOperator) return ""
        return ScientificCalculatorEngine.evaluateExpression(expr, isDegreeMode)
            .map { ScientificCalculatorEngine.formatNumber(it) }
            .getOrDefault("")
    }

    // ------------------------------------------------------------------------
    // Module 3 & 4: Virtual File System, Multi-Space & Streaming File Import
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
            showBanner("Created encrypted folder \"$clean\"")
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
            showBanner("Deleted folder \"${folder.folderName}\" and shredded ${filesInside.size} blobs")
        }
    }

    fun importSelectedUris(uris: List<Uri>, moveAndWipeOriginal: Boolean) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importProgress.value = ImportProgressState(
                isImporting = true,
                currentIndex = 0,
                totalCount = uris.size,
                currentFileName = "Initializing 64KB AES-256-GCM pipeline...",
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
                showBanner("Import error: ${result.errors.first()}", isError = true)
            } else {
                val modeDesc = if (moveAndWipeOriginal) {
                    "Moved ${result.importedCount} files (${result.deletedOriginalsCount} originals wiped)"
                } else {
                    "Encrypted ${result.importedCount} files into 64KB .dat chunks"
                }
                showBanner(modeDesc)
            }
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
                showBanner("Encrypted note \"${created.originalName}\" into blobs/${created.blobFilename.take(10)}….dat")
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
            showBanner("Shredded ${fileEntity.originalName} with CSPRNG overwrite")
        }
    }

    fun renameVaultFile(fileEntity: VaultFileEntity, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        val session = activeSession.value ?: return
        viewModelScope.launch {
            session.database.vaultDao().renameFile(fileEntity.id, clean)
            showBanner("Renamed to \"$clean\" inside SQLCipher DB")
        }
    }

    fun exportFileToPublicStorage(fileEntity: VaultFileEntity) {
        viewModelScope.launch {
            val res = VaultStorageManager.exportEncryptedFileToPublicDownloads(appContext, fileEntity)
            res.fold(
                onSuccess = { path ->
                    showBanner("Decrypted & restored to $path")
                },
                onFailure = { err ->
                    showBanner("Restore failed: ${err.message}", isError = true)
                }
            )
        }
    }

    fun exportFileToCustomSafUri(fileEntity: VaultFileEntity, targetUri: Uri) {
        val session = activeSession.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val masterKey = session.borrowMasterKeyCopy()
            try {
                val tree = VaultStorageManager.resolveVaultTree(appContext)
                val blobFile = File(tree.blobsDir, fileEntity.blobFilename)
                appContext.contentResolver.openOutputStream(targetUri)?.use { out ->
                    VaultCryptoEngine.decryptBlobFileToStream(masterKey, blobFile, out)
                }
                withContext(Dispatchers.Main) {
                    showBanner("Decrypted & exported \"${fileEntity.originalName}\"")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showBanner("Export failed: ${e.message}", isError = true)
                }
            } finally {
                Arrays.fill(masterKey, 0.toByte())
            }
        }
    }

    // ------------------------------------------------------------------------
    // Module 5: In-Memory Streaming Image & Document Viewers
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
        _decryptedDocumentText.value = "Decrypting 64KB AES-256-GCM chunks in RAM..."

        viewModelScope.launch(Dispatchers.IO) {
            val masterKey = session.borrowMasterKeyCopy()
            try {
                val tree = VaultStorageManager.resolveVaultTree(appContext)
                val blobFile = File(tree.blobsDir, fileEntity.blobFilename)
                RandomAccessDecryptedStream(blobFile, masterKey).use { stream ->
                    // Read up to 256KB in memory for text/hex preview without touching disk
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
                    _decryptedDocumentText.value = "Failed to stream-decrypt blob: ${e.message}"
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
        sb.appendLine("=== IN-MEMORY STREAMING BINARY INSPECTOR ===")
        sb.appendLine("Original Name : ${entity.originalName}")
        sb.appendLine("MIME Type     : ${entity.mimeType}")
        sb.appendLine("Plain Size    : ${entity.originalSize} Bytes")
        sb.appendLine("Physical Blob : blobs/${entity.blobFilename} (${blobFile.length()} Bytes)")
        sb.appendLine("Cipher Format : VBLK 76B Header + $totalChunks x 64KB AES-256-GCM Chunks")
        sb.appendLine("--------------------------------------------")
        sb.appendLine("First ${minOf(plainSample.size, 512)} Decrypted Bytes (RAM Stream):")
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
    // Module 4.4 & 3: Zero Re-encryption Password Change & Multi-Space Creation
    // ------------------------------------------------------------------------

    fun changeCurrentSpaceSequence(
        oldSequence: String,
        newSequence: String,
        confirmSequence: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val session = activeSession.value ?: run {
            onComplete(false, "No active vault session.")
            return
        }
        val cleanOld = oldSequence.trim()
        val cleanNew = newSequence.trim()
        val cleanConfirm = confirmSequence.trim()

        if (cleanNew.length !in 4..16) {
            onComplete(false, "New sequence must be 4 to 16 characters.")
            return
        }
        if (cleanNew != cleanConfirm) {
            onComplete(false, "New sequence confirmation does not match.")
            return
        }

        viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            val oldChars = cleanOld.toCharArray()
            val newChars = cleanNew.toCharArray()
            try {
                val usedElsewhere = withContext(Dispatchers.Default) {
                    VaultStorageManager.isSequenceAlreadyUsed(appContext, cleanNew)
                }
                if (usedElsewhere && cleanOld != cleanNew) {
                    onComplete(false, "That sequence is already assigned to another space.")
                    return@launch
                }

                val ok = withContext(Dispatchers.Default) {
                    VaultCryptoEngine.changeSpacePinZeroReencryption(
                        headerFile = session.headerFile,
                        oldPinChars = oldChars,
                        newPinChars = newChars
                    )
                }
                val elapsed = System.currentTimeMillis() - startMs
                if (ok) {
                    val msg = "Key-Wrap Header re-sealed in ${elapsed}ms (Zero .db/.dat re-encryption!)"
                    showBanner(msg)
                    onComplete(true, msg)
                } else {
                    onComplete(false, "Current sequence verification failed (AEADBadTagException).")
                }
            } finally {
                Arrays.fill(oldChars, '\u0000')
                Arrays.fill(newChars, '\u0000')
            }
        }
    }

    /**
     * Provisions an independent Secondary / Decoy Space for Plausible Deniability.
     */
    fun createIndependentDecoySpace(
        newSequence: String,
        confirmSequence: String,
        spaceAlias: String,
        isDecoy: Boolean,
        switchToNewSpaceImmediately: Boolean = false,
        onComplete: (Boolean, String) -> Unit
    ) {
        val cleanSeq = newSequence.trim()
        val cleanConfirm = confirmSequence.trim()

        if (cleanSeq.length !in 4..16) {
            onComplete(false, "Unlock sequence must be 4 to 16 characters.")
            return
        }
        if (cleanSeq != cleanConfirm) {
            onComplete(false, "Sequence confirmation does not match.")
            return
        }

        viewModelScope.launch {
            val alreadyUsed = VaultStorageManager.isSequenceAlreadyUsed(appContext, cleanSeq)
            if (alreadyUsed) {
                onComplete(false, "This sequence already unlocks an existing space. Choose a distinct sequence.")
                return@launch
            }

            val pinChars = cleanSeq.toCharArray()
            try {
                val created = VaultStorageManager.provisionNewSpace(
                    context = appContext,
                    pinChars = pinChars,
                    spaceAlias = spaceAlias.ifBlank { if (isDecoy) "Decoy Space" else "Secondary Vault" },
                    isDecoy = isDecoy,
                    seedSampleFiles = true,
                    activateImmediately = switchToNewSpaceImmediately
                )
                refreshSelfHealingStatus()
                val msg = "Created independent space ${created.spaceId}.header (128B) with isolated SQLCipher DB!"
                showBanner(msg)
                onComplete(true, msg)
            } catch (e: Exception) {
                onComplete(false, "Failed to provision space: ${e.message}")
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
                showBanner("Exported $count encrypted vault files (.header, .db, .dat) for Self-Healing recovery.")
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
                showBanner("Restored $count encrypted items into .sys_vault_data! Enter your sequence + = to unlock.")
            } catch (e: Exception) {
                showBanner("Self-healing import failed: ${e.message}", isError = true)
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
}
