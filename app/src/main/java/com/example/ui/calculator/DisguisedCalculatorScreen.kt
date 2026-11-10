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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CalcAccentCyan
import com.example.ui.theme.CalcAccentOrange
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
import com.example.ui.theme.VaultCyanPrimary
import com.example.ui.theme.VaultEmeraldSuccess
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceElevated
import com.example.viewmodel.XomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisguisedCalculatorScreen(
    calcState: XomeViewModel.CalculatorUiState,
    needsOnboarding: Boolean,
    isProvisioningOnboarding: Boolean,
    onboardingError: String?,
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
    onProvisionPrimarySpace: (pin: String, confirm: String, alias: String, stayInCalc: Boolean) -> Unit,
    onDismissOnboarding: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onImportSelfHealingArchive: (android.net.Uri) -> Unit
) {
    val archiveRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportSelfHealingArchive(uri)
        }
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
                .widthIn(max = 560.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Precision Hardware Status Header
            CalculatorHardwareHeader(
                isDegreeMode = calcState.isDegreeMode,
                isScientificExpanded = calcState.isScientificExpanded,
                isHistoryVisible = calcState.isHistoryVisible,
                isVerifyingBlindSequence = calcState.isVerifyingBlindSequence,
                totalSpacesCount = totalProvisionedSpaces,
                onToggleDegRad = onToggleDegRad,
                onToggleSciPad = onToggleSciPad,
                onToggleHistory = onToggleHistory,
                onOpenSetupIfEmpty = {
                    if (totalProvisionedSpaces == 0) {
                        onOpenOnboarding()
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Main Tactile LCD Readout Display
            CalculatorLcdDisplay(
                expression = calcState.expression,
                livePreview = calcState.livePreview,
                isDegreeMode = calcState.isDegreeMode,
                isVerifying = calcState.isVerifyingBlindSequence,
                statusBanner = statusBanner,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // History Drawer or Keypad
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
                        .height(210.dp)
                        .padding(bottom = 8.dp)
                )
            }

            // Expandable Scientific Function Row Matrix
            AnimatedVisibility(
                visible = calcState.isScientificExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                ScientificFunctionMatrix(
                    isDegreeMode = calcState.isDegreeMode,
                    onToggleDegRad = onToggleDegRad,
                    onInputToken = onInputToken,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

            // Primary 4x5 Tactile Arithmetic Keypad
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

        // First-Launch Cryptographic Onboarding Modal (Only shown when spaces/*.header is empty)
        if (needsOnboarding) {
            ModalBottomSheet(
                onDismissRequest = onDismissOnboarding,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = VaultSurface,
                scrimColor = Color.Black.copy(alpha = 0.78f),
                dragHandle = null
            ) {
                FirstLaunchVaultSetupSheet(
                    isProvisioning = isProvisioningOnboarding,
                    errorText = onboardingError,
                    onProvision = onProvisionPrimarySpace,
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
private fun CalculatorHardwareHeader(
    isDegreeMode: Boolean,
    isScientificExpanded: Boolean,
    isHistoryVisible: Boolean,
    isVerifyingBlindSequence: Boolean,
    totalSpacesCount: Int,
    onToggleDegRad: () -> Unit,
    onToggleSciPad: () -> Unit,
    onToggleHistory: () -> Unit,
    onOpenSetupIfEmpty: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clickable(enabled = totalSpacesCount == 0) { onOpenSetupIfEmpty() }
        ) {
            // Subtle LED indicator dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isVerifyingBlindSequence) CalcAccentOrange
                        else if (totalSpacesCount > 0) VaultEmeraldSuccess.copy(alpha = 0.75f)
                        else CalcTextMuted
                    )
            )
            Text(
                text = "XOME // FX-991EX",
                style = MaterialTheme.typography.labelLarge,
                color = CalcTextSecondary,
                fontWeight = FontWeight.Bold
            )
            if (totalSpacesCount == 0) {
                Surface(
                    color = CalcAccentOrange.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, CalcAccentOrange.copy(alpha = 0.45f)),
                    modifier = Modifier.testTag("setup_vault_badge")
                ) {
                    Text(
                        text = "INIT VAULT",
                        style = MaterialTheme.typography.labelSmall,
                        color = CalcAccentOrange,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                onClick = onToggleDegRad,
                color = CalcDisplayBg,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CalcDisplayBorder),
                modifier = Modifier.testTag("calc_deg_rad_toggle")
            ) {
                Text(
                    text = if (isDegreeMode) "DEG" else "RAD",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isDegreeMode) CalcAccentOrange else CalcAccentCyan,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            IconButton(
                onClick = onToggleSciPad,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isScientificExpanded) CalcOperatorKeyBg else CalcDisplayBg)
                    .testTag("calc_sci_toggle")
            ) {
                Icon(
                    imageVector = Icons.Default.Functions,
                    contentDescription = "Toggle scientific keypad",
                    tint = if (isScientificExpanded) CalcAccentCyan else CalcTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onToggleHistory,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isHistoryVisible) CalcOperatorKeyBg else CalcDisplayBg)
                    .testTag("calc_history_toggle")
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Calculation history",
                    tint = if (isHistoryVisible) CalcAccentOrange else CalcTextSecondary,
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
    isVerifying: Boolean,
    statusBanner: XomeViewModel.StatusBanner?,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("calculator_display"),
        shape = RoundedCornerShape(22.dp),
        color = CalcDisplayBg,
        border = BorderStroke(1.5.dp, CalcDisplayBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF141A26),
                            Color(0xFF0E121A)
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Top micro telemetry line inside LCD
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isDegreeMode) "TRIG: DEG" else "TRIG: RAD",
                    style = MaterialTheme.typography.labelSmall,
                    color = CalcTextMuted
                )
                Text(
                    text = if (isVerifying) "MATH ENGINE // BUSY" else "FLOAT-64 // READY",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isVerifying) CalcAccentOrange else CalcTextMuted
                )
            }

            if (statusBanner != null) {
                Surface(
                    color = VaultEmeraldSuccess.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 22.dp)
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

            // Main Expression & Live Preview aligned bottom-end
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
                        fontWeight = FontWeight.Bold,
                        fontSize = when {
                            expression.length > 16 -> 28.sp
                            expression.length > 11 -> 36.sp
                            else -> 46.sp
                        },
                        color = CalcTextPrimary,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.testTag("calc_expression_text")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (livePreview.isNotEmpty()) "= $livePreview" else " ",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp,
                    color = if (livePreview == "Syntax Error") CalcClearKeyText else CalcAccentCyan.copy(alpha = 0.85f),
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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = CalcDisplayBg,
        border = BorderStroke(1.dp, CalcDisplayBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CALCULATION TAPE",
                    style = MaterialTheme.typography.labelLarge,
                    color = CalcTextSecondary
                )
                if (history.isNotEmpty()) {
                    TextButton(
                        onClick = onClearHistory,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("CLEAR", style = MaterialTheme.typography.labelSmall, color = CalcClearKeyText)
                    }
                }
            }
            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No recent expressions evaluated",
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
                                style = MaterialTheme.typography.labelLarge,
                                color = CalcTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "= ${item.result}",
                                style = MaterialTheme.typography.labelLarge,
                                color = CalcAccentOrange,
                                fontWeight = FontWeight.Bold
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
    isDegreeMode: Boolean,
    onToggleDegRad: () -> Unit,
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
        shape = RoundedCornerShape(12.dp),
        color = CalcSciKeyBg,
        border = BorderStroke(1.dp, CalcDisplayBorder),
        modifier = modifier.height(42.dp)
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: AC, Backspace, %, ÷
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            TactileCalcKey(
                label = "%",
                containerColor = CalcOperatorKeyBg,
                contentColor = CalcAccentCyan,
                onClick = { onInputToken("%") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("calc_key_percent")
            )
            TactileCalcKey(
                label = "÷",
                containerColor = CalcOperatorKeyBg,
                contentColor = CalcAccentOrange,
                onClick = { onInputToken("÷") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("calc_key_divide")
            )
        }

        // Row 2: 7, 8, 9, ×
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
                contentColor = CalcAccentOrange,
                onClick = { onInputToken("×") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("calc_key_multiply")
            )
        }

        // Row 3: 4, 5, 6, −
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
                contentColor = CalcAccentOrange,
                onClick = { onInputToken("−") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("calc_key_minus")
            )
        }

        // Row 4: 1, 2, 3, +
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
                contentColor = CalcAccentOrange,
                onClick = { onInputToken("+") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("calc_key_plus")
            )
        }

        // Row 5: 00, 0, ., =
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
            // The Covert Trigger & Equals Button
            Surface(
                onClick = onEquals,
                shape = RoundedCornerShape(16.dp),
                color = CalcAccentOrange,
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
                        color = Color(0xFF140700)
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
        shape = RoundedCornerShape(16.dp),
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
private fun FirstLaunchVaultSetupSheet(
    isProvisioning: Boolean,
    errorText: String?,
    onProvision: (pin: String, confirm: String, alias: String, stayInCalc: Boolean) -> Unit,
    onSkipToCalculator: () -> Unit,
    onRestoreArchive: () -> Unit
) {
    var sequence by rememberSaveable { mutableStateOf("1314") }
    var confirmSequence by rememberSaveable { mutableStateOf("1314") }
    var spaceAlias by rememberSaveable { mutableStateOf("Primary Sovereign Vault") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Banner Illustration
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, VaultBorderSubtle, RoundedCornerShape(18.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_vault_banner_1794279611736),
                contentDescription = "Cryptographic Envelope Vault Banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, VaultSurface.copy(alpha = 0.92f))
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
                    tint = VaultCyanPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "ARGON2ID + AES-256-GCM ENVELOPE VAULT",
                    style = MaterialTheme.typography.labelLarge,
                    color = VaultCyanPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = "Initialize Primary Vault Space",
            style = MaterialTheme.typography.headlineMedium,
            color = CalcTextPrimary
        )

        Text(
            text = "No existing .header files were detected in .sys_vault_data/spaces/. Configure your secret calculator trigger sequence below. Typing this sequence and pressing '=' in the calculator unlocks your encrypted space.",
            style = MaterialTheme.typography.bodyMedium,
            color = CalcTextSecondary
        )

        // Quick preset chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Presets:",
                style = MaterialTheme.typography.labelSmall,
                color = CalcTextMuted
            )
            listOf("1314", "9988+11", "2048*2").forEach { preset ->
                FilterChip(
                    selected = sequence == preset && confirmSequence == preset,
                    onClick = {
                        sequence = preset
                        confirmSequence = preset
                    },
                    label = {
                        Text(preset, style = MaterialTheme.typography.labelSmall)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VaultCyanPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = VaultCyanPrimary
                    )
                )
            }
        }

        OutlinedTextField(
            value = spaceAlias,
            onValueChange = { spaceAlias = it },
            label = { Text("Space Designation") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_alias_input"),
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
                value = sequence,
                onValueChange = { sequence = it },
                label = { Text("Trigger Sequence (4–16 chars)") },
                leadingIcon = {
                    Icon(Icons.Default.Key, contentDescription = null, tint = CalcAccentOrange)
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("onboarding_pin_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CalcAccentOrange,
                    unfocusedBorderColor = VaultBorderSubtle
                )
            )

            OutlinedTextField(
                value = confirmSequence,
                onValueChange = { confirmSequence = it },
                label = { Text("Confirm Sequence") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = VaultCyanPrimary)
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("onboarding_confirm_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VaultCyanPrimary,
                    unfocusedBorderColor = VaultBorderSubtle
                )
            )
        }

        // Binary spec summary card
        Surface(
            color = VaultSurfaceElevated,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, VaultBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = VaultEmeraldSuccess,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "BINARY ENVELOPE SPECIFICATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = VaultEmeraldSuccess,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "• Header: 128 Bytes (Magic CLCVLT01 + 16B Salt + Argon2id + 48B Wrapped_MK)\n• Database: SQLCipher 256-bit AES (db/<hex>.db)\n• File Blobs: 76B VBLK Header + 64KB Streaming AES-256-GCM Chunks",
                    style = MaterialTheme.typography.labelSmall,
                    color = CalcTextSecondary
                )
            }
        }

        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodyMedium,
                color = CalcClearKeyText
            )
        }

        Button(
            onClick = { onProvision(sequence, confirmSequence, spaceAlias, false) },
            enabled = !isProvisioning,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VaultCyanPrimary,
                contentColor = Color(0xFF001F26)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("onboarding_provision_button")
        ) {
            if (isProvisioning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF001F26)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Deriving Argon2id KEK & Sealing 128B Header...",
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "Provision Primary Space & Enter Vault",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onRestoreArchive,
                enabled = !isProvisioning,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, VaultBorderSubtle),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("onboarding_restore_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = null,
                    tint = VaultCyanPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Self-Heal Restore", color = CalcTextPrimary, fontSize = 12.sp)
            }

            TextButton(
                onClick = onSkipToCalculator,
                enabled = !isProvisioning,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("onboarding_skip_button")
            ) {
                Text("Use Disguised Calc Only", color = CalcTextSecondary, fontSize = 12.sp)
            }
        }
    }
}
