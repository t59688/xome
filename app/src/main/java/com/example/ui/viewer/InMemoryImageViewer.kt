package com.example.ui.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.RandomAccessDecryptedStream
import com.example.crypto.VaultSessionManager
import com.example.data.VaultFileEntity
import com.example.storage.VaultStorageManager
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.AppLanguage
import com.example.ui.theme.CalcAccentCopper
import com.example.ui.theme.CalcClearKeyText
import com.example.ui.theme.CalcTextMuted
import com.example.ui.theme.CalcTextPrimary
import com.example.ui.theme.CalcTextSecondary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VaultBorderSubtle
import com.example.ui.theme.VaultAccentPrimary
import com.example.ui.theme.VaultEmeraldSuccess
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceElevated
import com.example.ui.theme.VaultVoidBg
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DecryptedImagePayload(
    val fullImageBitmap: ImageBitmap,
    val androidBitmap: Bitmap,
    val rawImageBytes: ByteArray,
    val imageWidth: Int,
    val imageHeight: Int,
    val totalChunks: Int,
    val chunkSize: Int,
    val plainLength: Long,
    val sampleOffsetRead: Long
)

@Composable
fun InMemoryImageViewerOverlay(
    imageFiles: List<VaultFileEntity>,
    initialFileId: Long,
    onClose: () -> Unit,
    onExportFile: (VaultFileEntity, (Result<String>) -> Unit) -> Unit,
    onShredFile: (VaultFileEntity) -> Unit,
    onStatusBarContrastChanged: (Boolean?) -> Unit
) {
    BackHandler { onClose() }
    val strings = LocalAppStrings.current

    if (imageFiles.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val initialIndex = remember(imageFiles, initialFileId) {
        imageFiles.indexOfFirst { it.id == initialFileId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { imageFiles.size }
    )

    var showChrome by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }
    var exportFeedback by remember { mutableStateOf<ViewerExportFeedback?>(null) }
    DisposableEffect(Unit) {
        onDispose { onStatusBarContrastChanged(null) }
    }
    LaunchedEffect(pagerState.currentPage) {
        // Until the new page is decoded, the viewer background is dark.
        onStatusBarContrastChanged(false)
        exportFeedback = null
    }
    var showStreamTelemetry by remember { mutableStateOf(false) }
    var isCurrentPageZoomed by remember { mutableStateOf(false) }

    // Page-scoped transformation triggers
    var rotateDeltaSignal by remember { mutableIntStateOf(0) }
    var rotateDeltaDegrees by remember { mutableFloatStateOf(0f) }
    var resetTransformSignal by remember { mutableIntStateOf(0) }
    var currentScaleDisplay by remember { mutableFloatStateOf(1f) }
    var currentRotationDisplay by remember { mutableFloatStateOf(0f) }
    var activeTileLabel by remember { mutableStateOf("BASE FRAME (RAM)") }

    val currentFile = imageFiles.getOrNull(pagerState.currentPage) ?: imageFiles.first()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultVoidBg)
            .testTag("in_memory_image_viewer")
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isCurrentPageZoomed,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val fileItem = imageFiles[pageIndex]
            val isCurrentPage = pageIndex == pagerState.currentPage

            InMemorySingleImagePage(
                fileEntity = fileItem,
                isCurrentPage = isCurrentPage,
                rotateSignal = if (isCurrentPage) rotateDeltaSignal else 0,
                rotateDelta = rotateDeltaDegrees,
                resetSignal = if (isCurrentPage) resetTransformSignal else 0,
                onZoomedStateChanged = { zoomed, scaleVal, rotVal, tileDesc ->
                    if (isCurrentPage) {
                        isCurrentPageZoomed = zoomed
                        currentScaleDisplay = scaleVal
                        currentRotationDisplay = rotVal
                        activeTileLabel = tileDesc
                    }
                },
                onSingleTapToggleChrome = {
                    showChrome = !showChrome
                },
                onStatusBarContrastChanged = { darkIcons ->
                    if (isCurrentPage) onStatusBarContrastChanged(darkIcons)
                }
            )
        }

        // Top Floating Glassmorphic Bar
        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = VaultSurface.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, VaultBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.testTag("viewer_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to vault",
                                tint = CalcTextPrimary
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = currentFile.originalName,
                                style = MaterialTheme.typography.titleMedium,
                                color = CalcTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = strings.ramStreamZeroDiskSubtitle(pagerState.currentPage + 1, imageFiles.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = VaultEmeraldSuccess
                            )
                        }

                        IconButton(
                            onClick = { showStreamTelemetry = !showStreamTelemetry },
                            modifier = Modifier.testTag("viewer_telemetry_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = "Toggle cryptographic stream HUD",
                                tint = if (showStreamTelemetry) VaultAccentPrimary else CalcTextSecondary
                            )
                        }

                        IconButton(
                            onClick = {
                                isExporting = true
                                exportFeedback = null
                                onExportFile(currentFile) { result ->
                                    isExporting = false
                                    exportFeedback = viewerExportFeedback(result, strings.language == AppLanguage.ZH)
                                }
                            },
                            enabled = !isExporting,
                            modifier = Modifier.testTag("viewer_export_button")
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = VaultAccentPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Decrypt and restore to Downloads",
                                    tint = VaultAccentPrimary
                                )
                            }
                        }

                        IconButton(
                            onClick = { onShredFile(currentFile) },
                            modifier = Modifier.testTag("viewer_shred_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = "Shred encrypted blob",
                                tint = CalcClearKeyText
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = exportFeedback != null) {
                    exportFeedback?.let { feedback ->
                        ViewerExportFeedbackBanner(
                            feedback = feedback,
                            onDismiss = { exportFeedback = null }
                        )
                    }
                }

                // Live RandomAccessDecryptedStream & BitmapRegionDecoder HUD
                AnimatedVisibility(visible = showStreamTelemetry) {
                    Surface(
                        color = VaultSurfaceElevated.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, VaultAccentPrimary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "BLOB: blobs/${currentFile.blobFilename.take(12)}….dat",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VaultAccentPrimary
                                )
                                Text(
                                    text = "DECODER: $activeTileLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CalcTextSecondary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "ZOOM: ${"%.2f".format(currentScaleDisplay)}x",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CalcAccentCopper
                                )
                                Text(
                                    text = "ROT: ${currentRotationDisplay.roundToInt()}°",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VaultEmeraldSuccess
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Floating Matrix Transform Toolbar (-90°, +90°, Reset, Zoom Steps)
        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Surface(
                color = VaultSurface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, VaultBorderSubtle)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            rotateDeltaDegrees = -90f
                            rotateDeltaSignal++
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(VaultSurfaceElevated)
                            .testTag("viewer_rotate_ccw")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateLeft,
                            contentDescription = "Rotate -90 degrees",
                            tint = CalcTextPrimary
                        )
                    }

                    IconButton(
                        onClick = {
                            rotateDeltaDegrees = 90f
                            rotateDeltaSignal++
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(VaultSurfaceElevated)
                            .testTag("viewer_rotate_cw")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = "Rotate +90 degrees",
                            tint = CalcTextPrimary
                        )
                    }

                    Surface(
                        onClick = { resetTransformSignal++ },
                        shape = RoundedCornerShape(14.dp),
                        color = VaultAccentPrimary.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, VaultAccentPrimary.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("viewer_reset_transform")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CenterFocusStrong,
                                contentDescription = "Reset zoom and rotation",
                                tint = VaultAccentPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = strings.fit1xBtn,
                                style = MaterialTheme.typography.labelLarge,
                                color = VaultAccentPrimary,
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
private fun InMemorySingleImagePage(
    fileEntity: VaultFileEntity,
    isCurrentPage: Boolean,
    rotateSignal: Int,
    rotateDelta: Float,
    resetSignal: Int,
    onZoomedStateChanged: (isZoomed: Boolean, scale: Float, rotation: Float, tileInfo: String) -> Unit,
    onSingleTapToggleChrome: () -> Unit,
    onStatusBarContrastChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val statusBarHeight = WindowInsets.statusBars.getTop(LocalDensity.current)

    var payload by remember(fileEntity.id) { mutableStateOf<DecryptedImagePayload?>(null) }
    var highResRegionTile by remember(fileEntity.id) { mutableStateOf<ImageBitmap?>(null) }
    var errorMessage by remember(fileEntity.id) { mutableStateOf<String?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val scaleAnim = remember(fileEntity.id) { Animatable(1f) }
    val offsetXAnim = remember(fileEntity.id) { Animatable(0f) }
    val offsetYAnim = remember(fileEntity.id) { Animatable(0f) }
    val rotationAnim = remember(fileEntity.id) { Animatable(0f) }

    // Decrypt image stream strictly in RAM on page entry, and wipe bytes on page exit
    DisposableEffect(fileEntity.id) {
        onDispose {
            payload?.rawImageBytes?.let { Arrays.fill(it, 0.toByte()) }
            payload = null
            highResRegionTile = null
        }
    }

    LaunchedEffect(fileEntity.id) {
        withContext(Dispatchers.IO) {
            val session = VaultSessionManager.activeSession.value
            if (session == null) {
                errorMessage = "Session locked"
                return@withContext
            }
            val masterKey = session.borrowMasterKeyCopy()
            try {
                val tree = VaultStorageManager.resolveVaultTree(context)
                val blobFile = File(tree.blobsDir, fileEntity.blobFilename)
                RandomAccessDecryptedStream(blobFile, masterKey).use { stream ->
                    // Exercise O(1) random-access seek on last chunk before reading full stream
                    val probeOffset = (stream.plainLength / 2L).coerceAtLeast(0L)
                    stream.seek(probeOffset)
                    val fullBytes = stream.decryptAllToMemory()
                    val decoded = BitmapFactory.decodeByteArray(fullBytes, 0, fullBytes.size)
                    if (decoded != null) {
                        payload = DecryptedImagePayload(
                            fullImageBitmap = decoded.asImageBitmap(),
                            androidBitmap = decoded,
                            rawImageBytes = fullBytes,
                            imageWidth = decoded.width,
                            imageHeight = decoded.height,
                            totalChunks = stream.totalChunks,
                            chunkSize = stream.chunkSize,
                            plainLength = stream.plainLength,
                            sampleOffsetRead = probeOffset
                        )
                    } else {
                        Arrays.fill(fullBytes, 0.toByte())
                        errorMessage = "Cannot decode image stream in memory"
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Stream decrypt error: ${e.message}"
            } finally {
                Arrays.fill(masterKey, 0.toByte())
            }
        }
    }

    LaunchedEffect(isCurrentPage, payload, containerSize, statusBarHeight) {
        val loaded = payload ?: return@LaunchedEffect
        if (!isCurrentPage || containerSize == IntSize.Zero) return@LaunchedEffect
        onStatusBarContrastChanged(
            ImageViewerStatusBarContrast.needsDarkIcons(
                imageWidth = loaded.imageWidth,
                imageHeight = loaded.imageHeight,
                viewportWidth = containerSize.width,
                viewportHeight = containerSize.height,
                statusBarHeight = statusBarHeight,
                pixelAt = loaded.androidBitmap::getPixel
            )
        )
    }

    // Handle toolbar ±90° rotation
    LaunchedEffect(rotateSignal) {
        if (rotateSignal > 0 && isCurrentPage) {
            val targetRot = ((rotationAnim.value / 90f).roundToInt() * 90f) + rotateDelta
            rotationAnim.animateTo(
                targetValue = targetRot,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    // Handle toolbar Reset 1.0x
    LaunchedEffect(resetSignal) {
        if (resetSignal > 0 && isCurrentPage) {
            launch { scaleAnim.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) }
            launch { offsetXAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
            launch { offsetYAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
            launch { rotationAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
        }
    }

    // High-Res BitmapRegionDecoder tile decoding when zoomed > 2.0x
    LaunchedEffect(scaleAnim.value, offsetXAnim.value, offsetYAnim.value, payload) {
        val currentPayload = payload ?: return@LaunchedEffect
        val s = scaleAnim.value
        val rot = rotationAnim.value

        if (s >= 2.1f && currentPayload.rawImageBytes.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                try {
                    val bais = ByteArrayInputStream(currentPayload.rawImageBytes)
                    val decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        BitmapRegionDecoder.newInstance(bais)
                    } else {
                        @Suppress("DEPRECATION")
                        BitmapRegionDecoder.newInstance(bais, false)
                    }
                    if (decoder != null) {
                        val w = currentPayload.imageWidth
                        val h = currentPayload.imageHeight
                        val cropW = (w / s).toInt().coerceIn(64, w)
                        val cropH = (h / s).toInt().coerceIn(64, h)
                        val left = ((w - cropW) / 2).coerceIn(0, (w - cropW).coerceAtLeast(0))
                        val top = ((h - cropH) / 2).coerceIn(0, (h - cropH).coerceAtLeast(0))
                        val rect = Rect(left, top, left + cropW, top + cropH)
                        val opts = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        val regionBmp = decoder.decodeRegion(rect, opts)
                        decoder.recycle()
                        if (regionBmp != null) {
                            highResRegionTile = regionBmp.asImageBitmap()
                        }
                    }
                } catch (_: Exception) {
                }
            }
        } else {
            highResRegionTile = null
        }

        val tileDesc = if (s >= 2.1f) {
            "BitmapRegionDecoder TILE (${currentPayload.totalChunks}x64KB)"
        } else {
            "RAM STREAM (${currentPayload.imageWidth}×${currentPayload.imageHeight}, ${currentPayload.totalChunks} Chunks)"
        }
        onZoomedStateChanged(s > 1.04f, s, rot, tileDesc)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(fileEntity.id) {
                detectTapGestures(
                    onTap = { onSingleTapToggleChrome() },
                    onDoubleTap = { tapOffset ->
                        // Cycle smoothly between 1.0x -> 2.5x -> 5.0x -> 1.0x around tap focal point
                        val currentScale = scaleAnim.value
                        val nextScale = when {
                            currentScale < 1.8f -> 2.5f
                            currentScale < 4.0f -> 5.0f
                            else -> 1.0f
                        }
                        val centerX = containerSize.width / 2f
                        val centerY = containerSize.height / 2f
                        val targetOffsetX = if (nextScale == 1.0f) {
                            0f
                        } else {
                            (centerX - tapOffset.x) * (nextScale - 1f) * 0.45f
                        }
                        val targetOffsetY = if (nextScale == 1.0f) {
                            0f
                        } else {
                            (centerY - tapOffset.y) * (nextScale - 1f) * 0.45f
                        }
                        coroutineScope.launch {
                            launch {
                                scaleAnim.animateTo(
                                    nextScale,
                                    spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                            launch {
                                offsetXAnim.animateTo(
                                    targetOffsetX,
                                    spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                            launch {
                                offsetYAnim.animateTo(
                                    targetOffsetY,
                                    spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                            if (nextScale == 1.0f) {
                                launch {
                                    rotationAnim.animateTo(
                                        0f,
                                        spring(stiffness = Spring.StiffnessMediumLow)
                                    )
                                }
                            }
                        }
                    }
                )
            }
            .pointerInput(fileEntity.id) {
                // Custom multi-touch gesture handler that only consumes single-finger pan when zoomed in,
                // allowing HorizontalPager to smoothly swipe between images when scale == 1.0f!
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        val zoomChange = event.calculateZoom()
                        val rotationChange = event.calculateRotation()
                        val panChange = event.calculatePan()

                        val isMultiTouch = pointerCount >= 2
                        val isZoomedIn = scaleAnim.value > 1.04f

                        if (isMultiTouch || isZoomedIn) {
                            val newScale = (scaleAnim.value * zoomChange).coerceIn(0.8f, 8.0f)
                            val newRot = if (isMultiTouch && abs(rotationChange) > 0.2f) {
                                rotationAnim.value + rotationChange
                            } else {
                                rotationAnim.value
                            }

                            // Boundary damping for pan
                            val maxPanX = ((containerSize.width * (newScale - 1f).coerceAtLeast(0f)) / 2f) + 120f
                            val maxPanY = ((containerSize.height * (newScale - 1f).coerceAtLeast(0f)) / 2f) + 120f
                            val newOffsetX = (offsetXAnim.value + panChange.x).coerceIn(-maxPanX, maxPanX)
                            val newOffsetY = (offsetYAnim.value + panChange.y).coerceIn(-maxPanY, maxPanY)

                            coroutineScope.launch {
                                scaleAnim.snapTo(newScale)
                                rotationAnim.snapTo(newRot)
                                offsetXAnim.snapTo(if (newScale <= 1.0f) 0f else newOffsetX)
                                offsetYAnim.snapTo(if (newScale <= 1.0f) 0f else newOffsetY)
                            }

                            event.changes.forEach { change ->
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // Snap back if pinched below 1.0x
                    if (scaleAnim.value < 1.0f) {
                        coroutineScope.launch {
                            launch { scaleAnim.animateTo(1f, spring()) }
                            launch { offsetXAnim.animateTo(0f, spring()) }
                            launch { offsetYAnim.animateTo(0f, spring()) }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            errorMessage != null -> {
                Text(
                    text = errorMessage ?: "Error",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CalcClearKeyText
                )
            }
            payload == null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = VaultAccentPrimary)
                    Text(
                        text = LocalAppStrings.current.streamingChunksIntoRam,
                        style = MaterialTheme.typography.labelLarge,
                        color = CalcTextSecondary
                    )
                }
            }
            else -> {
                val activePayload = payload!!
                Image(
                    bitmap = activePayload.fullImageBitmap,
                    contentDescription = fileEntity.originalName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scaleAnim.value
                            scaleY = scaleAnim.value
                            translationX = offsetXAnim.value
                            translationY = offsetYAnim.value
                            rotationZ = rotationAnim.value
                        }
                )
            }
        }
    }
}

private data class ViewerExportFeedback(val message: String, val isError: Boolean)

private fun viewerExportFeedback(result: Result<String>, chinese: Boolean): ViewerExportFeedback =
    result.fold(
        onSuccess = { path ->
            ViewerExportFeedback(
                message = if (chinese) "已解密保存至 $path" else "Decrypted file saved to $path",
                isError = false
            )
        },
        onFailure = { error ->
            ViewerExportFeedback(
                message = if (chinese) "保存失败：${error.localizedMessage ?: "未知错误"}"
                          else "Save failed: ${error.localizedMessage ?: "Unknown error"}",
                isError = true
            )
        }
    )

@Composable
private fun ViewerExportFeedbackBanner(
    feedback: ViewerExportFeedback,
    onDismiss: () -> Unit
) {
    val accent = if (feedback.isError) CalcClearKeyText else VaultEmeraldSuccess
    Surface(
        onClick = onDismiss,
        color = VaultSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = .5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("viewer_export_feedback")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (feedback.isError) Icons.Default.ErrorOutline
                              else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = feedback.message,
                style = MaterialTheme.typography.bodyMedium,
                color = CalcTextPrimary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss export result",
                tint = CalcTextSecondary,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
fun InMemoryDocumentViewerDialog(
    fileEntity: VaultFileEntity,
    decryptedContent: String,
    onClose: () -> Unit,
    onExportFile: (VaultFileEntity, (Result<String>) -> Unit) -> Unit,
    onShredFile: (VaultFileEntity) -> Unit
) {
    BackHandler { onClose() }
    val language = LocalAppStrings.current.language
    var isExporting by remember { mutableStateOf(false) }
    var exportFeedback by remember { mutableStateOf<ViewerExportFeedback?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultVoidBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("in_memory_doc_viewer")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top bar
            Surface(
                color = VaultSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, VaultBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close document viewer",
                            tint = CalcTextPrimary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileEntity.originalName,
                            style = MaterialTheme.typography.titleMedium,
                            color = CalcTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = LocalAppStrings.current.inMemoryDecryptedStreamSubtitle(fileEntity.blobFilename.take(10)),
                            style = MaterialTheme.typography.labelSmall,
                            color = VaultAccentPrimary
                        )
                    }
                    IconButton(
                        onClick = {
                            isExporting = true
                            exportFeedback = null
                            onExportFile(fileEntity) { result ->
                                isExporting = false
                                exportFeedback = viewerExportFeedback(result, language == AppLanguage.ZH)
                            }
                        },
                        enabled = !isExporting
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = VaultAccentPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Decrypt and export",
                                tint = VaultAccentPrimary
                            )
                        }
                    }
                    IconButton(onClick = { onShredFile(fileEntity) }) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Shred file",
                            tint = CalcClearKeyText
                        )
                    }
                }
            }

            AnimatedVisibility(visible = exportFeedback != null) {
                exportFeedback?.let { feedback ->
                    ViewerExportFeedbackBanner(feedback, onDismiss = { exportFeedback = null })
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scrollable decrypted text / hex content
            Surface(
                color = VaultSurfaceElevated,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, VaultBorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(
                        text = decryptedContent,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = CalcTextPrimary
                    )
                }
            }
        }
    }
}
