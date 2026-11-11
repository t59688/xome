package com.example.ui.calculator

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SecurityUpdateGood
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CalcAccentMuted
import com.example.ui.theme.CalcAccentCopper
import com.example.ui.theme.CalcChassisBg
import com.example.ui.theme.CalcClearKeyBg
import com.example.ui.theme.CalcClearKeyText
import com.example.ui.theme.CalcDisplayBg
import com.example.ui.theme.CalcDisplayBorder
import com.example.ui.theme.CalcNumberKeyBg
import com.example.ui.theme.CalcOperatorKeyBg
import com.example.ui.theme.CalcSciKeyBg
import com.example.ui.theme.CalcTextMuted
import com.example.ui.theme.CalcTextPrimary
import com.example.ui.theme.CalcTextSecondary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VaultBorderSubtle
import com.example.ui.theme.VaultAccentPrimary
import com.example.ui.theme.VaultEmeraldSuccess
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceElevated
import com.example.ui.graph.GraphStudioScreen
import com.example.ui.vault.KeyfilePickerCard
import com.example.ui.vault.VaultWizardRules
import com.example.viewmodel.XomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisguisedCalculatorScreen(
    calcState: XomeViewModel.CalculatorUiState,
    needsOnboarding: Boolean,
    showSetupDialog: Boolean,
    isProvisioningOnboarding: Boolean,
    onboardingError: String?,
    isRecoveringSpace: Boolean = false,
    recoveryError: String? = null,
    unsealedRecoverableSpaces: Int = 0,
    totalProvisionedSpaces: Int,
    statusBanner: XomeViewModel.StatusBanner?,
    onInputToken: (String) -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onEquals: () -> Unit,
    onToggleDegRad: () -> Unit,
    onToggleSciPad: () -> Unit,
    onToggleHistory: () -> Unit,
    onClearHistory: () -> Unit,
    onToggleLanguage: () -> Unit,
    onProvisionPrimarySpace: (pin: String, confirm: String, keyfilePepper: ByteArray?, alias: String, stayInCalc: Boolean) -> Unit,
    onExtractKeyfileDigest: suspend (android.net.Uri) -> Result<XomeViewModel.KeyfileSelection> = {
        Result.failure(IllegalStateException("Not configured"))
    },
    onRecoverSpaceWithKeyfile: (pin: String, keyfilePepper: ByteArray?) -> Unit = { _, _ -> },
    onDismissOnboarding: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onImportSelfHealingArchive: (android.net.Uri) -> Unit,
    updateAvailable: Boolean = false,
    onOpenUpdates: () -> Unit = {}
) {
    val strings = LocalAppStrings.current
    var graphStudioOpen by rememberSaveable { mutableStateOf(false) }
    val archiveRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportSelfHealingArchive(uri)
        }
    }

    if (graphStudioOpen) {
        GraphStudioScreen(onClose = { graphStudioOpen = false })
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CalcChassisBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("calculator_root"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 540.dp)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            CalculatorTopToolbar(
                isDegreeMode = calcState.isDegreeMode,
                isScientificExpanded = calcState.isScientificExpanded,
                isHistoryVisible = calcState.isHistoryVisible,
                totalSpacesCount = totalProvisionedSpaces,
                onToggleDegRad = onToggleDegRad,
                onOpenGraph = { graphStudioOpen = true },
                onToggleSciPad = onToggleSciPad,
                onToggleHistory = onToggleHistory,
                onToggleLanguage = onToggleLanguage,
                onOpenSetupIfEmpty = onOpenOnboarding,
                updateAvailable = updateAvailable,
                onOpenUpdates = onOpenUpdates
            )

            AnimatedVisibility(visible = needsOnboarding) {
                Surface(
                    color = VaultSurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, VaultBorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .testTag("first_launch_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.firstLaunchBannerTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 14.sp,
                                color = CalcTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = strings.firstLaunchBannerSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = CalcTextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        IconButton(
                            onClick = onOpenOnboarding,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(VaultAccentPrimary.copy(alpha = 0.16f))
                                .testTag("open_custom_setup_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = strings.configureBtn,
                                tint = VaultAccentPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            CalculatorLcdDisplay(
                expression = calcState.expression,
                livePreview = calcState.livePreview,
                isDegreeMode = calcState.isDegreeMode,
                statusBanner = statusBanner,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
                    .heightIn(max = 218.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            AnimatedVisibility(
                visible = calcState.isHistoryVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                CalculatorHistoryPanel(
                    history = calcState.history,
                    onSelectHistoryExpression = { expr ->
                        onClear()
                        onInputToken(expr)
                        onToggleHistory()
                    },
                    onClearHistory = onClearHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .padding(bottom = 8.dp)
                )
            }

            AnimatedVisibility(
                visible = calcState.isScientificExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                ScientificFunctionMatrix(
                    onInputToken = onInputToken,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

            PrimaryArithmeticKeypad(
                onInputToken = onInputToken,
                onClear = onClear,
                onBackspace = onBackspace,
                onEquals = onEquals,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            )
        }

        if (showSetupDialog) {
            ModalBottomSheet(
                onDismissRequest = onDismissOnboarding,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = VaultSurface,
                scrimColor = Color.Black.copy(alpha = 0.65f),
                dragHandle = null
            ) {
                FirstLaunchVaultSetupSheet(
                    isProvisioning = isProvisioningOnboarding,
                    errorText = onboardingError,
                    isRecovering = isRecoveringSpace,
                    recoveryError = recoveryError,
                    unsealedRecoverableSpaces = unsealedRecoverableSpaces,
                    onProvision = onProvisionPrimarySpace,
                    onExtractKeyfileDigest = onExtractKeyfileDigest,
                    onRecoverSpace = onRecoverSpaceWithKeyfile,
                    onSkipToCalculator = onDismissOnboarding,
                    onRestoreArchive = {
                        archiveRestoreLauncher.launch(arrayOf("*/*"))
                    }
                )
            }
        }
    }
}

@Composable
private fun CalculatorTopToolbar(
    isDegreeMode: Boolean,
    isScientificExpanded: Boolean,
    isHistoryVisible: Boolean,
    totalSpacesCount: Int,
    onToggleDegRad: () -> Unit,
    onOpenGraph: () -> Unit,
    onToggleSciPad: () -> Unit,
    onToggleHistory: () -> Unit,
    onToggleLanguage: () -> Unit,
    onOpenSetupIfEmpty: () -> Unit,
    updateAvailable: Boolean,
    onOpenUpdates: () -> Unit
) {
    val strings = LocalAppStrings.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = onToggleDegRad,
                color = CalcDisplayBg,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CalcDisplayBorder),
                modifier = Modifier.testTag("calc_deg_rad_toggle")
            ) {
                Text(
                    text = if (isDegreeMode) "DEG" else "RAD",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = JetBrainsMonoFamily,
                    color = if (isDegreeMode) CalcAccentCopper else CalcAccentMuted,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }

            if (totalSpacesCount == 0) {
                IconButton(
                    onClick = onOpenSetupIfEmpty,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CalcDisplayBg)
                        .testTag("setup_vault_badge")
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = strings.initVaultBadge,
                        tint = CalcAccentCopper,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onOpenGraph,
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                    .background(CalcOperatorKeyBg).testTag("calc_graph_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = if (strings.language == com.example.ui.i18n.AppLanguage.ZH)
                        "图形工作台" else "Graph studio",
                    tint = CalcAccentCopper, modifier = Modifier.size(19.dp))
            }
            IconButton(
                onClick = onOpenUpdates,
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                    .background(CalcDisplayBg).testTag("calc_updates_button")
            ) {
                BadgedBox(badge = {
                    if (updateAvailable) Badge(Modifier.testTag("app_update_badge"))
                }) {
                    Icon(
                        imageVector = Icons.Default.SecurityUpdateGood,
                        contentDescription = if (strings.language == com.example.ui.i18n.AppLanguage.ZH)
                            "应用更新" else "App updates",
                        tint = if (updateAvailable) VaultAccentPrimary else CalcTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            IconButton(
                onClick = onToggleLanguage,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CalcDisplayBg)
                    .testTag("calc_lang_toggle")
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = "Switch Language",
                    tint = CalcTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onToggleSciPad,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isScientificExpanded) CalcOperatorKeyBg else CalcDisplayBg)
                    .testTag("calc_sci_toggle")
            ) {
                Icon(
                    imageVector = Icons.Default.Functions,
                    contentDescription = "Scientific keypad",
                    tint = if (isScientificExpanded) CalcAccentMuted else CalcTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onToggleHistory,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isHistoryVisible) CalcOperatorKeyBg else CalcDisplayBg)
                    .testTag("calc_history_toggle")
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "History",
                    tint = if (isHistoryVisible) CalcAccentCopper else CalcTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun CalculatorLcdDisplay(
    expression: String,
    livePreview: String,
    isDegreeMode: Boolean,
    statusBanner: XomeViewModel.StatusBanner?,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("calculator_display"),
        shape = RoundedCornerShape(20.dp),
        color = CalcDisplayBg,
        border = BorderStroke(1.dp, CalcDisplayBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            if (statusBanner != null) {
                Surface(
                    color = VaultEmeraldSuccess.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = statusBanner.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = VaultEmeraldSuccess,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomEnd),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState, reverseScrolling = true),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = expression.ifEmpty { "0" },
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = when {
                            expression.length > 16 -> 28.sp
                            expression.length > 11 -> 36.sp
                            else -> 48.sp
                        },
                        color = CalcTextPrimary,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.testTag("calc_expression_text")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                val localizedPreview = if (livePreview == "Syntax Error") strings.syntaxError else livePreview
                Text(
                    text = if (localizedPreview.isNotEmpty()) "= $localizedPreview" else " ",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    color = if (livePreview == "Syntax Error") CalcClearKeyText else CalcTextSecondary,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("calc_preview_text")
                )
            }
        }
    }
}

@Composable
private fun CalculatorHistoryPanel(
    history: List<com.example.calculator.ScientificCalculatorEngine.CalcHistoryEntry>,
    onSelectHistoryExpression: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = CalcDisplayBg,
        border = BorderStroke(1.dp, CalcDisplayBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.calculationTapeTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = CalcTextSecondary
                )
                if (history.isNotEmpty()) {
                    TextButton(
                        onClick = onClearHistory,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(strings.clearBtn, style = MaterialTheme.typography.labelSmall, color = CalcClearKeyText)
                    }
                }
            }
            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = strings.noRecentCalculations,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CalcTextMuted
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(history, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectHistoryExpression(item.result) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.expression,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 14.sp,
                                color = CalcTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "= ${item.result}",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 14.sp,
                                color = CalcAccentCopper,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScientificFunctionMatrix(
    onInputToken: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val row1 = listOf(
        "sin" to "sin(",
        "cos" to "cos(",
        "tan" to "tan(",
        "ln" to "ln(",
        "log" to "log(",
        "√" to "√("
    )
    val row2 = listOf(
        "(" to "(",
        ")" to ")",
        "xʸ" to "^",
        "x!" to "!",
        "π" to "π",
        "e" to "e"
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            row1.forEach { (label, token) ->
                ScientificChipButton(
                    label = label,
                    onClick = { onInputToken(token) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sci_key_$label")
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            row2.forEach { (label, token) ->
                ScientificChipButton(
                    label = label,
                    onClick = { onInputToken(token) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sci_key_$label")
                )
            }
        }
    }
}

@Composable
private fun ScientificChipButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = CalcSciKeyBg,
        border = BorderStroke(1.dp, CalcDisplayBorder),
        modifier = modifier.height(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = CalcTextSecondary
            )
        }
    }
}

@Composable
private fun PrimaryArithmeticKeypad(
    onInputToken: (String) -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onEquals: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            TactileCalcKey(
                label = "AC",
                containerColor = CalcClearKeyBg,
                contentColor = CalcClearKeyText,
                onClick = onClear,
                modifier = Modifier
                    .weight(1f)
                    .testTag("calc_key_ac")
            )
            Surface(
                onClick = onBackspace,
                shape = RoundedCornerShape(16.dp),
                color = CalcOperatorKeyBg,
                border = BorderStroke(1.dp, CalcDisplayBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .testTag("calc_key_backspace")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = CalcTextPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
            TactileCalcKey(
                label = "%",
                containerColor = CalcOperatorKeyBg,
                contentColor = CalcAccentMuted,
                onClick = { onInputToken("%") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("calc_key_percent")
            )
            TactileCalcKey(
                label = "÷",
                containerColor = CalcOperatorKeyBg,
                contentColor = CalcAccentCopper,
                onClick = { onInputToken("÷") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("calc_key_divide")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("7", "8", "9").forEach { digit ->
                TactileCalcKey(
                    label = digit,
                    containerColor = CalcNumberKeyBg,
                    contentColor = CalcTextPrimary,
                    onClick = { onInputToken(digit) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calc_key_$digit")
                )
            }
            TactileCalcKey(
                label = "×",
                containerColor = CalcOperatorKeyBg,
                contentColor = CalcAccentCopper,
                onClick = { onInputToken("×") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("calc_key_multiply")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("4", "5", "6").forEach { digit ->
                TactileCalcKey(
                    label = digit,
                    containerColor = CalcNumberKeyBg,
                    contentColor = CalcTextPrimary,
                    onClick = { onInputToken(digit) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calc_key_$digit")
                )
            }
            TactileCalcKey(
                label = "−",
                containerColor = CalcOperatorKeyBg,
                contentColor = CalcAccentCopper,
                onClick = { onInputToken("−") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("calc_key_minus")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("1", "2", "3").forEach { digit ->
                TactileCalcKey(
                    label = digit,
                    containerColor = CalcNumberKeyBg,
                    contentColor = CalcTextPrimary,
                    onClick = { onInputToken(digit) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calc_key_$digit")
                )
            }
            TactileCalcKey(
                label = "+",
                containerColor = CalcOperatorKeyBg,
                contentColor = CalcAccentCopper,
                onClick = { onInputToken("+") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("calc_key_plus")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TactileCalcKey(
                label = "00",
                containerColor = CalcNumberKeyBg,
                contentColor = CalcTextSecondary,
                onClick = { onInputToken("00") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("calc_key_00")
            )
            TactileCalcKey(
                label = "0",
                containerColor = CalcNumberKeyBg,
                contentColor = CalcTextPrimary,
                onClick = { onInputToken("0") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("calc_key_0")
            )
            TactileCalcKey(
                label = ".",
                containerColor = CalcNumberKeyBg,
                contentColor = CalcTextPrimary,
                onClick = { onInputToken(".") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("calc_key_dot")
            )
            Surface(
                onClick = onEquals,
                shape = RoundedCornerShape(14.dp),
                color = CalcAccentCopper,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .testTag("calc_key_equals")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "=",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF24180F)
                    )
                }
            }
        }
    }
}

@Composable
private fun TactileCalcKey(
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, CalcDisplayBorder),
        modifier = modifier.height(60.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

@Composable
internal fun FirstLaunchVaultSetupSheet(
    isProvisioning: Boolean,
    errorText: String?,
    isRecovering: Boolean = false,
    recoveryError: String? = null,
    unsealedRecoverableSpaces: Int = 0,
    onProvision: (pin: String, confirm: String, keyfilePepper: ByteArray?, alias: String, stayInCalc: Boolean) -> Unit,
    onExtractKeyfileDigest: suspend (android.net.Uri) -> Result<XomeViewModel.KeyfileSelection> = {
        Result.failure(IllegalStateException("Not configured"))
    },
    onRecoverSpace: (pin: String, keyfilePepper: ByteArray?) -> Unit = { _, _ -> },
    onSkipToCalculator: () -> Unit,
    onRestoreArchive: () -> Unit,
    initialKeyfile: XomeViewModel.KeyfileSelection? = null
) {
    val strings = LocalAppStrings.current
    val zh = strings.language == AppLanguage.ZH
    val scope = rememberCoroutineScope()

    var activeTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Create, 1 = Recover
    var sequence by remember { mutableStateOf("") }
    var confirmSequence by remember { mutableStateOf("") }
    var recoverSequence by remember { mutableStateOf("") }
    var spaceAlias by rememberSaveable { mutableStateOf(strings.defaultPrimarySpaceAlias) }

    var createKeyfile by remember { mutableStateOf(initialKeyfile) }
    var recoverKeyfile by remember { mutableStateOf(initialKeyfile) }
    var isHashingCreateKeyfile by remember { mutableStateOf(false) }
    var isHashingRecoverKeyfile by remember { mutableStateOf(false) }
    var keyfilePickError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            createKeyfile?.wipe()
            recoverKeyfile?.wipe()
        }
    }

    val createKeyfileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isHashingCreateKeyfile = true
            keyfilePickError = null
            scope.launch {
                val result = onExtractKeyfileDigest(uri)
                isHashingCreateKeyfile = false
                result.onSuccess { selection ->
                    createKeyfile?.wipe()
                    createKeyfile = selection
                }.onFailure { err ->
                    keyfilePickError = err.message
                }
            }
        }
    }

    val recoverKeyfileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isHashingRecoverKeyfile = true
            keyfilePickError = null
            scope.launch {
                val result = onExtractKeyfileDigest(uri)
                isHashingRecoverKeyfile = false
                result.onSuccess { selection ->
                    recoverKeyfile?.wipe()
                    recoverKeyfile = selection
                }.onFailure { err ->
                    keyfilePickError = err.message
                }
            }
        }
    }

    LaunchedEffect(strings.language) {
        if (spaceAlias in listOf("Main Space", "主空间", "Primary Sovereign Vault", "主加密私密空间")) {
            spaceAlias = strings.defaultPrimarySpaceAlias
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, VaultBorderSubtle, RoundedCornerShape(18.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_vault_banner_1794279611736),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, VaultSurface.copy(alpha = 0.94f))
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = VaultAccentPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = strings.onboardingTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = CalcTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Segmented mode toggle: Create New Space vs. Recover Existing Space
        Surface(
            color = VaultSurfaceElevated,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, VaultBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val tabs = listOf(
                    0 to (if (zh) "创建新空间" else "Create Space"),
                    1 to (if (zh) "恢复已有空间" else "Recover Space")
                )
                tabs.forEach { (tabIdx, label) ->
                    val selected = activeTab == tabIdx
                    Surface(
                        onClick = {
                            activeTab = tabIdx
                            keyfilePickError = null
                        },
                        shape = RoundedCornerShape(9.dp),
                        color = if (selected) VaultAccentPrimary else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(if (tabIdx == 0) "onboarding_tab_create" else "onboarding_tab_recover")
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) Color(0xFF06202B) else CalcTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (activeTab == 0) {
            Text(
                text = strings.onboardingSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = CalcTextSecondary
            )

            // Keep all onboarding inputs on the same 56dp baseline. On compact phones,
            // display passwords one per row rather than shrinking labels into two lines.
            OutlinedTextField(
                value = spaceAlias,
                onValueChange = { spaceAlias = it.take(36) },
                label = {
                    Text(
                        strings.spaceDesignationLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("onboarding_alias_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VaultAccentPrimary,
                    unfocusedBorderColor = VaultBorderSubtle
                )
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val stackFields = maxWidth < 320.dp || LocalDensity.current.fontScale >= 1.3f
                if (stackFields) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OnboardingSequenceInput(
                            sequence = sequence,
                            onSequenceChange = { sequence = it },
                            label = strings.triggerSequenceLabel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_pin_input")
                        )
                        OnboardingSequenceInput(
                            sequence = confirmSequence,
                            onSequenceChange = { confirmSequence = it },
                            label = strings.confirmSequenceLabel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_confirm_input")
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OnboardingSequenceInput(
                            sequence = sequence,
                            onSequenceChange = { sequence = it },
                            label = strings.triggerSequenceLabel,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("onboarding_pin_input")
                        )
                        OnboardingSequenceInput(
                            sequence = confirmSequence,
                            onSequenceChange = { confirmSequence = it },
                            label = strings.confirmSequenceLabel,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("onboarding_confirm_input")
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OnboardingHintRow(
                    good = VaultWizardRules.validSequence(sequence),
                    text = if (zh) "6–64 位，同时包含数字与计算器符号（如 +-*/().）"
                    else "6–64 chars combining digits and calculator symbols"
                )
                if (confirmSequence.isNotEmpty()) {
                    OnboardingHintRow(
                        good = VaultWizardRules.confirmedSequence(sequence, confirmSequence),
                        text = if (zh) "两次输入的序列一致" else "Both entries match"
                    )
                }
            }

            KeyfilePickerCard(
                selectedKeyfile = createKeyfile,
                isHashing = isHashingCreateKeyfile,
                errorText = keyfilePickError,
                accent = VaultAccentPrimary,
                title = if (zh) "绑定实体钥匙文件（Keyfile）" else "Bind Original Keyfile",
                subtitle = if (zh) "点击选择任意非空原文件（防离线爆破必选）" else "Tap to choose any non-empty original file",
                tipText = if (zh) "建议选 PDF / 压缩包 / 安装包 / 原图（避免微信等默认压缩）。本机用安全芯片封印，日常免重选。"
                else "Prefer PDF / ZIP / APK / lossless audio or original unedited files. Sealed locally after setup.",
                onRequestPickFile = { createKeyfileLauncher.launch(arrayOf("*/*")) },
                tagPrefix = "onboarding_keyfile"
            )

            if (errorText != null) {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = CalcClearKeyText,
                    modifier = Modifier.testTag("onboarding_error_text")
                )
            }

            if (isProvisioning) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = VaultAccentPrimary,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = strings.derivingKekProgress,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VaultAccentPrimary
                    )
                }
            } else {
                Button(
                    onClick = {
                        onProvision(sequence, confirmSequence, createKeyfile?.pepper, spaceAlias, false)
                    },
                    enabled = !isHashingCreateKeyfile,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VaultAccentPrimary,
                        contentColor = Color(0xFF06202B)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("onboarding_provision_button")
                ) {
                    Text(
                        text = strings.provisionPrimarySpaceBtn,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onSkipToCalculator,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, VaultBorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("onboarding_skip_button")
                ) {
                    Text(
                        text = strings.useDisguisedCalcOnlyBtn,
                        style = MaterialTheme.typography.labelLarge,
                        color = CalcTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            // Recover existing space tab (after uninstall/reinstall or migrating backup archive)
            Surface(
                color = if (unsealedRecoverableSpaces > 0) VaultEmeraldSuccess.copy(alpha = 0.12f)
                else VaultSurfaceElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (unsealedRecoverableSpaces > 0) VaultEmeraldSuccess.copy(alpha = 0.45f)
                    else VaultBorderSubtle
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (unsealedRecoverableSpaces > 0) {
                        if (zh) {
                            "检测到本机存有 $unsealedRecoverableSpaces 个待绑定空间容器。选择创建时绑定的原文件并输入原序列即可恢复。"
                        } else {
                            "Found $unsealedRecoverableSpaces unsealed space container(s) on device. Bind your original Keyfile + sequence to restore."
                        }
                    } else {
                        if (zh) {
                            "如果从旧手机迁移，可先导入 .zip 备份包，再绑定原文件（Keyfile）与解锁序列。"
                        } else {
                            "Migrating from another device? Import your .zip backup first, then verify your original Keyfile + sequence."
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unsealedRecoverableSpaces > 0) VaultEmeraldSuccess else CalcTextSecondary,
                    modifier = Modifier.padding(12.dp)
                )
            }

            OutlinedButton(
                onClick = onRestoreArchive,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, VaultAccentPrimary.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("onboarding_restore_archive_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Archive,
                    contentDescription = null,
                    tint = VaultAccentPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.selfHealRestoreBtn,
                    style = MaterialTheme.typography.labelLarge,
                    color = VaultAccentPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            KeyfilePickerCard(
                selectedKeyfile = recoverKeyfile,
                isHashing = isHashingRecoverKeyfile,
                errorText = keyfilePickError,
                accent = VaultEmeraldSuccess,
                title = if (zh) "选择原钥匙文件（Keyfile）" else "Select Original Keyfile",
                subtitle = if (zh) "必须与创建该空间时选择的原文件完全一致" else "Must match the exact unmodified original file",
                tipText = if (zh) "验证成功后，本机将自动用安全芯片封印，此后日常仅需输算式解锁。"
                else "Once verified, the Keyfile digest is sealed locally in Keystore for sequence-only daily unlock.",
                onRequestPickFile = { recoverKeyfileLauncher.launch(arrayOf("*/*")) },
                tagPrefix = "recover_keyfile"
            )

            OnboardingSequenceInput(
                sequence = recoverSequence,
                onSequenceChange = { recoverSequence = it },
                label = if (zh) "原空间解锁序列" else "Original unlock sequence",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_recover_pin_input")
            )

            if (recoveryError != null) {
                Text(
                    text = recoveryError,
                    style = MaterialTheme.typography.bodySmall,
                    color = CalcClearKeyText,
                    modifier = Modifier.testTag("onboarding_recovery_error")
                )
            }

            if (isRecovering) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = VaultEmeraldSuccess,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (zh) "正在验证隐形槽位并封印本机..." else "Verifying stealth slots & sealing...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VaultEmeraldSuccess
                    )
                }
            } else {
                Button(
                    onClick = { onRecoverSpace(recoverSequence, recoverKeyfile?.pepper) },
                    enabled = !isHashingRecoverKeyfile,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VaultEmeraldSuccess,
                        contentColor = Color(0xFF07231A)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("onboarding_recover_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (zh) "验证并绑定到本机" else "Verify & Bind to Device",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onSkipToCalculator,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, VaultBorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("onboarding_skip_button")
                ) {
                    Text(
                        text = strings.useDisguisedCalcOnlyBtn,
                        style = MaterialTheme.typography.labelLarge,
                        color = CalcTextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingHintRow(good: Boolean, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = if (good) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (good) VaultEmeraldSuccess else CalcTextMuted,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (good) CalcTextSecondary else CalcTextMuted
        )
    }
}

/** The two credential inputs share exactly the same field geometry and semantics. */
@Composable
private fun OnboardingSequenceInput(
    sequence: String,
    onSequenceChange: (String) -> Unit,
    label: String,
    modifier: Modifier
) {
    OutlinedTextField(
        value = sequence,
        onValueChange = { onSequenceChange(it.take(64)) },
        visualTransformation = PasswordVisualTransformation(),
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(56.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CalcAccentCopper,
            unfocusedBorderColor = VaultBorderSubtle
        )
    )
}
