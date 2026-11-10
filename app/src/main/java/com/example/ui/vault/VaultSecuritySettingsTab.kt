package com.example.ui.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.RootSecurityDetector
import com.example.crypto.VaultSessionManager
import com.example.data.VaultSpaceMetaEntity
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CalcAccentOrange
import com.example.ui.theme.CalcClearKeyText
import com.example.ui.theme.CalcTextMuted
import com.example.ui.theme.CalcTextPrimary
import com.example.ui.theme.CalcTextSecondary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VaultAmberWarning
import com.example.ui.theme.VaultBorderSubtle
import com.example.ui.theme.VaultCyanPrimary
import com.example.ui.theme.VaultEmeraldSuccess
import com.example.ui.theme.VaultPurpleDecoy
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceElevated

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
    onToggleFlagSecure: (Boolean) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onChangeSequence: (oldSeq: String, newSeq: String, confirmSeq: String, cb: (Boolean, String) -> Unit) -> Unit,
    onCreateDecoySpace: (seq: String, confirmSeq: String, alias: String, isDecoy: Boolean, switchNow: Boolean, cb: (Boolean, String) -> Unit) -> Unit,
    onExportSelfHealingArchive: () -> Unit,
    onImportSelfHealingArchive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    var oldPin by rememberSaveable { mutableStateOf("") }
    var newPin by rememberSaveable { mutableStateOf("") }
    var confirmNewPin by rememberSaveable { mutableStateOf("") }
    var pinFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    var decoyAlias by rememberSaveable { mutableStateOf(strings.defaultDecoySpaceAlias) }
    var decoyPin by rememberSaveable { mutableStateOf("") }
    var decoyConfirmPin by rememberSaveable { mutableStateOf("") }
    var isDecoyMode by rememberSaveable { mutableStateOf(true) }
    var switchImmediately by rememberSaveable { mutableStateOf(false) }
    var decoyFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    LaunchedEffect(strings.language) {
        if (decoyAlias in listOf("Guest Space", "访客空间", "Decoy Travel Space", "诱饵旅行空间")) {
            decoyAlias = strings.defaultDecoySpaceAlias
        }
    }

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

        // 2. Language & Screenshot Protection Card
        LanguageAndPrivacyCard(
            currentLanguage = strings.language,
            report = securityReport,
            flagSecureEnabled = flagSecureEnabled,
            isStreamingEmulator = isStreamingEmulator,
            onSelectLanguage = onSelectLanguage,
            onToggleFlagSecure = onToggleFlagSecure
        )

        // 3. Change Unlock Sequence Card
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
                        imageVector = Icons.Default.LockReset,
                        contentDescription = null,
                        tint = VaultCyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = strings.zeroReencryptionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = CalcTextPrimary
                        )
                        Text(
                            text = strings.zeroReencryptionSubtitle(session.spaceId),
                            style = MaterialTheme.typography.labelSmall,
                            color = CalcTextSecondary
                        )
                    }
                }

                OutlinedTextField(
                    value = oldPin,
                    onValueChange = { oldPin = it },
                    label = { Text(strings.currentTriggerSequenceLabel) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_pin_old_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VaultCyanPrimary,
                        unfocusedBorderColor = VaultBorderSubtle
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it },
                        label = { Text(strings.newSequenceLabel) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("change_pin_new_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CalcAccentOrange,
                            unfocusedBorderColor = VaultBorderSubtle
                        )
                    )
                    OutlinedTextField(
                        value = confirmNewPin,
                        onValueChange = { confirmNewPin = it },
                        label = { Text(strings.confirmNewLabel) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("change_pin_confirm_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CalcAccentOrange,
                            unfocusedBorderColor = VaultBorderSubtle
                        )
                    )
                }

                if (pinFeedback != null) {
                    Text(
                        text = pinFeedback!!.second,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (pinFeedback!!.first) VaultEmeraldSuccess else CalcClearKeyText
                    )
                }

                Button(
                    onClick = {
                        onChangeSequence(oldPin, newPin, confirmNewPin) { ok, msg ->
                            pinFeedback = ok to msg
                            if (ok) {
                                oldPin = ""
                                newPin = ""
                                confirmNewPin = ""
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VaultCyanPrimary,
                        contentColor = Color(0xFF06202B)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("change_pin_submit_button")
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.rotateKekBtn, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }

        // 4. Create Additional Independent Space Card
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
                        imageVector = Icons.Default.AddModerator,
                        contentDescription = null,
                        tint = VaultPurpleDecoy,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = strings.plausibleDeniabilityTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = CalcTextPrimary
                        )
                        Text(
                            text = strings.plausibleDeniabilitySubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = CalcTextSecondary
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.quickSequenceLabel, style = MaterialTheme.typography.labelSmall, color = CalcTextMuted)
                    listOf("2580", "7788", "9988+11").forEach { preset ->
                        FilterChip(
                            selected = decoyPin == preset,
                            onClick = {
                                decoyPin = preset
                                decoyConfirmPin = preset
                            },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VaultPurpleDecoy.copy(alpha = 0.2f),
                                selectedLabelColor = VaultPurpleDecoy
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = decoyAlias,
                    onValueChange = { decoyAlias = it },
                    label = { Text(strings.newSpaceDesignationLabel) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("decoy_alias_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VaultPurpleDecoy,
                        unfocusedBorderColor = VaultBorderSubtle
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = decoyPin,
                        onValueChange = { decoyPin = it },
                        label = { Text(strings.newSpaceSequenceLabel) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("decoy_pin_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VaultPurpleDecoy,
                            unfocusedBorderColor = VaultBorderSubtle
                        )
                    )
                    OutlinedTextField(
                        value = decoyConfirmPin,
                        onValueChange = { decoyConfirmPin = it },
                        label = { Text(strings.confirmSequenceLabel) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("decoy_confirm_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VaultPurpleDecoy,
                            unfocusedBorderColor = VaultBorderSubtle
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.seedDecoyFilesTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CalcTextPrimary
                        )
                        Text(
                            text = strings.seedDecoyFilesSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = CalcTextMuted
                        )
                    }
                    Switch(
                        checked = isDecoyMode,
                        onCheckedChange = { isDecoyMode = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = VaultPurpleDecoy)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.switchIntoNewSpaceLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CalcTextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = switchImmediately,
                        onCheckedChange = { switchImmediately = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = VaultCyanPrimary)
                    )
                }

                if (decoyFeedback != null) {
                    Text(
                        text = decoyFeedback!!.second,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (decoyFeedback!!.first) VaultEmeraldSuccess else CalcClearKeyText
                    )
                }

                Button(
                    onClick = {
                        onCreateDecoySpace(
                            decoyPin,
                            decoyConfirmPin,
                            decoyAlias,
                            isDecoyMode,
                            switchImmediately
                        ) { ok, msg ->
                            decoyFeedback = ok to msg
                            if (ok) {
                                decoyPin = ""
                                decoyConfirmPin = ""
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VaultPurpleDecoy,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("create_decoy_space_button")
                ) {
                    Text(strings.provisionIndependentSpaceBtn, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }

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
                        border = BorderStroke(1.dp, VaultCyanPrimary.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("export_self_heal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = null,
                            tint = VaultCyanPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.exportSysVaultDataBtn,
                            color = VaultCyanPrimary,
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

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun LanguageAndPrivacyCard(
    currentLanguage: AppLanguage,
    report: RootSecurityDetector.EnvironmentSecurityReport,
    flagSecureEnabled: Boolean,
    isStreamingEmulator: Boolean,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleFlagSecure: (Boolean) -> Unit
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
                        tint = VaultCyanPrimary,
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
                                selectedContainerColor = VaultCyanPrimary,
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
    val info = session.headerInfo

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
                        tint = VaultCyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = spaceMeta?.spaceAlias ?: strings.headerTelemetryTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = CalcTextPrimary
                        )
                        Text(
                            text = "$totalFilesCount files · ${formatByteSize(totalPlainBytes)} · $totalSpacesCount space(s)",
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
                        text = "Space ID : ${session.spaceId}.header (128B)\n" +
                            "KDF      : Argon2id (${info.memoryCostKb / 1024}MB, t=${info.timeCost}, p=${info.parallelism})\n" +
                            "Cipher   : AES-256-GCM + SQLCipher",
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
