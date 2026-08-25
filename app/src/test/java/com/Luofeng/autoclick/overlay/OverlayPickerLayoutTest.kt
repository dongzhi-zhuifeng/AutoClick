package com.Luofeng.autoclick.overlay

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPickerLayoutTest {
    @Test
    fun markerOrigin_centersOnPoint() {
        val (x, y) = overlayMarkerOrigin(centerX = 100f, centerY = 200f, sizePx = 24)
        assertEquals(88, x)
        assertEquals(188, y)
    }

    @Test
    fun markerCenter_roundTrips() {
        val size = 24
        val (ox, oy) = overlayMarkerOrigin(540f, 960f, size)
        val (cx, cy) = overlayMarkerCenter(ox, oy, size)
        assertEquals(540f, cx, 0.01f)
        assertEquals(960f, cy, 0.01f)
    }

    @Test
    fun backDown_consumedWithoutStop() {
        val decision = overlayBackKeyDecision(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN)
        assertTrue(decision.consume)
        assertFalse(decision.shouldStop)
    }

    @Test
    fun backUp_consumedAndStop() {
        val decision = overlayBackKeyDecision(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP)
        assertTrue(decision.consume)
        assertTrue(decision.shouldStop)
    }

    @Test
    fun otherKey_ignored() {
        val decision = overlayBackKeyDecision(KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_DOWN)
        assertFalse(decision.consume)
        assertFalse(decision.shouldStop)
    }

    @Test
    fun coordsOrigin_sitsTopRightOfMarker() {
        val (x, y) = overlayCoordsOrigin(
            markerCenterX = 100f,
            markerCenterY = 200f,
            markerSizePx = 24,
            coordsW = 80,
            coordsH = 32,
            gapPx = 4
        )
        assertEquals(108, x)
        assertEquals(152, y)
    }

    @Test
    fun confirmOrigin_sitsCenteredBelowMarker() {
        val (x, y) = overlayConfirmOrigin(
            markerCenterX = 100f,
            markerCenterY = 200f,
            markerSizePx = 24,
            confirmW = 120,
            confirmH = 40,
            gapPx = 4
        )
        assertEquals(40, x)
        assertEquals(216, y)
    }

    @Test
    fun clampOrigin_keepsHudOnScreen() {
        val (x, y) = overlayClampOrigin(
            x = -10,
            y = -4,
            width = 80,
            height = 32,
            screenW = 1080,
            screenH = 1920
        )
        assertEquals(0, x)
        assertEquals(0, y)
        val (x2, y2) = overlayClampOrigin(
            x = 1040,
            y = 1900,
            width = 80,
            height = 40,
            screenW = 1080,
            screenH = 1920
        )
        assertEquals(1000, x2)
        assertEquals(1880, y2)
    }
}
