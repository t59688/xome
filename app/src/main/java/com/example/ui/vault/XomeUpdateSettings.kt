package com.example.ui.vault

import android.content.ActivityNotFoundException
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SecurityUpdateGood
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CalcClearKeyText
import com.example.ui.theme.CalcTextPrimary
import com.example.ui.theme.CalcTextSecondary
import com.example.ui.theme.VaultAccentPrimary
import com.example.ui.theme.VaultBorderSubtle
import com.example.ui.theme.VaultEmeraldSuccess
import com.example.ui.theme.VaultSurface
import com.example.update.UpdateChannel
import com.example.update.UpdatePhase
import com.example.update.UpdateRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Shared update controls for the calculator entry and unlocked settings. */
@Composable
internal fun XomeUpdateSettings(repository: UpdateRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zh = LocalAppStrings.current.language == AppLanguage.ZH
    val state by repository.state.collectAsStateWithLifecycle()
    val channel by repository.channel.collectAsStateWithLifecycle()
    var showNotes by remember { mutableStateOf(false) }

    LaunchedEffect(repository) { repository.refresh() }
    LaunchedEffect(repository, state.isDownloading) {
        while (state.isDownloading) {
            delay(1200)
            repository.refresh()
        }
    }

    fun requestInstall() {
        if (!repository.canInstall()) {
            repository.showError(
                if (zh) "请允许此应用安装 APK，返回后再次点击安装"
                else "Allow APK installs, then return and tap Install again"
            )
            try {
                context.startActivity(repository.installPermissionIntent())
            } catch (e: Exception) {
                repository.showError(e.localizedMessage ?: "无法打开安装设置")
            }
            return
        }
        scope.launch {
            try {
                context.startActivity(repository.installerIntent())
            } catch (e: Exception) {
                repository.showError(e.localizedMessage ?: "无法启动安装程序")
            }
        }
    }

    Surface(
        color = VaultSurface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, VaultBorderSubtle),
        modifier = Modifier.fillMaxWidth().testTag("vault_update_settings")
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
                    imageVector = Icons.Default.SecurityUpdateGood,
                    contentDescription = null,
                    tint = VaultAccentPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (zh) "应用更新" else "App updates",
                        style = MaterialTheme.typography.titleMedium,
                        color = CalcTextPrimary
                    )
                    Text(
                        text = if (zh) "当前版本 ${BuildConfig.VERSION_NAME} · GitHub Releases"
                            else "Version ${BuildConfig.VERSION_NAME} · GitHub Releases",
                        style = MaterialTheme.typography.labelSmall,
                        color = CalcTextSecondary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UpdateChannel.entries.forEach { option ->
                    FilterChip(
                        selected = channel == option,
                        onClick = { scope.launch { repository.selectChannel(option) } },
                        enabled = !state.isDownloading,
                        label = {
                            Text(if (option == UpdateChannel.STABLE) {
                                if (zh) "正式版" else "Stable"
                            } else "Beta")
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VaultAccentPrimary,
                            selectedLabelColor = Color(0xFF21150E)
                        ),
                        modifier = Modifier.testTag("update_channel_${option.name.lowercase(Locale.ROOT)}")
                    )
                }
            }

            Text(
                text = when (state.phase) {
                    UpdatePhase.IDLE -> if (zh) "使用应用时自动定期检查，也可手动检查" else "Checked periodically while using the app; manual checks are also available"
                    UpdatePhase.CHECKING -> if (zh) "正在查询 GitHub…" else "Checking GitHub…"
                    UpdatePhase.AVAILABLE -> if (zh) "发现新版本 ${state.release?.version?.name}" else "New version ${state.release?.version?.name}"
                    UpdatePhase.DOWNLOADING -> if (zh) "下载中 ${updateBytes(state.downloadedBytes)} / ${updateBytes(state.totalBytes)}"
                        else "Downloading ${updateBytes(state.downloadedBytes)} / ${updateBytes(state.totalBytes)}"
                    UpdatePhase.PAUSED -> if (zh) "下载已暂停，可等待系统恢复" else "Download paused by Android"
                    UpdatePhase.VERIFYING -> if (zh) "正在校验安装包" else "Verifying APK integrity"
                    UpdatePhase.READY -> if (zh) "安装包已验证，可以安装" else "APK verified and ready to install"
                    UpdatePhase.FAILED -> if (zh) "操作失败，可以重试" else "Update operation failed"
                    UpdatePhase.UP_TO_DATE -> if (zh) "当前已是最新版本" else "Already up to date"
                    UpdatePhase.NO_RELEASE -> if (zh) "暂未发布可安装版本" else "No published installable release yet"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when (state.phase) {
                    UpdatePhase.AVAILABLE, UpdatePhase.READY -> VaultEmeraldSuccess
                    UpdatePhase.FAILED -> CalcClearKeyText
                    else -> CalcTextSecondary
                }
            )

            state.release?.let { release ->
                if (state.hasUpdate) {
                    Text(
                        text = release.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = CalcTextPrimary
                    )
                    if (release.notes.isNotBlank()) {
                        Text(
                            text = release.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = CalcTextSecondary,
                            maxLines = if (showNotes) 40 else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = { showNotes = !showNotes }) {
                            Text(if (showNotes) (if (zh) "收起说明" else "Less")
                                else (if (zh) "查看更新说明" else "Release notes"))
                        }
                    }
                    Text(
                        text = "${updateBytes(release.size)} · SHA-256 / APK ${if (zh) "签名校验" else "signature check"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CalcTextSecondary
                    )
                }
            }

            if (state.isDownloading) {
                if (state.totalBytes > 0 && state.phase != UpdatePhase.VERIFYING) {
                    LinearProgressIndicator(
                        progress = { (state.downloadedBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = VaultAccentPrimary
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = VaultAccentPrimary)
                }
            }
            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = CalcClearKeyText
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    state.phase == UpdatePhase.READY -> Button(
                        onClick = ::requestInstall,
                        colors = ButtonDefaults.buttonColors(containerColor = VaultAccentPrimary),
                        modifier = Modifier.testTag("update_install_button")
                    ) { Text(if (zh) "安装更新" else "Install", color = Color(0xFF21150E)) }
                    state.isDownloading -> OutlinedButton(
                        onClick = { scope.launch { repository.cancelDownload() } },
                        modifier = Modifier.testTag("update_cancel_button")
                    ) { Text(if (zh) "取消下载" else "Cancel") }
                    state.hasUpdate -> Button(
                        onClick = { scope.launch { repository.download() } },
                        colors = ButtonDefaults.buttonColors(containerColor = VaultAccentPrimary),
                        modifier = Modifier.testTag("update_download_button")
                    ) { Text(if (zh) "下载更新" else "Download", color = Color(0xFF21150E)) }
                }
                OutlinedButton(
                    onClick = { scope.launch { repository.check(force = true) } },
                    enabled = !state.isDownloading && state.phase != UpdatePhase.CHECKING,
                    modifier = Modifier.testTag("update_check_button")
                ) {
                    if (state.phase == UpdatePhase.CHECKING) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(if (zh) "检查更新" else "Check updates")
                }
                if (state.phase == UpdatePhase.READY) {
                    TextButton(onClick = { scope.launch { repository.cancelDownload() } }) {
                        Text(if (zh) "删除安装包" else "Remove APK")
                    }
                }
            }
        }
    }
}

private fun updateBytes(bytes: Long): String =
    "%.1f MB".format(Locale.ROOT, bytes.coerceAtLeast(0L) / 1048576.0)
