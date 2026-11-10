package com.example.ui.vault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.RootSecurityDetector
import com.example.crypto.VaultSessionManager
import com.example.data.VaultSpaceMetaEntity
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
    onChangeSequence: (oldSeq: String, newSeq: String, confirmSeq: String, cb: (Boolean, String) -> Unit) -> Unit,
    onCreateDecoySpace: (seq: String, confirmSeq: String, alias: String, isDecoy: Boolean, switchNow: Boolean, cb: (Boolean, String) -> Unit) -> Unit,
    onExportSelfHealingArchive: () -> Unit,
    onImportSelfHealingArchive: () -> Unit,
    modifier: Modifier = Modifier
) {
    var oldPin by rememberSaveable { mutableStateOf("") }
    var newPin by rememberSaveable { mutableStateOf("") }
    var confirmNewPin by rememberSaveable { mutableStateOf("") }
    var pinFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    var decoyAlias by rememberSaveable { mutableStateOf("Decoy Travel Space") }
    var decoyPin by rememberSaveable { mutableStateOf("") }
    var decoyConfirmPin by rememberSaveable { mutableStateOf("") }
    var isDecoyMode by rememberSaveable { mutableStateOf(true) }
    var switchImmediately by rememberSaveable { mutableStateOf(false) }
    var decoyFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Module 6.3: Root / Hook Environment Non-Blocking Security Banner
        RootEnvironmentSecurityCard(report = securityReport)

        // 2. Active Space 128-Byte Binary Envelope Header Inspector
        BinaryHeaderInspectorCard(
            session = session,
            spaceMeta = spaceMeta,
            totalSpacesCount = totalSpacesCount,
            totalFilesCount = totalFilesCount,
            totalPlainBytes = totalPlainBytes
        )

        // 3. Module 4.4: Zero Re-encryption Password Change (< 500ms)
        Surface(
            color = VaultSurface,
            shape = RoundedCornerShape(18.dp),
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
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Zero Re-encryption Sequence Rotation",
                            style = MaterialTheme.typography.titleMedium,
                            color = CalcTextPrimary
                        )
                        Text(
                            text = "Re-wraps the 256-bit MasterKey inside ${session.spaceId}.header without touching .db or .dat blobs (<500ms).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CalcTextSecondary
                        )
                    }
                }

                OutlinedTextField(
                    value = oldPin,
                    onValueChange = { oldPin = it },
                    label = { Text("Current Trigger Sequence") },
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
                        label = { Text("New Sequence (4–16)") },
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
                        label = { Text("Confirm New") },
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
                        contentColor = Color(0xFF001F26)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("change_pin_submit_button")
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Rotate KEK & Re-Seal 128B Header", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 4. Module 3: Plausible Deniability — Create Independent Private / Decoy Space
        Surface(
            color = VaultSurface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, VaultPurpleDecoy.copy(alpha = 0.45f)),
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
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Plausible Deniability: New Independent Space",
                            style = MaterialTheme.typography.titleMedium,
                            color = CalcTextPrimary
                        )
                        Text(
                            text = "Provisions an unlinked 128B .header + isolated SQLCipher .db unlocked by a separate calculator sequence.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CalcTextSecondary
                        )
                    }
                }

                // Quick decoy sequence presets
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Quick Sequence:", style = MaterialTheme.typography.labelSmall, color = CalcTextMuted)
                    listOf("2580", "7788", "9988+11").forEach { preset ->
                        FilterChip(
                            selected = decoyPin == preset,
                            onClick = {
                                decoyPin = preset
                                decoyConfirmPin = preset
                            },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VaultPurpleDecoy.copy(alpha = 0.22f),
                                selectedLabelColor = VaultPurpleDecoy
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = decoyAlias,
                    onValueChange = { decoyAlias = it },
                    label = { Text("New Space Designation") },
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
                        label = { Text("New Space Sequence") },
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
                        label = { Text("Confirm Sequence") },
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
                            text = "Seed Innocuous Decoy Files",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CalcTextPrimary
                        )
                        Text(
                            text = "Pre-populates harmless folders for plausible deniability",
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
                        text = "Switch into new space immediately upon creation",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CalcTextSecondary
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
                        .height(48.dp)
                        .testTag("create_decoy_space_button")
                ) {
                    Text("Provision Independent Encrypted Space", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 5. Self-Healing External Storage & Archive Sync Card
        Surface(
            color = VaultSurface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, VaultBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = VaultEmeraldSuccess,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Self-Healing Vault Persistence (.sys_vault_data)",
                            style = MaterialTheme.typography.titleMedium,
                            color = CalcTextPrimary
                        )
                        Text(
                            text = "Export or restore the raw encrypted directory tree (spaces/*.header, db/*.db, blobs/*.dat) for cross-reinstall recovery.",
                            style = MaterialTheme.typography.bodyMedium,
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
                        border = BorderStroke(1.dp, VaultCyanPrimary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_self_heal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = null,
                            tint = VaultCyanPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export .sys_vault_data", color = VaultCyanPrimary, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onImportSelfHealingArchive,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, VaultEmeraldSuccess.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("import_self_heal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = null,
                            tint = VaultEmeraldSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore Bundle", color = VaultEmeraldSuccess, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun RootEnvironmentSecurityCard(
    report: RootSecurityDetector.EnvironmentSecurityReport
) {
    val bgColor = if (report.isRootOrHookDetected) {
        VaultAmberWarning.copy(alpha = 0.14f)
    } else {
        VaultEmeraldSuccess.copy(alpha = 0.12f)
    }
    val borderColor = if (report.isRootOrHookDetected) {
        VaultAmberWarning.copy(alpha = 0.55f)
    } else {
        VaultEmeraldSuccess.copy(alpha = 0.45f)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("root_security_banner")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (report.isRootOrHookDetected) Icons.Default.WarningAmber else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (report.isRootOrHookDetected) VaultAmberWarning else VaultEmeraldSuccess,
                modifier = Modifier.size(22.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (report.isRootOrHookDetected) {
                        "Root / Hook Environment Risk Notice"
                    } else {
                        "Environment Integrity Verified • FLAG_SECURE Active"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (report.isRootOrHookDetected) VaultAmberWarning else VaultEmeraldSuccess
                )
                Text(
                    text = if (report.isRootOrHookDetected) {
                        "Privileged Root (su/Magisk) or Xposed hooks were detected (${report.findings.joinToString()}). System-level FLAG_SECURE screenshot protection could be bypassed by root processes."
                    } else {
                        "No su binaries, Magisk mounts, or Xposed hooks detected. Window FLAG_SECURE is enforced and Lifecycle.onStop() instant-wipes the MasterKey."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = CalcTextPrimary
                )
            }
        }
    }
}

@Composable
private fun BinaryHeaderInspectorCard(
    session: VaultSessionManager.ActiveVaultSession,
    spaceMeta: VaultSpaceMetaEntity?,
    totalSpacesCount: Int,
    totalFilesCount: Int,
    totalPlainBytes: Long
) {
    val info = session.headerInfo
    val saltHex = remember(info.salt) {
        info.salt.joinToString("") { "%02X".format(it) }
    }
    val nonceHex = remember(info.mkNonce) {
        info.mkNonce.joinToString("") { "%02X".format(it) }
    }
    val wrappedMkPreview = remember(info.wrappedMk) {
        info.wrappedMk.take(12).joinToString("") { "%02X".format(it) } + "…"
    }

    Surface(
        color = VaultSurfaceElevated,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, VaultBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = VaultCyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "128-BYTE ENVELOPE HEADER TELEMETRY",
                        style = MaterialTheme.typography.labelLarge,
                        color = VaultCyanPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = if (spaceMeta?.isDecoy == true) VaultPurpleDecoy.copy(alpha = 0.2f) else VaultEmeraldSuccess.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (spaceMeta?.isDecoy == true) "DECOY SPACE" else "PRIMARY SPACE",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (spaceMeta?.isDecoy == true) VaultPurpleDecoy else VaultEmeraldSuccess,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = """
                    [0x00..0x07] Magic      : CLCVLT01 (8B ASCII)
                    [0x08..0x17] Argon2 Salt: $saltHex (16B)
                    [0x18..0x23] Argon2id   : m=${info.memoryCostKb}KB, t=${info.timeCost}, p=${info.parallelism}
                    [0x24..0x2F] MK_Nonce   : $nonceHex (12B GCM IV)
                    [0x30..0x5F] Wrapped_MK : $wrappedMkPreview (48B = 32B MK + 16B Tag)
                    [0x60..0x7F] Reserved   : 32B CSPRNG Padding
                    ------------------------------------------------
                    Active Header File      : spaces/${session.spaceId}.header (128 Bytes)
                    SQLCipher Metadata DB   : db/${session.spaceId}.db (AES-256)
                    Provisioned Spaces Pool : $totalSpacesCount independent .header file(s)
                    Encrypted Blobs in Space: $totalFilesCount files (${formatByteSize(totalPlainBytes)})
                """.trimIndent(),
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = CalcTextSecondary
            )
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
