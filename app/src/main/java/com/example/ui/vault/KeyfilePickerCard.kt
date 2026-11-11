package com.example.ui.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CalcClearKeyText
import com.example.ui.theme.CalcTextMuted
import com.example.ui.theme.CalcTextPrimary
import com.example.ui.theme.CalcTextSecondary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VaultAmberWarning
import com.example.ui.theme.VaultBorderSubtle
import com.example.ui.theme.VaultEmeraldSuccess
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceElevated
import com.example.viewmodel.XomeViewModel

private const val RECOMMENDED_MIN_KEYFILE_BYTES = 1024L

/**
 * Reusable zero-fingerprint Keyfile selection card.
 * Displays transient metadata only while the wizard/sheet is open; never persists path or hash.
 */
@Composable
internal fun KeyfilePickerCard(
    selectedKeyfile: XomeViewModel.KeyfileSelection?,
    isHashing: Boolean,
    errorText: String?,
    accent: Color,
    title: String,
    subtitle: String,
    tipText: String,
    onRequestPickFile: () -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "keyfile"
) {
    val zh = LocalAppStrings.current.language == AppLanguage.ZH
    val hasValidFile = selectedKeyfile != null && errorText == null
    val isSmallFile = selectedKeyfile != null && selectedKeyfile.byteCount < RECOMMENDED_MIN_KEYFILE_BYTES

    val borderColor = when {
        errorText != null -> CalcClearKeyText.copy(alpha = 0.72f)
        hasValidFile && isSmallFile -> VaultAmberWarning.copy(alpha = 0.65f)
        hasValidFile -> VaultEmeraldSuccess.copy(alpha = 0.65f)
        else -> accent.copy(alpha = 0.45f)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = VaultSurfaceElevated,
        border = BorderStroke(1.2.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("${tagPrefix}_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        hasValidFile -> VaultEmeraldSuccess.copy(alpha = 0.14f)
                        else -> accent.copy(alpha = 0.14f)
                    }
                ) {
                    Box(
                        modifier = Modifier.size(42.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isHashing) {
                            CircularProgressIndicator(
                                strokeWidth = 2.2.dp,
                                color = accent,
                                modifier = Modifier.size(20.dp)
                            )
                        } else if (hasValidFile) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = VaultEmeraldSuccess,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = CalcTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isHashing) {
                        Text(
                            text = if (zh) "正在提取 256-bit 钥匙摘要..." else "Computing 256-bit Keyfile digest...",
                            style = MaterialTheme.typography.bodySmall,
                            color = accent
                        )
                    } else if (selectedKeyfile != null) {
                        Text(
                            text = selectedKeyfile.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CalcTextPrimary,
                            fontFamily = JetBrainsMonoFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("${tagPrefix}_selected_name")
                        )
                    } else {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = CalcTextSecondary
                        )
                    }
                }

                OutlinedButton(
                    enabled = !isHashing,
                    onClick = onRequestPickFile,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    border = BorderStroke(
                        1.dp,
                        if (hasValidFile) VaultBorderSubtle else accent.copy(alpha = 0.65f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (hasValidFile) VaultSurface else accent.copy(alpha = 0.14f),
                        contentColor = if (hasValidFile) CalcTextPrimary else accent
                    ),
                    modifier = Modifier.testTag("${tagPrefix}_pick_button")
                ) {
                    Text(
                        text = when {
                            selectedKeyfile != null -> if (zh) "更换" else "Change"
                            else -> if (zh) "选择文件" else "Choose"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }
            }

            AnimatedVisibility(visible = errorText != null || selectedKeyfile != null || tipText.isNotBlank()) {
                when {
                    errorText != null -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = CalcClearKeyText,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = errorText,
                                style = MaterialTheme.typography.bodySmall,
                                color = CalcClearKeyText,
                                modifier = Modifier.testTag("${tagPrefix}_error")
                            )
                        }
                    }
                    selectedKeyfile != null -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = VaultSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSmallFile) Icons.Default.WarningAmber else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isSmallFile) VaultAmberWarning else VaultEmeraldSuccess,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (isSmallFile) {
                                        if (zh) {
                                            "文件较小（${formatByteSize(selectedKeyfile.byteCount)}），建议换用 >1 KB 且不易被压缩的原文件"
                                        } else {
                                            "Small file (${formatByteSize(selectedKeyfile.byteCount)}). Prefer an unmodified file >1 KB."
                                        }
                                    } else {
                                        if (zh) {
                                            "已提取 256-bit 零指纹摘要（${formatByteSize(selectedKeyfile.byteCount)}）· 不记录文件名与特征"
                                        } else {
                                            "256-bit zero-fingerprint digest ready (${formatByteSize(selectedKeyfile.byteCount)})"
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSmallFile) VaultAmberWarning else CalcTextSecondary
                                )
                            }
                        }
                    }
                    else -> {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = CalcTextMuted,
                                modifier = Modifier
                                    .padding(top = 1.dp)
                                    .size(14.dp)
                            )
                            Text(
                                text = tipText,
                                style = MaterialTheme.typography.labelSmall,
                                color = CalcTextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}
