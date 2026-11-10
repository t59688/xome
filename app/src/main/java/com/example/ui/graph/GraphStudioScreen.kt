package com.example.ui.graph

import android.content.Context
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.calculator.graph.GraphDraft
import com.example.calculator.graph.GraphExpression
import com.example.calculator.graph.GraphMode
import com.example.calculator.graph.GraphPreset
import com.example.calculator.graph.GraphPresets
import com.example.calculator.graph.GraphViewport
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CalcAccentCopper
import com.example.ui.theme.CalcChassisBg
import com.example.ui.theme.CalcDisplayBg
import com.example.ui.theme.CalcDisplayBorder
import com.example.ui.theme.CalcTextMuted
import com.example.ui.theme.CalcTextPrimary
import com.example.ui.theme.CalcTextSecondary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VaultBorderSubtle
import com.example.ui.theme.VaultSurfaceElevated
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.*

private val mint = Color(0xFF69D2AC)
private val lilac = Color(0xFFBCA7E8)
private val cyanGlow = Color(0xFF4EEBF2)
private val plotColors = listOf(CalcAccentCopper, mint, lilac)
private val slate = Color(0xFF526066)
private val grid = Color(0xFF283238)

private fun displayNumber(n: Double): String =
    if (!n.isFinite()) "—" else "%.2f".format(Locale.US, n)

@Composable
fun GraphStudioScreen(onClose: () -> Unit) {
    val zh = LocalAppStrings.current.language == AppLanguage.ZH
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("xome_graph_studio", Context.MODE_PRIVATE) }

    val initialModeName = prefs.getString("active_mode", GraphMode.CARTESIAN.name) ?: GraphMode.CARTESIAN.name
    val initialMode = runCatching { GraphMode.valueOf(initialModeName) }.getOrDefault(GraphMode.CARTESIAN)

    var currentCategory by remember { mutableStateOf(initialMode.category) }
    var mode by remember { mutableStateOf(initialMode) }

    val drafts = remember {
        mutableStateMapOf<GraphMode, GraphDraft>().apply {
            GraphMode.entries.forEach { m ->
                val fallback = GraphPresets.first(m).expressions
                put(m, GraphDraft(
                    prefs.getString(m.name + "_0", fallback.first) ?: fallback.first,
                    prefs.getString(m.name + "_1", fallback.second) ?: fallback.second,
                    prefs.getString(m.name + "_2", fallback.third) ?: fallback.third
                ))
            }
        }
    }

    var selectedPreset by remember(mode) {
        mutableStateOf<GraphPreset?>(GraphPresets.forMode(mode).firstOrNull {
            it.expressions == drafts[mode]
        })
    }
    var showMathInsight by remember { mutableStateOf(false) }

    fun persist() {
        val edit = prefs.edit().putString("active_mode", mode.name)
        drafts.forEach { (m, formula) ->
            edit.putString(m.name + "_0", formula.first)
            edit.putString(m.name + "_1", formula.second)
            edit.putString(m.name + "_2", formula.third)
        }
        edit.apply()
    }

    val exit = { persist(); onClose() }
    BackHandler(onBack = exit)
    LaunchedEffect(mode) { prefs.edit().putString("active_mode", mode.name).apply() }

    var fullScreen by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var resetRequest by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { playing = false; persist() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var time by remember { mutableFloatStateOf(0f) }
    var parameter by remember { mutableFloatStateOf(1f) }

    val draft = drafts[mode] ?: GraphPresets.first(mode).expressions
    val compiled = remember(mode, draft) {
        draft.expressions().take(mode.fieldCount).map {
            if (it.isBlank()) null else GraphExpression.compile(it).getOrNull()
        }
    }
    val formulaErrors = remember(mode, draft) {
        draft.expressions().take(mode.fieldCount).mapIndexed { index, formula ->
            if (formula.isBlank()) null
            else GraphExpression.compile(formula).exceptionOrNull()?.let {
                (index + 1).toString() + " · " + (it.message ?: "表达式错误")
            }
        }
    }

    // Animation frame pump: 30-60 FPS cap
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var previousFrame = 0L
        var previousUpdate = 0L
        while (isActive) {
            val frame = withFrameNanos { it }
            if (previousFrame == 0L) { previousFrame = frame; previousUpdate = frame; continue }
            if (frame - previousUpdate < 24_000_000L) continue
            val seconds = ((frame - previousFrame) / 1_000_000_000.0).coerceIn(0.0, 0.15)
            time = ((time + seconds.toFloat() * 1.35f) % (2f * PI.toFloat()))
            previousFrame = frame
            previousUpdate = frame
        }
    }

    // Debounced persistence
    LaunchedEffect(mode, draft) {
        delay(500)
        prefs.edit().putString(mode.name + "_0", draft.first)
            .putString(mode.name + "_1", draft.second)
            .putString(mode.name + "_2", draft.third).apply()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CalcChassisBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .testTag("graph_studio_root")
    ) {
        // Top Instrument Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = exit, modifier = Modifier.testTag("graph_close")) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (zh) "返回计算器" else "Back to calculator",
                    tint = CalcTextPrimary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (zh) "图形工作台" else "Graph Studio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CalcTextPrimary
                )
                Text(
                    text = if (zh) "函数 · 波形 · 空间" else "Functions · Waves · Space",
                    style = MaterialTheme.typography.labelSmall,
                    color = CalcTextMuted
                )
            }


        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
            // 3-way Segmented Category Selector: 2D | WAVE | 3D
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CalcDisplayBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, CalcDisplayBorder),
                modifier = Modifier.fillMaxWidth().testTag("graph_category_switch")
            ) {
                Row(modifier = Modifier.padding(3.dp)) {
                    listOf(
                        "2D" to if (zh) "2D曲线" else "2D Curve",
                        "WAVE" to if (zh) "波形" else "Wave",
                        "3D" to if (zh) "3D立体" else "3D Space"
                    ).forEach { (cat, label) ->
                        val isSelected = currentCategory == cat
                        Surface(
                            onClick = {
                                if (currentCategory != cat) {
                                    currentCategory = cat
                                    mode = when (cat) {
                                        "2D" -> GraphPresets.modes2D.first()
                                        "WAVE" -> GraphPresets.modesWave.first()
                                        else -> GraphPresets.modes3D.first()
                                    }
                                    selectedPreset = GraphPresets.forMode(mode).firstOrNull()
                                    playing = false
                                }
                            },
                            shape = RoundedCornerShape(9.dp),
                            color = if (isSelected) CalcAccentCopper else Color.Transparent,
                            modifier = Modifier.weight(1f).testTag("graph_category_" + cat)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) CalcChassisBg else CalcTextSecondary,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }
        }

        if (!fullScreen) {
            // Mode Sub-selector (e.g. Cartesian, Parametric, Polar, Vectors for 2D)
            val subModes = when (currentCategory) {
                "2D" -> GraphPresets.modes2D
                "WAVE" -> GraphPresets.modesWave
                else -> GraphPresets.modes3D
            }
            if (subModes.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subModes.forEach { option ->
                        GraphChoice(
                            label = if (zh) option.zh else option.en,
                            selected = option == mode,
                            onClick = {
                                mode = option
                                selectedPreset = GraphPresets.forMode(mode).firstOrNull()
                                playing = false
                            },
                            tag = "graph_mode_" + option.name
                        )
                    }
                }
            }

            // Presets Horizontal Carousel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Info insight toggle button
                Surface(
                    onClick = { showMathInsight = !showMathInsight },
                    shape = RoundedCornerShape(10.dp),
                    color = if (showMathInsight) CalcAccentCopper.copy(alpha = 0.2f) else CalcDisplayBg,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (showMathInsight) CalcAccentCopper else CalcDisplayBorder
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Math Insight",
                            tint = if (showMathInsight) CalcAccentCopper else CalcTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (zh) "原理" else "Insight",
                            color = if (showMathInsight) CalcAccentCopper else CalcTextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                GraphPresets.forMode(mode).forEachIndexed { idx, preset ->
                    val isCurrent = selectedPreset?.nameEn == preset.nameEn
                    Surface(
                        onClick = {
                            selectedPreset = preset
                            drafts[mode] = preset.expressions
                            parameter = preset.parameter
                            time = 0f
                            playing = preset.animate
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCurrent) CalcAccentCopper.copy(alpha = 0.15f) else CalcDisplayBg,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCurrent) CalcAccentCopper.copy(alpha = 0.8f) else CalcDisplayBorder
                        ),
                        modifier = Modifier.testTag("graph_preset_" + idx)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (preset.animate) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(mint)
                                )
                            }
                            Text(
                                text = if (zh) preset.nameZh else preset.nameEn,
                                color = if (isCurrent) CalcAccentCopper else CalcTextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Math Insight Banner (Collapsible)
            AnimatedVisibility(
                visible = showMathInsight && selectedPreset != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                selectedPreset?.let { p ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = VaultSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .animateContentSize()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (zh) "【${p.mathTag}】${p.nameZh}" else "【${p.mathTag}】${p.nameEn}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = CalcAccentCopper
                                )
                                Text(
                                    text = p.formulaDisplay,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = mint
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (zh) p.insightZh else p.insightEn,
                                style = MaterialTheme.typography.bodySmall,
                                color = CalcTextSecondary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }

        // Main Drawing Viewport (2D Canvas or 3D OpenGL Surface)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = if (fullScreen) 6.dp else 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(if (fullScreen) 12.dp else 18.dp))
                .background(CalcDisplayBg)
                .border(1.dp, CalcDisplayBorder, RoundedCornerShape(if (fullScreen) 12.dp else 18.dp))
                .testTag("graph_plot_view")
        ) {
            if (mode.threeD) {
                val surface = remember(context) { GraphSurfaceView(context) }
                DisposableEffect(surface) { onDispose { surface.onPause() } }
                LaunchedEffect(resetRequest) {
                    if (resetRequest > 0) surface.resetCamera()
                }
                AndroidView(
                    factory = { surface },
                    update = { view ->
                        view.setScene(GraphScene(mode, compiled, time.toDouble(), parameter.toDouble()))
                    },
                    modifier = Modifier.fillMaxSize().testTag("graph_3d_surface")
                )
            } else {
                GraphCanvas2D(
                    mode = mode,
                    formulas = compiled,
                    time = time.toDouble(),
                    parameter = parameter.toDouble(),
                    resetRequest = resetRequest,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top-left HUD badge: Mode & Status
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (playing) mint else CalcAccentCopper)
                )
                Text(
                    text = if (zh) mode.zh else mode.en,
                    color = CalcTextPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = if (playing) "LIVE 60FPS" else "RAD",
                    color = if (playing) mint else CalcTextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFamily
                )
            }

            // Top-right HUD controls
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                IconButton(onClick = { resetRequest++ }, modifier = Modifier.testTag("graph_reset")) {
                    Icon(
                        Icons.Default.CenterFocusStrong,
                        contentDescription = if (zh) "重置视角" else "Reset view",
                        tint = CalcTextSecondary
                    )
                }
                IconButton(
                    onClick = { fullScreen = !fullScreen },
                    modifier = Modifier.testTag("graph_fullscreen")
                ) {
                    Icon(
                        if (fullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (zh) "扩展画布" else "Expand plot",
                        tint = CalcTextSecondary
                    )
                }
            }

            // Bottom-left gesture hint
            Text(
                text = if (mode.threeD) {
                    if (zh) "单指拖动旋转 · 双指缩放视野" else "Drag to orbit · Pinch to zoom"
                } else if (mode == GraphMode.WAVEFORM) {
                    if (zh) "示波器网格 · 拖动平移 · 双击居中" else "Oscilloscope reticle · Pan · Double-tap center"
                } else {
                    if (zh) "点击探查导数切线 · 双指缩放 · 双击复位" else "Tap for tangent probe · Pinch to zoom · Double-tap reset"
                },
                color = CalcTextMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }

        // Formulas Input Panel (Collapsed in FullScreen)
        if (!fullScreen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val labels = when (mode) {
                    GraphMode.CARTESIAN -> listOf("y₁(x)", "y₂(x)", "y₃(x)")
                    GraphMode.WAVEFORM -> listOf("波形₁(x,t)", "谐波/分量(x,t)", "包络/调制(x,t)")
                    GraphMode.PARAMETRIC -> listOf("x(u)", "y(u)")
                    GraphMode.POLAR -> listOf("r(θ)")
                    GraphMode.VECTOR_FIELD -> listOf("P(x,y)", "Q(x,y)")
                    GraphMode.SURFACE -> listOf("z(x,y)")
                    GraphMode.SPACE_CURVE -> listOf("x(u)", "y(u)", "z(u)")
                }
                val count = if ((mode == GraphMode.CARTESIAN || mode == GraphMode.WAVEFORM) && draft.third.isBlank()) 2 else mode.fieldCount
                for (idx in 0 until count) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(plotColors[idx.coerceAtMost(2)])
                        )
                        OutlinedTextField(
                            value = draft.expressions()[idx],
                            onValueChange = { next ->
                                playing = false
                                val updated = draft.update(idx, next.take(192))
                                drafts[mode] = updated
                                selectedPreset = GraphPresets.forMode(mode).firstOrNull {
                                    it.expressions == updated
                                }
                            },
                            singleLine = true,
                            isError = formulaErrors.getOrNull(idx) != null,
                            supportingText = formulaErrors.getOrNull(idx)?.let { issue ->
                                {
                                    Text(
                                        issue,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFFF978F),
                                        modifier = Modifier.testTag("graph_formula_error")
                                    )
                                }
                            },
                            label = { Text(labels[idx], color = CalcTextMuted) },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 14.sp,
                                color = CalcTextPrimary
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CalcAccentCopper,
                                unfocusedBorderColor = CalcDisplayBorder,
                                cursorColor = CalcAccentCopper
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("graph_formula_" + idx),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
                if ((mode == GraphMode.CARTESIAN || mode == GraphMode.WAVEFORM) && draft.third.isBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            onClick = {
                                drafts[mode] = draft.copy(third = "0.5*sin(3*x-t)")
                                selectedPreset = null
                            },
                            color = Color.Transparent,
                            modifier = Modifier.testTag("graph_add_curve")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = CalcAccentCopper,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (zh) "添加叠加曲线" else "Add component curve",
                                    color = CalcAccentCopper,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        // Animated Transport Controls (Play / Time Slider / Parameter Slider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                onClick = { playing = !playing },
                shape = RoundedCornerShape(12.dp),
                color = if (playing) CalcAccentCopper else Color(0xFF2A322F),
                modifier = Modifier.testTag("graph_play_toggle")
            ) {
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (zh) (if (playing) "暂停" else "播放动态波形") else (if (playing) "Pause" else "Play"),
                    tint = if (playing) CalcChassisBg else mint,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp)
                )
            }

            // Time Slider (t)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "t",
                        color = CalcAccentCopper,
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = displayNumber(time.toDouble()),
                        color = CalcTextSecondary,
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Slider(
                    value = time,
                    onValueChange = { playing = false; time = it },
                    valueRange = 0f..(2 * PI.toFloat()),
                    modifier = Modifier
                        .height(30.dp)
                        .semantics { contentDescription = if (zh) "时间 t" else "Time t" }
                        .testTag("graph_time_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = CalcAccentCopper,
                        activeTrackColor = CalcAccentCopper,
                        inactiveTrackColor = grid
                    )
                )
            }

            // Parameter Slider (a)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "a",
                        color = lilac,
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = displayNumber(parameter.toDouble()),
                        color = CalcTextSecondary,
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Slider(
                    value = parameter,
                    onValueChange = { parameter = it },
                    valueRange = 0.2f..3.5f,
                    modifier = Modifier
                        .height(30.dp)
                        .semantics { contentDescription = if (zh) "幅值参数 a" else "Amplitude parameter a" }
                        .testTag("graph_parameter_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = lilac,
                        activeTrackColor = lilac,
                        inactiveTrackColor = grid
                    )
                )
            }
        }
    }
}

@Composable
private fun GraphChoice(label: String, selected: Boolean, onClick: () -> Unit, tag: String) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) CalcAccentCopper.copy(alpha = .18f) else Color.Transparent,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, CalcAccentCopper.copy(alpha = .8f))
        else androidx.compose.foundation.BorderStroke(1.dp, CalcDisplayBorder),
        modifier = Modifier.testTag(tag)
    ) {
        Text(
            text = label,
            color = if (selected) CalcAccentCopper else CalcTextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/**
 * 2D & Waveform Canvas Plotter:
 * Hardware-accelerated drawing with analytical tangent calculus probe and oscilloscope telemetry.
 */
@Composable
private fun GraphCanvas2D(
    mode: GraphMode,
    formulas: List<GraphExpression?>,
    time: Double,
    parameter: Double,
    resetRequest: Int,
    modifier: Modifier = Modifier
) {
    val camera = remember(mode) { mutableStateOf(GraphViewport()) }
    var inspectX by remember(mode) { mutableStateOf<Double?>(null) }

    LaunchedEffect(resetRequest) {
        if (resetRequest > 0) {
            camera.value = GraphViewport()
            inspectX = null
        }
    }

    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(144, 159, 164)
            textSize = 28f
        }
    }
    val hudPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(232, 183, 130)
            textSize = 32f
            isFakeBoldText = true
        }
    }
    val scopePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(105, 210, 172)
            textSize = 30f
            isFakeBoldText = true
        }
    }

    val vars = remember { GraphExpression.Variables() }

    Canvas(
        modifier = modifier
            .pointerInput(mode) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    camera.value = camera.value.transformed(
                        widthPx = size.width.toFloat(),
                        heightPx = size.height.toFloat(),
                        focalX = centroid.x,
                        focalY = centroid.y,
                        panX = pan.x,
                        panY = pan.y,
                        zoom = zoom,
                        lockAspect = mode != GraphMode.CARTESIAN && mode != GraphMode.WAVEFORM
                    )
                }
            }
            .pointerInput(mode) {
                detectTapGestures(
                    onDoubleTap = {
                        camera.value = GraphViewport()
                        inspectX = null
                    },
                    onTap = { point ->
                        if (mode == GraphMode.CARTESIAN || mode == GraphMode.WAVEFORM) {
                            val v = camera.value
                            inspectX = v.centerX + (point.x - size.width / 2) *
                                    v.spanX / size.width.coerceAtLeast(1)
                        }
                    }
                )
            }
            .testTag("graph_2d_canvas")
    ) {
        val w = size.width
        val h = size.height
        if (w <= 2f || h <= 2f) return@Canvas

        val v = camera.value
        val xScale = w / v.spanX
        val visibleY = v.visibleY(w, h,
            lockAspect = mode != GraphMode.CARTESIAN && mode != GraphMode.WAVEFORM)
        val yScale = h / visibleY

        fun sx(x: Double): Float = (w * .5 + (x - v.centerX) * xScale).toFloat()
        fun sy(y: Double): Float = (h * .5 - (y - v.centerY) * yScale).toFloat()

        val minX = v.centerX - v.spanX / 2
        val maxX = v.centerX + v.spanX / 2
        val minY = v.centerY - visibleY / 2
        val maxY = v.centerY + visibleY / 2

        fun gridStep(span: Double): Double {
            val base = 10.0.pow(floor(log10(span / 6)))
            return listOf(1.0, 2.0, 5.0, 10.0).first { it * base >= span / 8 } * base
        }

        val stepX = gridStep(v.spanX)
        val stepY = gridStep(visibleY)

        // Draw Coordinate Grid Lines
        for (index in (floor(minX / stepX).toInt())..(ceil(maxX / stepX).toInt())) {
            val world = index * stepX
            val axis = abs(world) < 1e-8
            val pixel = sx(world)
            drawLine(
                if (axis) slate else grid,
                Offset(pixel, 0f),
                Offset(pixel, h),
                strokeWidth = if (axis) 1.8f else 1f
            )
        }
        for (index in (floor(minY / stepY).toInt())..(ceil(maxY / stepY).toInt())) {
            val world = index * stepY
            val axis = abs(world) < 1e-8
            val pixel = sy(world)
            drawLine(
                if (axis) slate else grid,
                Offset(0f, pixel),
                Offset(w, pixel),
                strokeWidth = if (axis) 1.8f else 1f
            )
        }

        // Oscilloscope dotted reticle if WAVEFORM mode
        if (mode == GraphMode.WAVEFORM) {
            val cx = sx(0.0)
            val cy = sy(0.0)
            val dotSpacing = 16.dp.toPx()
            for (dx in 0 until (w / dotSpacing).toInt()) {
                val x = dx * dotSpacing
                drawCircle(Color(0xFF384643), radius = 1.3f, center = Offset(x, cy.coerceIn(4f, h - 4f)))
            }
            for (dy in 0 until (h / dotSpacing).toInt()) {
                val y = dy * dotSpacing
                drawCircle(Color(0xFF384643), radius = 1.3f, center = Offset(cx.coerceIn(4f, w - 4f), y))
            }
        }

        // Grid numbers
        textPaint.textSize = 10.sp.toPx()
        drawIntoCanvas { context ->
            val canvas = context.nativeCanvas
            for (index in (floor(minX / stepX).toInt())..(ceil(maxX / stepX).toInt())) {
                val n = index * stepX
                if (n != 0.0) {
                    canvas.drawText(
                        "%.3g".format(Locale.US, n),
                        sx(n) + 3f,
                        (sy(0.0).coerceIn(12f, h - 16f) + 16f),
                        textPaint
                    )
                }
            }
            for (index in (floor(minY / stepY).toInt())..(ceil(maxY / stepY).toInt())) {
                val n = index * stepY
                if (n != 0.0) {
                    canvas.drawText(
                        "%.3g".format(Locale.US, n),
                        (sx(0.0).coerceIn(4f, w - 56f) + 4f),
                        sy(n) - 4f,
                        textPaint
                    )
                }
            }
        }

        vars.t = time
        vars.a = parameter

        if (mode == GraphMode.VECTOR_FIELD) {
            val fx = formulas.getOrNull(0)
            val fy = formulas.getOrNull(1)
            if (fx != null && fy != null) {
                val spacing = 35.dp.toPx()
                val cols = (w / spacing).toInt().coerceIn(4, 24)
                val rows = (h / spacing).toInt().coerceIn(4, 25)
                for (i in 0..cols) for (j in 0..rows) {
                    val px = (i + 0.5f) * w / (cols + 1)
                    val py = (j + 0.5f) * h / (rows + 1)
                    vars.x = v.centerX + (px - w * .5f) / xScale
                    vars.y = v.centerY - (py - h * .5f) / yScale
                    val vx = fx.evaluate(vars)
                    val vy = fy.evaluate(vars)
                    val mag = hypot(vx, vy)
                    if (!mag.isFinite() || mag < 1e-9) continue
                    val dx = (vx / mag * spacing * .30).toFloat()
                    val dy = (-vy / mag * spacing * .30).toFloat()
                    val end = Offset(px + dx, py + dy)
                    drawLine(
                        mint,
                        Offset(px - dx * .55f, py - dy * .55f),
                        end,
                        1.7.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    val angle = atan2(dy, dx)
                    val arrow = 5.dp.toPx()
                    drawLine(
                        mint, end, Offset(
                            end.x - arrow * cos(angle - .52f), end.y - arrow * sin(angle - .52f)
                        ), 1.5.dp.toPx()
                    )
                    drawLine(
                        mint, end, Offset(
                            end.x - arrow * cos(angle + .52f), end.y - arrow * sin(angle + .52f)
                        ), 1.5.dp.toPx()
                    )
                }
            }
        } else {
            val isCartesianOrWave = mode == GraphMode.CARTESIAN || mode == GraphMode.WAVEFORM
            val numberOfPaths = if (isCartesianOrWave) formulas.size else 1
            val samples = (w * 1.5).toInt().coerceIn(280, 1400)

            // Oscilloscope Telemetry Calculation for WAVEFORM
            var waveMin = Double.POSITIVE_INFINITY
            var waveMax = Double.NEGATIVE_INFINITY
            var waveSumSq = 0.0
            var waveValidCount = 0

            for (lineIndex in 0 until numberOfPaths) {
                val one = formulas.getOrNull(if (isCartesianOrWave) lineIndex else 0) ?: continue
                val two = if (mode == GraphMode.PARAMETRIC) formulas.getOrNull(1) else null
                if (mode == GraphMode.PARAMETRIC && two == null) continue

                val path = Path()
                var previousY = Float.NaN
                var started = false

                for (i in 0..samples) {
                    val fraction = i.toDouble() / samples
                    val x: Double
                    val y: Double
                    when (mode) {
                        GraphMode.CARTESIAN, GraphMode.WAVEFORM -> {
                            vars.x = minX + fraction * v.spanX
                            x = vars.x
                            y = one.evaluate(vars)

                            if (lineIndex == 0 && y.isFinite()) {
                                if (y < waveMin) waveMin = y
                                if (y > waveMax) waveMax = y
                                waveSumSq += y * y
                                waveValidCount++
                            }
                        }
                        GraphMode.PARAMETRIC -> {
                            vars.u = fraction * 2 * PI
                            x = one.evaluate(vars)
                            y = two!!.evaluate(vars)
                        }
                        GraphMode.POLAR -> {
                            vars.u = fraction * 4 * PI
                            val radius = one.evaluate(vars)
                            x = radius * cos(vars.u)
                            y = radius * sin(vars.u)
                        }
                        else -> {
                            x = Double.NaN
                            y = Double.NaN
                        }
                    }

                    if (!x.isFinite() || !y.isFinite() || abs(x) > 1e8 || abs(y) > 1e8) {
                        started = false
                        continue
                    }
                    val px = sx(x)
                    val py = sy(y)
                    if (px < -w * 2 || px > w * 3 || py < -h * 2 || py > h * 3) {
                        started = false
                        continue
                    }
                    if (!started || (previousY.isFinite() && abs(py - previousY) > h * .75)) {
                        path.moveTo(px, py)
                        started = true
                    } else {
                        path.lineTo(px, py)
                    }
                    previousY = py
                }

                val strokeColor = plotColors[lineIndex.coerceAtMost(2)]
                val strokeWidth = if (mode == GraphMode.WAVEFORM && lineIndex == 0) 2.6.dp.toPx() else 2.1.dp.toPx()

                // Glow shadow if Waveform primary line
                if (mode == GraphMode.WAVEFORM && lineIndex == 0) {
                    drawPath(
                        path,
                        color = strokeColor.copy(alpha = 0.25f),
                        style = Stroke(width = strokeWidth + 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                drawPath(
                    path,
                    color = strokeColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Oscilloscope Dashboard HUD if in WAVEFORM mode
            if (mode == GraphMode.WAVEFORM && waveValidCount > 0 && waveMin.isFinite() && waveMax.isFinite()) {
                val vpp = waveMax - waveMin
                val vrms = sqrt(waveSumSq / waveValidCount)
                drawIntoCanvas { cv ->
                    val txt = "P-P = %.2f   RMS = %.2f".format(Locale.US, vpp, vrms)
                    cv.nativeCanvas.drawText(txt, 16.dp.toPx(), 44.dp.toPx(), scopePaint)
                }
            }

            // Interactive Tangent & Derivative Probe (for CARTESIAN)
            if (mode == GraphMode.CARTESIAN) {
                inspectX?.let { x0 ->
                    val px = sx(x0)
                    if (px in 0f..w) {
                        // Vertical guideline
                        drawLine(
                            CalcTextSecondary.copy(alpha = .40f),
                            Offset(px, 0f),
                            Offset(px, h),
                            1.dp.toPx()
                        )

                        val prime = formulas.firstOrNull()
                        if (prime != null) {
                            vars.x = x0
                            val y0 = prime.evaluate(vars)
                            if (y0.isFinite() && abs(y0) <= 1e7) {
                                val py = sy(y0)

                                // Numerical derivative dy/dx = (f(x+h) - f(x-h)) / 2h
                                val delta = 0.005 * v.spanX.coerceAtLeast(0.1)
                                vars.x = x0 + delta
                                val yPlus = prime.evaluate(vars)
                                vars.x = x0 - delta
                                val yMinus = prime.evaluate(vars)
                                val slope = if (yPlus.isFinite() && yMinus.isFinite()) (yPlus - yMinus) / (2 * delta) else Double.NaN

                                // Tangent line segment
                                if (slope.isFinite() && abs(slope) < 80) {
                                    val tSpan = v.spanX * 0.22
                                    val x1 = x0 - tSpan
                                    val y1 = y0 - slope * tSpan
                                    val x2 = x0 + tSpan
                                    val y2 = y0 + slope * tSpan
                                    drawLine(
                                        color = CalcAccentCopper.copy(alpha = 0.8f),
                                        start = Offset(sx(x1), sy(y1)),
                                        end = Offset(sx(x2), sy(y2)),
                                        strokeWidth = 1.6.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                                    )
                                }

                                // Glowing contact point
                                drawCircle(
                                    CalcAccentCopper.copy(alpha = 0.35f),
                                    radius = 8.dp.toPx(),
                                    center = Offset(px, py)
                                )
                                drawCircle(
                                    CalcAccentCopper,
                                    radius = 4.5.dp.toPx(),
                                    center = Offset(px, py)
                                )
                                drawCircle(
                                    CalcDisplayBg,
                                    radius = 2.dp.toPx(),
                                    center = Offset(px, py)
                                )

                                // HUD text with coordinates and instantaneous slope
                                drawIntoCanvas { cv ->
                                    val slopeStr = if (slope.isFinite()) "  dy/dx = " + displayNumber(slope) else ""
                                    val txt = "x = " + displayNumber(x0) + "  y = " + displayNumber(y0) + slopeStr
                                    cv.nativeCanvas.drawText(
                                        txt,
                                        16.dp.toPx(),
                                        44.dp.toPx(),
                                        hudPaint
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
