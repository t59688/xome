package com.example.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.VaultSurface
import com.example.ui.vault.XomeUpdateSettings
import com.example.update.UpdatePhase
import com.example.update.UpdateRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppUpdateHost(
    repository: UpdateRepository,
    showSettings: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit
) {
    val state by repository.state.collectAsStateWithLifecycle()
    val zh = LocalAppStrings.current.language == AppLanguage.ZH
    var showPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(state.phase, state.release?.version, showSettings) {
        if (showSettings) repository.dismissPrompt()
        showPrompt = !showSettings && state.phase == UpdatePhase.AVAILABLE && repository.shouldPrompt()
    }

    fun dismissPrompt() {
        repository.dismissPrompt()
        showPrompt = false
    }

    if (showPrompt) {
        AlertDialog(
            modifier = Modifier.testTag("app_update_prompt"),
            onDismissRequest = ::dismissPrompt,
            title = { Text(if (zh) "发现新版本" else "Update available") },
            text = { Text(if (zh) "Xome ${state.release?.version?.name} 已发布，可查看说明并选择下载。"
                else "Xome ${state.release?.version?.name} is available. Review the release and choose whether to download.") },
            confirmButton = {
                TextButton(onClick = { dismissPrompt(); onOpen() }) {
                    Text(if (zh) "查看更新" else "View update")
                }
            },
            dismissButton = {
                TextButton(onClick = ::dismissPrompt, modifier = Modifier.testTag("update_remind_later")) {
                    Text(if (zh) "稍后" else "Later")
                }
            }
        )
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = onClose,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VaultSurface,
            modifier = Modifier.testTag("app_update_sheet")
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding()) {
                XomeUpdateSettings(repository)
            }
        }
    }
}
