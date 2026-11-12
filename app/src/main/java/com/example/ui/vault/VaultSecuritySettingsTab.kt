package com.example.ui.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddModerator
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.RootSecurityDetector
import com.example.crypto.VaultSessionManager
import com.example.data.VaultSpaceMetaEntity
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CalcAccentCopper
import com.example.ui.theme.CalcClearKeyText
import com.example.ui.theme.CalcTextMuted
import com.example.ui.theme.CalcTextPrimary
import com.example.ui.theme.CalcTextSecondary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VaultAmberWarning
import com.example.ui.theme.VaultBorderSubtle
import com.example.ui.theme.VaultAccentPrimary
import com.example.ui.theme.VaultEmeraldSuccess
import com.example.ui.theme.VaultPurpleDecoy
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceElevated
import com.example.update.UpdateRepository
import com.example.viewmodel.XomeViewModel

@Composable
fun VaultSecuritySettingsTab(
    session: VaultSessionManager.ActiveVaultSession,
    spaceMeta: VaultSpaceMetaEntity?,
    totalSpacesCount: Int,
    totalFilesCount: Int,
    totalPlainBytes: Long,
    securityReport: RootSecurityDetector.EnvironmentSecurityReport,
    flagSecureEnabled: Boolean,
    isStreamingEmulator: Boolean,
    updateRepository: UpdateRepository,
    onToggleFlagSecure: (Boolean) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onChangeSequence: (oldSeq: String, newSeq: String, confirmSeq: String, cb: (Boolean, String) -> Unit) -> Unit,
    onRotateKeyfile: (currentSeq: String, newPepper: ByteArray?, cb: (Boolean, String) -> Unit) -> Unit,
    onVerifyCurrentSequence: (String, (Boolean) -> Unit) -> Unit,
    onCreateDecoySpace: (seq: String, confirmSeq: String, keyfilePepper: ByteArray?, alias: String, isDecoy: Boolean, seedFiles: Boolean, switchNow: Boolean, cb: (Boolean, String) -> Unit) -> Unit,
    onExtractKeyfileDigest: suspend (android.net.Uri) -> Result<XomeViewModel.KeyfileSelection>,
    onLaunchKeyfilePicker: ((android.net.Uri?) -> Unit) -> Unit,
    onExportSelfHealingArchive: () -> Unit,
    onImportSelfHealingArchive: () -> Unit,
    modifier: Modifier = Modifier,
    hasAllFilesAccess: Boolean = false,
    onRequestAllFilesAccess: () -> Unit = {}
) {
    val strings = LocalAppStrings.current

    // Sensitive wizard inputs are ephemeral and never persisted through rememberSaveable.
    var showCreateWizard by remember { mutableStateOf(false) }
    var showChangeWizard by remember { mutableStateOf(false) }
    var showRotateKeyfileWizard by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Space Summary Card (Clean metrics + optional expandable technical details)
        SpaceOverviewCard(
            session = session,
            spaceMeta = spaceMeta,
            totalSpacesCount = totalSpacesCount,
            totalFilesCount = totalFilesCount,
            totalPlainBytes = totalPlainBytes
        )

        // 2. Language, File Access & Screenshot Protection Card
        LanguageAndPrivacyCard(
            currentLanguage = strings.language,
            report = securityReport,
            flagSecureEnabled = flagSecureEnabled,
            isStreamingEmulator = isStreamingEmulator,
            hasAllFilesAccess = hasAllFilesAccess,
            onSelectLanguage = onSelectLanguage,
            onToggleFlagSecure = onToggleFlagSecure,
            onRequestAllFilesAccess = onRequestAllFilesAccess
        )

        // Reveal only one task at a time: details and secrets live in the dedicated wizard.
        VaultWizardEntry(
            title = strings.plausibleDeniabilityTitle,
            subtitle = strings.plausibleDeniabilitySubtitle,
            icon = Icons.Default.AddModerator,
            accent = VaultPurpleDecoy,
            tag = "create_decoy_space_button",
            onClick = { showCreateWizard = true }
        )
        VaultWizardEntry(
            title = strings.zeroReencryptionTitle,
            subtitle = strings.zeroReencryptionSubtitle(session.spaceId),
            icon = Icons.Default.LockReset,
            accent = VaultAccentPrimary,
            tag = "change_pin_submit_button",
            onClick = { showChangeWizard = true }
        )
        VaultWizardEntry(
            title = strings.rotateKeyfileTitle,
            subtitle = strings.rotateKeyfileSubtitle,
            icon = Icons.Default.AttachFile,
            accent = VaultEmeraldSuccess,
            tag = "rotate_keyfile_button",
            onClick = { showRotateKeyfileWizard = true }
        )

        // 5. Backup & Restore Card (Clean single-line buttons that never wrap)
        Surface(
            color = VaultSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, VaultBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = VaultEmeraldSuccess,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = strings.selfHealingPersistenceTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = CalcTextPrimary
                        )
                        Text(
                            text = strings.selfHealingPersistenceSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = CalcTextSecondary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportSelfHealingArchive,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, VaultAccentPrimary.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("export_self_heal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = null,
                            tint = VaultAccentPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.exportSysVaultDataBtn,
                            color = VaultAccentPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onImportSelfHealingArchive,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, VaultEmeraldSuccess.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("import_self_heal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = null,
                            tint = VaultEmeraldSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.restoreBundleBtn,
                            color = VaultEmeraldSuccess,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        XomeUpdateSettings(updateRepository)

        Spacer(modifier = Modifier.height(28.dp))
    }

    if (showCreateWizard) {
        CreateIndependentSpaceWizard(
            initialAlias = strings.defaultDecoySpaceAlias,
            onExtractKeyfileDigest = onExtractKeyfileDigest,
            onLaunchKeyfilePicker = onLaunchKeyfilePicker,
            onCreate = onCreateDecoySpace,
            onClose = { showCreateWizard = false }
        )
    }
    if (showChangeWizard) {
        ChangeUnlockSequenceWizard(
            onVerify = onVerifyCurrentSequence,
            onChange = onChangeSequence,
            onClose = { showChangeWizard = false }
        )
    }
    if (showRotateKeyfileWizard) {
        RotateKeyfileWizard(
            onVerify = onVerifyCurrentSequence,
            onExtractKeyfileDigest = onExtractKeyfileDigest,
            onLaunchKeyfilePicker = onLaunchKeyfilePicker,
            onRotateKeyfile = onRotateKeyfile,
            onClose = { showRotateKeyfileWizard = false }
        )
    }
}

@Composable
private fun LanguageAndPrivacyCard(
    currentLanguage: AppLanguage,
    report: RootSecurityDetector.EnvironmentSecurityReport,
    flagSecureEnabled: Boolean,
    isStreamingEmulator: Boolean,
    hasAllFilesAccess: Boolean,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleFlagSecure: (Boolean) -> Unit,
    onRequestAllFilesAccess: () -> Unit
) {
    val strings = LocalAppStrings.current

    Surface(
        color = VaultSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, VaultBorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("language_switcher_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Language selector row
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
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = VaultAccentPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = strings.languageCardTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = CalcTextPrimary
                        )
                        Text(
                            text = strings.languageCardSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = CalcTextSecondary
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        val selected = currentLanguage == lang
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectLanguage(lang) },
                            label = {
                                Text(
                                    text = lang.nativeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VaultAccentPrimary,
                                selectedLabelColor = Color(0xFF06202B)
                            ),
                            modifier = Modifier.testTag("select_lang_${lang.code}")
                        )
                    }
                }
            }

            // Screenshot protection toggle row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("root_security_banner"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (report.isRootOrHookDetected) Icons.Default.WarningAmber else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (report.isRootOrHookDetected) VaultAmberWarning else VaultEmeraldSuccess,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = strings.hardwareFlagSecureTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = CalcTextPrimary
                        )
                        Text(
                            text = if (isStreamingEmulator) {
                                strings.hardwareFlagSecureEmulatorSub
                            } else {
                                strings.hardwareFlagSecureDeviceSub
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = CalcTextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = flagSecureEnabled,
                    onCheckedChange = onToggleFlagSecure,
                    colors = SwitchDefaults.colors(checkedThumbColor = VaultEmeraldSuccess),
                    modifier = Modifier.testTag("flag_secure_switch")
                )
            }

            // Full file management access row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("all_files_permission_settings_row"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = if (hasAllFilesAccess) VaultEmeraldSuccess else VaultAmberWarning,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = strings.allFilesAccessTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = CalcTextPrimary
                        )
                        Text(
                            text = if (hasAllFilesAccess) strings.allFilesAccessGrantedSub else strings.allFilesAccessMissingSub,
                            style = MaterialTheme.typography.labelSmall,
                            color = CalcTextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (hasAllFilesAccess) {
                    Surface(
                        color = VaultEmeraldSuccess.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = strings.grantedBadge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = VaultEmeraldSuccess,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = onRequestAllFilesAccess,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VaultAccentPrimary,
                            contentColor = Color(0xFF06202B)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("settings_grant_all_files_button")
                    ) {
                        Text(
                            text = strings.grantAllFilesBtn,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpaceOverviewCard(
    session: VaultSessionManager.ActiveVaultSession,
    spaceMeta: VaultSpaceMetaEntity?,
    totalSpacesCount: Int,
    totalFilesCount: Int,
    totalPlainBytes: Long
) {
    val strings = LocalAppStrings.current
    var showTechnicalDetails by rememberSaveable { mutableStateOf(false) }
    val profile = session.kdfProfile

    Surface(
        color = VaultSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, VaultBorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showTechnicalDetails = !showTechnicalDetails }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = VaultAccentPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = spaceMeta?.spaceAlias ?: strings.headerTelemetryTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = CalcTextPrimary
                        )
                        Text(
                            text = if (strings.language == AppLanguage.ZH) {
                                "$totalFilesCount 个文件 · ${formatByteSize(totalPlainBytes)} · $totalSpacesCount 个空间"
                            } else {
                                "$totalFilesCount files · ${formatByteSize(totalPlainBytes)} · $totalSpacesCount spaces"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = CalcTextSecondary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = if (spaceMeta?.isDecoy == true) VaultPurpleDecoy.copy(alpha = 0.16f) else VaultEmeraldSuccess.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (spaceMeta?.isDecoy == true) strings.decoySpaceBadge else strings.primarySpaceBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (spaceMeta?.isDecoy == true) VaultPurpleDecoy else VaultEmeraldSuccess,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Icon(
                        imageVector = if (showTechnicalDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = CalcTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = showTechnicalDetails) {
                Surface(
                    color = VaultSurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Container : ${session.spaceId}.dat (8,208 B · 64 chaff slots · magic-free)\n" +
                            "Keyfile   : 256-bit zero-fingerprint digest (Keystore-sealed)\n" +
                            "KDF       : Argon2id (${profile.memoryCostKb / 1024} MiB, t=${profile.timeCost}, p=${profile.parallelism}) + HMAC\n" +
                            "Cipher    : Magic-free AES-256-GCM (64KB) + SQLCipher",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = CalcTextSecondary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

fun formatByteSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(java.util.Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(java.util.Locale.US, "%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format(java.util.Locale.US, "%.2f GB", gb)
}
