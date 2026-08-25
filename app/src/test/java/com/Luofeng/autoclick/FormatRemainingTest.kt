package com.Luofeng.autoclick

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatRemainingTest {
    @Test
    fun zeroOrNegative_isImminent() {
        assertEquals("即将执行", formatRemaining(0))
        assertEquals("即将执行", formatRemaining(-3))
    }

    @Test
    fun secondsOnly() {
        assertEquals("还有 9 秒", formatRemaining(9))
    }

    @Test
    fun minutesAndSeconds() {
        assertEquals("还有 2分05秒", formatRemaining(125))
    }

    @Test
    fun hoursAndMinutes_omitsSeconds() {
        assertEquals("还有 1时02分", formatRemaining(3725))
    }
}
