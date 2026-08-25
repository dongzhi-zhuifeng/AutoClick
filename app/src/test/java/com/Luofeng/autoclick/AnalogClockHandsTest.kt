package com.Luofeng.autoclick

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalogClockHandsTest {
    @Test
    fun noon_allHandsAtTwelve() {
        val hands = AnalogClockHands.from(12, 0, 0)
        assertEquals(0.0, hands.hourDegrees, 0.001)
        assertEquals(0.0, hands.minuteDegrees, 0.001)
        assertEquals(0.0, hands.secondDegrees, 0.001)
    }

    @Test
    fun threeOClock_hourAtNinety() {
        val hands = AnalogClockHands.from(3, 0, 0)
        assertEquals(90.0, hands.hourDegrees, 0.001)
        assertEquals(0.0, hands.minuteDegrees, 0.001)
        assertEquals(0.0, hands.secondDegrees, 0.001)
    }

    @Test
    fun fifteenHours_sameAsThree() {
        val hands = AnalogClockHands.from(15, 0, 0)
        assertEquals(90.0, hands.hourDegrees, 0.001)
    }

    @Test
    fun halfPastTwelve_hourHalfwayToOne() {
        val hands = AnalogClockHands.from(12, 30, 0)
        assertEquals(15.0, hands.hourDegrees, 0.001)
        assertEquals(180.0, hands.minuteDegrees, 0.001)
    }

    @Test
    fun fifteenSeconds_secondHandAtNinety() {
        val hands = AnalogClockHands.from(0, 0, 15)
        assertEquals(90.0, hands.secondDegrees, 0.001)
        assertEquals(1.5, hands.minuteDegrees, 0.001)
        assertEquals(0.125, hands.hourDegrees, 0.001)
    }
}
