package com.example.ui.vault

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddModerator
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CalcClearKeyText
import com.example.ui.theme.CalcTextMuted
import com.example.ui.theme.CalcTextPrimary
import com.example.ui.theme.CalcTextSecondary
import com.example.ui.theme.VaultAccentPrimary
import com.example.ui.theme.VaultBorderSubtle
import com.example.ui.theme.VaultEmeraldSuccess
import com.example.ui.theme.VaultPurpleDecoy
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceElevated
import com.example.ui.theme.VaultVoidBg

private fun tr(zh: Boolean, zhText: String, enText: String) = if (zh) zhText else enText

/** Compact settings entry; all editable secrets live only inside transient wizard state. */
@Composable
internal fun VaultWizardEntry(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(15.dp),
        color = VaultSurface,
        border = BorderStroke(1.dp, VaultBorderSubtle),
        modifier = Modifier.fillMaxWidth().testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 17.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = accent.copy(alpha = .12f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(icon, contentDescription = null, tint = accent,
                    modifier = Modifier.padding(12.dp).size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    color = CalcTextPrimary, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = CalcTextSecondary)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null,
                tint = CalcTextMuted, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
internal fun CreateIndependentSpaceWizard(
    initialAlias: String,
    onCreate: (String, String, String, Boolean, Boolean, Boolean, (Boolean, String) -> Unit) -> Unit,
    onClose: () -> Unit
) {
    val zh = LocalAppStrings.current.language == AppLanguage.ZH
    val accent = VaultPurpleDecoy
    var step by remember { mutableIntStateOf(0) }
    var alias by remember { mutableStateOf(initialAlias) }
    var sequence by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val seedFiles = remember { mutableStateOf(true) }
    val decoySpace = remember { mutableStateOf(true) }
    val enterNow = remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val canContinue = when (step) {
        0 -> VaultWizardRules.validAlias(alias)
        1 -> VaultWizardRules.confirmedSequence(sequence, confirmation)
        else -> true
    }

    WizardFrame(
        title = tr(zh, "新建独立空间", "Create independent space"),
        subtitle = tr(zh, "不同空间，独立凭据与数据。", "Independent credentials and data for each space."),
        steps = listOf(
            tr(zh, "名称", "Name"), tr(zh, "解锁序列", "Sequence"),
            tr(zh, "选项", "Options"), tr(zh, "创建", "Create")
        ),
        step = step,
        accent = accent,
        processing = processing,
        completed = success,
        enabled = (success || canContinue) && !processing,
        primaryLabel = when {
            success -> tr(zh, "完成", "Done")
            step == 3 -> tr(zh, "创建空间", "Create space")
            else -> tr(zh, "继续", "Continue")
        },
        onBack = {
            if (success) onClose()
            else if (step > 0) {
                error = null
                step--
            } else onClose()
        },
        onClose = onClose,
        onPrimary = {
            error = null
            if (success) {
                onClose()
            } else if (step < 3) {
                if (canContinue) step++
            } else {
                processing = true
                onCreate(
                    sequence.trim(), confirmation.trim(), alias.trim(),
                    decoySpace.value, seedFiles.value, enterNow.value
                ) { ok, msg ->
                    processing = false
                    if (ok) {
                        success = true
                        sequence = ""
                        confirmation = ""
                    } else {
                        error = msg
                    }
                }
            }
        },
        error = error,
        tag = "create_space_wizard"
    ) { page ->
        when (page) {
            0 -> WizardSection(
                icon = Icons.Default.AddModerator,
                title = tr(zh, "为新空间命名", "Name your space"),
                description = tr(zh, "名称只显示在已解锁的空间内。", "The name is shown only inside the unlocked vault."),
                accent = accent
            ) {
                Text(tr(zh, "快捷名称", "Quick names"),
                    style = MaterialTheme.typography.labelLarge, color = CalcTextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        tr(zh, "访客空间", "Guest"),
                        tr(zh, "工作空间", "Work"),
                        tr(zh, "临时空间", "Temporary")
                    ).forEachIndexed { index, preset ->
                        FilterChip(
                            selected = alias == preset,
                            onClick = { alias = preset; decoySpace.value = index == 0 },
                            label = { Text(preset, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = .22f),
                                selectedLabelColor = CalcTextPrimary
                            )
                        )
                    }
                }
                WizardField(
                    value = alias,
                    onChange = { alias = it.take(36) },
                    label = tr(zh, "空间名称", "Space name"),
                    accent = accent,
                    tag = "create_space_name"
                )
                WizardValidationHint(
                    good = VaultWizardRules.validAlias(alias),
                    text = tr(zh, "名称需要 1–36 个字符", "Name must contain 1–36 characters"),
                    accent = accent
                )
            }
            1 -> WizardSection(
                icon = Icons.Default.Key,
                title = tr(zh, "设置解锁序列", "Set the unlock sequence"),
                description = tr(zh,
                    "在计算器中输入此序列并按 =，即可打开新空间。",
                    "Enter this sequence on the calculator and press = to unlock."),
                accent = accent
            ) {
                WizardField(sequence, { sequence = it; error = null },
                    tr(zh, "解锁序列", "Unlock sequence"), accent,
                    "create_space_sequence", secret = true)
                WizardField(confirmation, { confirmation = it; error = null },
                    tr(zh, "再次输入", "Repeat sequence"), accent,
                    "create_space_confirmation", secret = true)
                WizardValidationHint(
                    good = VaultWizardRules.validSequence(sequence),
                    text = tr(zh, "4–16 个字符，支持计算表达式", "4–16 characters; calculator expressions allowed"),
                    accent = accent
                )
                if (confirmation.isNotEmpty()) {
                    WizardValidationHint(
                        good = VaultWizardRules.confirmedSequence(sequence, confirmation),
                        text = tr(zh, if (sequence.trim() == confirmation.trim()) "两次输入一致" else "两次输入不一致",
                            "Both entries must match"),
                        accent = accent
                    )
                }
            }
            2 -> WizardSection(
                icon = Icons.Default.AddModerator,
                title = tr(zh, "个性化新空间", "Space options"),
                description = tr(zh, "可按用途选择，所有选项都能独立决定。",
                    "Choose only the options that fit your use."),
                accent = accent
            ) {
                WizardSwitchRow(
                    title = tr(zh, "访客模式", "Guest mode"),
                    subtitle = tr(zh, "将空间标记为访客用途", "Mark this space as a guest vault"),
                    valueState = decoySpace, accent = accent,
                    tag = "create_space_decoy_switch"
                )
                WizardSwitchRow(
                    title = tr(zh, "生成示例文件", "Include starter files"),
                    subtitle = tr(zh, "填入少量样本文档，便于体验", "Add a few starter files"),
                    valueState = seedFiles, accent = accent,
                    tag = "create_space_seed_switch"
                )
                WizardSwitchRow(
                    title = tr(zh, "创建后立即进入", "Open after creation"),
                    subtitle = tr(zh, "创建完成后切换到新空间", "Switch to the new space when ready"),
                    valueState = enterNow, accent = accent,
                    tag = "create_space_enter_switch"
                )
            }
            else -> WizardSection(
                icon = Icons.Default.CheckCircle,
                title = if (success) tr(zh, "新空间已创建", "Space is ready")
                        else tr(zh, "确认创建", "Review before creating"),
                description = if (success) tr(zh,
                    "数据已独立保存。下次使用设置的解锁序列进入。",
                    "Your space is ready. Use its sequence to unlock next time.")
                else tr(zh, "确认名称与选项后再创建。", "Review the options before creating."),
                accent = if (success) VaultEmeraldSuccess else accent
            ) {
                WizardSummaryRow(tr(zh, "空间名称", "Name"), alias)
                WizardSummaryRow(tr(zh, "解锁序列", "Sequence"),
                    tr(zh, "已设置 · 内容隐藏", "Configured · hidden"))
                WizardSummaryRow(tr(zh, "访客模式", "Guest mode"),
                    tr(zh, if (decoySpace.value) "开启" else "关闭", if (decoySpace.value) "On" else "Off"))
                WizardSummaryRow(tr(zh, "示例文件", "Starter files"),
                    tr(zh, if (seedFiles.value) "生成" else "不生成", if (seedFiles.value) "Include" else "Skip"))
                WizardSummaryRow(tr(zh, "创建后进入", "Open after creation"),
                    tr(zh, if (enterNow.value) "进入" else "留在当前空间",
                        if (enterNow.value) "Open" else "Stay here"))
            }
        }
    }
}

@Composable
internal fun ChangeUnlockSequenceWizard(
    onVerify: (String, (Boolean) -> Unit) -> Unit,
    onChange: (String, String, String, (Boolean, String) -> Unit) -> Unit,
    onClose: () -> Unit
) {
    val zh = LocalAppStrings.current.language == AppLanguage.ZH
    val accent = VaultAccentPrimary
    var step by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf("") }
    var verified by remember { mutableStateOf(false) }
    var next by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var processing by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val canContinue = when (step) {
        0 -> VaultWizardRules.validSequence(current)
        1 -> VaultWizardRules.changedSequence(current, next, confirmation)
        else -> true
    }

    WizardFrame(
        title = tr(zh, "修改解锁密码", "Change unlock sequence"),
        subtitle = tr(zh, "仅更新凭据，不重新加密已有文件。",
            "Update credentials without re-encrypting existing files."),
        steps = listOf(
            tr(zh, "验证", "Verify"), tr(zh, "新序列", "New sequence"),
            tr(zh, "完成", "Finish")
        ),
        step = step,
        accent = accent,
        processing = processing,
        completed = success,
        enabled = (success || canContinue) && !processing,
        primaryLabel = when {
            success -> tr(zh, "完成", "Done")
            step == 0 -> tr(zh, "验证并继续", "Verify and continue")
            step == 1 -> tr(zh, "继续", "Continue")
            else -> tr(zh, "更新密码", "Change sequence")
        },
        onBack = {
            if (success) onClose()
            else if (step > 0) { error = null; step-- }
            else onClose()
        },
        onClose = onClose,
        onPrimary = {
            error = null
            when {
                success -> onClose()
                step == 0 && verified -> step = 1
                step == 0 -> {
                    processing = true
                    val verifiedValue = current
                    onVerify(verifiedValue) { valid ->
                        processing = false
                        if (current != verifiedValue) return@onVerify
                        if (valid) { verified = true; step = 1 }
                        else error = tr(zh, "当前解锁序列不正确", "Current sequence is incorrect")
                    }
                }
                step == 1 -> if (canContinue) step = 2
                else -> {
                    processing = true
                    onChange(current, next, confirmation) { ok, msg ->
                        processing = false
                        if (ok) {
                            success = true
                            current = ""
                            next = ""
                            confirmation = ""
                        } else {
                            error = msg
                        }
                    }
                }
            }
        },
        error = error,
        tag = "change_sequence_wizard"
    ) { page ->
        when (page) {
            0 -> WizardSection(
                icon = Icons.Default.LockReset,
                title = tr(zh, "验证当前序列", "Verify current sequence"),
                description = tr(zh, "请先证明你拥有当前空间的访问权限。",
                    "Confirm that you can unlock this space."),
                accent = accent
            ) {
                WizardField(
                    current,
                    { current = it; verified = false; error = null },
                    tr(zh, "当前解锁序列", "Current sequence"),
                    accent, "change_sequence_current", secret = true
                )
                WizardValidationHint(
                    good = VaultWizardRules.validSequence(current),
                    text = tr(zh, "4–16 个字符", "4–16 characters"), accent = accent
                )
            }
            1 -> WizardSection(
                icon = Icons.Default.Key,
                title = tr(zh, "设置新序列", "Choose a new sequence"),
                description = tr(zh, "新序列不能与当前序列相同。",
                    "Your new sequence must be different."),
                accent = accent
            ) {
                WizardField(
                    next, { next = it; error = null },
                    tr(zh, "新解锁序列", "New unlock sequence"),
                    accent, "change_sequence_new", secret = true
                )
                WizardField(
                    confirmation, { confirmation = it; error = null },
                    tr(zh, "再次输入新序列", "Repeat new sequence"),
                    accent, "change_sequence_confirmation", secret = true
                )
                WizardValidationHint(
                    good = VaultWizardRules.validSequence(next) && next.trim() != current.trim(),
                    text = tr(zh, "4–16 个字符，且不同于旧序列",
                        "4–16 characters, different from the current sequence"),
                    accent = accent
                )
                if (confirmation.isNotEmpty()) {
                    WizardValidationHint(
                        good = VaultWizardRules.confirmedSequence(next, confirmation),
                        text = tr(zh, "两次输入必须一致", "Both entries must match"),
                        accent = accent
                    )
                }
            }
            else -> WizardSection(
                icon = Icons.Default.CheckCircle,
                title = if (success) tr(zh, "密码已更新", "Sequence updated")
                        else tr(zh, "确认修改", "Confirm change"),
                description = if (success) tr(zh,
                    "已有加密文件保持不变，下一次请使用新序列。",
                    "Encrypted files are unchanged. Use the new sequence next time.")
                else tr(zh, "将只更新当前空间的解锁凭据。",
                    "Only this space's unlock credentials will change."),
                accent = if (success) VaultEmeraldSuccess else accent
            ) {
                WizardSummaryRow(tr(zh, "当前序列", "Current sequence"),
                    tr(zh, "已验证", "Verified"))
                WizardSummaryRow(tr(zh, "新序列", "New sequence"),
                    tr(zh, "已确认 · 内容隐藏", "Confirmed · hidden"))
                WizardSummaryRow(tr(zh, "已有文件", "Stored files"),
                    tr(zh, "无需重新加密", "No re-encryption"))
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun WizardFrame(
    title: String,
    subtitle: String,
    steps: List<String>,
    step: Int,
    accent: Color,
    processing: Boolean,
    completed: Boolean,
    enabled: Boolean,
    primaryLabel: String,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onPrimary: () -> Unit,
    error: String?,
    tag: String,
    body: @Composable (Int) -> Unit
) {
    val zh = LocalAppStrings.current.language == AppLanguage.ZH
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Dialog(
        onDismissRequest = { if (!processing) onBack() },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.let {
                WindowCompat.getInsetsController(it, it.decorView).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
        BackHandler(enabled = !processing) { onBack() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(VaultVoidBg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .testTag(tag)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    enabled = !processing,
                    onClick = { keyboard?.hide(); focusManager.clearFocus(); onBack() },
                    modifier = Modifier.testTag("wizard_back")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr(zh, "返回", "Back"),
                        tint = CalcTextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge,
                        color = CalcTextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = CalcTextSecondary, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
                IconButton(enabled = !processing, onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = tr(zh, "关闭", "Close"),
                        tint = CalcTextMuted)
                }
            }
            WizardProgressRow(steps, step.coerceAtMost(steps.lastIndex), accent)

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val direction = if (targetState >= initialState) 1 else -1
                    (slideInHorizontally(tween(250)) { direction * it / 6 } + fadeIn(tween(210)))
                        .togetherWith(
                            slideOutHorizontally(tween(150)) { -direction * it / 6 } +
                                fadeOut(tween(150))
                        )
                },
                modifier = Modifier.weight(1f),
                label = "vault_wizard_step"
            ) { currentStep ->
                Column(
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                        .testTag("wizard_step_$currentStep"),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    body(currentStep)
                }
            }
            Surface(color = VaultVoidBg, shadowElevation = 9.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnimatedVisibility(visible = error != null) {
                        Text(
                            text = error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = CalcClearKeyText,
                            modifier = Modifier.fillMaxWidth().testTag("wizard_error")
                        )
                    }
                    Button(
                        onClick = {
                            keyboard?.hide()
                            focusManager.clearFocus()
                            onPrimary()
                        },
                        enabled = enabled,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Color(0xFF211A17),
                            disabledContainerColor = VaultSurfaceElevated,
                            disabledContentColor = CalcTextMuted
                        ),
                        modifier = Modifier.fillMaxWidth()
                            .height(54.dp)
                            .testTag("wizard_primary")
                    ) {
                        if (processing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                                color = Color(0xFF211A17)
                            )
                        } else {
                            Text(primaryLabel, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.size(8.dp))
                            Icon(
                                if (step == steps.lastIndex) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null, modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (step > 0 && !processing && !completed) {
                        TextButton(
                            onClick = {
                                keyboard?.hide()
                                focusManager.clearFocus()
                                onBack()
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(tr(zh, "上一步", "Previous step"), color = CalcTextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardProgressRow(steps: List<String>, current: Int, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        steps.forEachIndexed { index, label ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (index <= current) accent else VaultSurfaceElevated,
                    border = if (index <= current) null else BorderStroke(1.dp, VaultBorderSubtle)
                ) {
                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        if (index < current) {
                            Icon(Icons.Default.Check, contentDescription = null,
                                tint = Color(0xFF211A17), modifier = Modifier.size(16.dp))
                        } else {
                            Text("${index + 1}", color = if (index == current) Color(0xFF211A17)
                                else CalcTextMuted, fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Text(
                    label, style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (index == current) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (index == current) CalcTextPrimary else CalcTextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun WizardSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    accent: Color,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Surface(
        color = VaultSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, VaultBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp)
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = accent.copy(alpha = .13f)) {
                Icon(icon, contentDescription = null, tint = accent,
                    modifier = Modifier.padding(12.dp).size(21.dp))
            }
            Text(title, color = CalcTextPrimary, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge)
            Text(description, color = CalcTextSecondary,
                style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}

@Composable
private fun WizardField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    accent: Color,
    tag: String,
    secret: Boolean = false
) {
    var reveal by remember { mutableStateOf(false) }
    val zh = LocalAppStrings.current.language == AppLanguage.ZH
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(64)) },
        label = { Text(label) },
        visualTransformation = if (secret && !reveal) PasswordVisualTransformation()
            else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        trailingIcon = if (secret) {
            {
                IconButton(onClick = { reveal = !reveal }) {
                    Icon(
                        if (reveal) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (reveal) tr(zh, "隐藏序列", "Hide sequence")
                            else tr(zh, "显示序列", "Show sequence")
                    )
                }
            }
        } else null,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = VaultBorderSubtle,
            cursorColor = accent
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag(tag)
    )
}

@Composable
private fun WizardValidationHint(good: Boolean, text: String, accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(
            if (good) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (good) VaultEmeraldSuccess else CalcTextMuted,
            modifier = Modifier.size(15.dp)
        )
        Text(text,
            color = if (good) CalcTextSecondary else CalcTextMuted,
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun WizardSwitchRow(
    title: String,
    subtitle: String,
    valueState: MutableState<Boolean>,
    accent: Color,
    tag: String
) {
    // Read the observable state in this child composition, not as a snapshot captured
    // by AnimatedContent when this step is first mounted.
    val value = valueState.value
    val onChange: (Boolean) -> Unit = { valueState.value = it }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f).clickable { onChange(!value) }
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = CalcTextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CalcTextSecondary)
        }
        Switch(
            checked = value, onCheckedChange = onChange,
            modifier = Modifier.testTag(tag),
            colors = SwitchDefaults.colors(
                checkedTrackColor = accent,
                checkedThumbColor = Color(0xFF211A17),
                uncheckedTrackColor = VaultSurfaceElevated,
                uncheckedThumbColor = CalcTextSecondary
            )
        )
    }
}

@Composable
private fun WizardSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = CalcTextSecondary, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f))
        Text(value, color = CalcTextPrimary, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.45f),
            textAlign = TextAlign.End, maxLines = 3)
    }
}
