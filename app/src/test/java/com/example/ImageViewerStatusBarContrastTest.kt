package com.example

import com.example.ui.viewer.ImageViewerStatusBarContrast
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageViewerStatusBarContrastTest {
    @Test
    fun whitePortraitImageUsesDarkIcons() {
        assertTrue(contrast(1080, 2400, 1080, 2400) { _, _ -> 0xffffffff.toInt() })
    }

    @Test
    fun darkPortraitImageUsesLightIcons() {
        assertFalse(contrast(1080, 2400, 1080, 2400) { _, _ -> 0xff080d12.toInt() })
    }

    @Test
    fun letterboxedBrightImageStillUsesLightIconsOnDarkBackground() {
        assertFalse(contrast(1200, 1000, 1080, 2400) { _, _ -> 0xffffffff.toInt() })
    }

    @Test
    fun whiteImageWithDarkTopUsesLightIcons() {
        assertFalse(contrast(1080, 2400, 1080, 2400) { _, y ->
            if (y < 150) 0xff111111.toInt() else 0xffffffff.toInt()
        })
    }

    @Test
    fun invalidViewportNeverRequestsDarkIcons() {
        assertFalse(contrast(1080, 2400, 0, 2400) { _, _ -> 0xffffffff.toInt() })
    }

    private fun contrast(
        imageWidth: Int,
        imageHeight: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        pixelAt: (Int, Int) -> Int
    ) = ImageViewerStatusBarContrast.needsDarkIcons(
        imageWidth, imageHeight, viewportWidth, viewportHeight, 66, pixelAt
    )
}
