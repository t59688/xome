package com.example.ui.viewer

import kotlin.math.min

/**
 * Chooses readable Android status-bar icons from the image pixels actually displayed
 * behind the transparent status bar. ContentScale.Fit may letterbox the image, so a
 * bright bitmap is not necessarily a bright status-bar background.
 *
 * All coordinates are screen pixels; [pixelAt] receives bitmap coordinates and
 * packed ARGB values. This pure calculation can be unit-tested without Android UI.
 */
internal object ImageViewerStatusBarContrast {
    fun needsDarkIcons(
        imageWidth: Int,
        imageHeight: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        statusBarHeight: Int,
        pixelAt: (Int, Int) -> Int
    ): Boolean {
        if (imageWidth <= 0 || imageHeight <= 0 ||
            viewportWidth <= 0 || viewportHeight <= 0 || statusBarHeight <= 0
        ) return false

        val scale = min(
            viewportWidth.toFloat() / imageWidth,
            viewportHeight.toFloat() / imageHeight
        )
        val left = (viewportWidth - imageWidth * scale) / 2f
        val top = (viewportHeight - imageHeight * scale) / 2f

        // Sample both time and signal-icon regions, rather than only image center.
        val xFractions = floatArrayOf(.08f, .16f, .27f, .72f, .83f, .94f)
        val yFractions = floatArrayOf(.30f, .65f)
        var brightnessSum = 0f

        for (fy in yFractions) {
            val screenY = statusBarHeight * fy
            for (fx in xFractions) {
                val screenX = viewportWidth * fx
                val bitmapX = ((screenX - left) / scale).toInt()
                val bitmapY = ((screenY - top) / scale).toInt()
                val bgBrightness = (13f * .2126f + 16f * .7152f + 19f * .0722f) / 255f

                if (bitmapX !in 0 until imageWidth || bitmapY !in 0 until imageHeight) {
                    brightnessSum += bgBrightness
                } else {
                    val argb = pixelAt(bitmapX, bitmapY)
                    val a = ((argb ushr 24) and 0xff) / 255f
                    val r = (argb ushr 16) and 0xff
                    val g = (argb ushr 8) and 0xff
                    val b = argb and 0xff
                    val brightness = (r * .2126f + g * .7152f + b * .0722f) / 255f
                    brightnessSum += brightness * a + bgBrightness * (1 - a)
                }
            }
        }
        return brightnessSum / (xFractions.size * yFractions.size) >= .60f
    }
}
