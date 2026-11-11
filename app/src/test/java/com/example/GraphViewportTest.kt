package com.example

import com.example.calculator.graph.GraphViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphViewportTest {
    @Test fun dragMovesContentInDirectionOfFinger() {
        val initial = GraphViewport()
        val moved = initial.transformed(400f, 600f, 200f, 300f,
            panX = 70f, panY = 45f, zoom = 1f, lockAspect = false)
        assertTrue(moved.centerX < 0)
        assertTrue(moved.centerY > 0)
        assertEquals(-2.1, moved.centerX, 1e-8)
        assertEquals(0.375, moved.centerY, 1e-8)
    }

    @Test fun zoomAtFocalPointPreservesItsWorldCoordinates() {
        val initial = GraphViewport(centerX = 2.0, centerY = -1.0,
            spanX = 16.0, spanY = 6.0)
        val width = 400f
        val height = 600f
        val x = 310f
        val y = 110f
        val oldWorldX = initial.centerX + (x - width / 2) * initial.spanX / width
        val oldWorldY = initial.centerY + (height / 2 - y) * initial.spanY / height
        val scaled = initial.transformed(width, height, x, y,
            panX = 0f, panY = 0f, zoom = 1.5f, lockAspect = false)
        assertEquals(oldWorldX,
            scaled.centerX + (x - width / 2) * scaled.spanX / width, 1e-8)
        assertEquals(oldWorldY,
            scaled.centerY + (height / 2 - y) * scaled.spanY / height, 1e-8)
    }

    @Test fun polarPlotPreservesEqualScalesWhenZooming() {
        val viewport = GraphViewport().transformed(390f, 760f,
            200f, 400f, 0f, 0f, zoom = 1.8f, lockAspect = true)
        assertEquals(viewport.spanX / 390f,
            viewport.visibleY(390f, 760f, true) / 760f, 1e-7)
    }

    @Test fun extremePinchIsBounded() {
        var viewport = GraphViewport()
        repeat(100) {
            viewport = viewport.transformed(400f, 600f, 200f, 300f,
                0f, 0f, zoom = 2f, lockAspect = false)
        }
        assertEquals(1.25, viewport.spanX, 1e-8)
        assertEquals(.5, viewport.spanY, 1e-8)
        repeat(100) {
            viewport = viewport.transformed(400f, 600f, 200f, 300f,
                0f, 0f, zoom = .5f, lockAspect = false)
        }
        assertEquals(100.0, viewport.spanX, 1e-8)
        assertEquals(100.0, viewport.spanY, 1e-8)
    }
}
