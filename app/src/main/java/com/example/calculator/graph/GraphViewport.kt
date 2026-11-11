package com.example.calculator.graph

/**
 * World coordinates of the visible plot, independent of screen density.
 * Cartesian functions can use independent vertical scaling; polar and parametric
 * plots retain equal X/Y scale so circles don't become ellipses.
 */
data class GraphViewport(
    val centerX: Double = 0.0,
    val centerY: Double = 0.0,
    val spanX: Double = 12.0,
    val spanY: Double = 5.0
) {
    fun visibleY(widthPx: Float, heightPx: Float, lockAspect: Boolean): Double =
        if (lockAspect) spanX * heightPx / widthPx.coerceAtLeast(1f) else spanY

    fun transformed(
        widthPx: Float,
        heightPx: Float,
        focalX: Float,
        focalY: Float,
        panX: Float,
        panY: Float,
        zoom: Float,
        lockAspect: Boolean
    ): GraphViewport {
        val width = widthPx.coerceAtLeast(1f)
        val height = heightPx.coerceAtLeast(1f)
        val factor = zoom.coerceIn(0.5f, 2f)
        val currentY = visibleY(width, height, lockAspect)
        val nextX = (spanX / factor).coerceIn(1.25, 100.0)
        val nextY = if (lockAspect) nextX * height / width
            else (spanY / factor).coerceIn(.5, 100.0)
        val scaleX = nextX / width
        val scaleY = nextY / height
        return copy(
            centerX = centerX + (focalX - width / 2) *
                (spanX / width - scaleX) - panX * scaleX,
            centerY = centerY + (height / 2 - focalY) *
                (currentY / height - scaleY) + panY * scaleY,
            spanX = nextX,
            spanY = nextY
        )
    }
}
