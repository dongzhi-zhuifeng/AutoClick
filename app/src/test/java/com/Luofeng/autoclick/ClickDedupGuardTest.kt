package com.Luofeng.autoclick

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickDedupGuardTest {
    @Test
    fun firstMarkSucceeds_secondWithinWindowFails() {
        ClickDedupGuard.clear(42L)
        assertTrue(ClickDedupGuard.tryMark(42L, windowMs = 2_000L))
        assertFalse(ClickDedupGuard.tryMark(42L, windowMs = 2_000L))
        ClickDedupGuard.clear(42L)
        assertTrue(ClickDedupGuard.tryMark(42L, windowMs = 2_000L))
    }
}
