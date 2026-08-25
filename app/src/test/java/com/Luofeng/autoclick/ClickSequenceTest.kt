package com.Luofeng.autoclick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickSequenceTest {
    private fun step(id: Long, x: Float, y: Float, delay: Long) =
        ClickStep(id, x, y, delay)

    @Test
    fun emptySteps_isEmpty() {
        val p = ClickProject(1, "", 8, 0, repeatCount = 3, repeatGapMs = 500, steps = emptyList())
        assertTrue(flattenProject(p).isEmpty())
    }

    @Test
    fun singlePoint_repeatThree_usesGapBetweenRounds() {
        val p = ClickProject(
            id = 1, name = "", hour = 8, minute = 0,
            repeatCount = 3, repeatGapMs = 500,
            steps = listOf(step(10, 0.5f, 0.5f, 0))
        )
        val out = flattenProject(p)
        assertEquals(3, out.size)
        assertEquals(0L, out[0].delayAfterPrevMs)
        assertEquals(500L, out[1].delayAfterPrevMs)
        assertEquals(500L, out[2].delayAfterPrevMs)
        assertEquals(0.5f, out[1].xRatio, 0.001f)
    }

    @Test
    fun firstRoundHead_storedDelayIgnored_laterAndGapUnchanged() {
        val p = ClickProject(
            id = 1, name = "", hour = 8, minute = 0,
            repeatCount = 2, repeatGapMs = 500,
            steps = listOf(step(1, 0.1f, 0.1f, 400), step(2, 0.2f, 0.2f, 350))
        )
        val out = flattenProject(p)
        assertEquals(4, out.size)
        assertEquals(listOf(0L, 350L, 500L, 350L), out.map { it.delayAfterPrevMs })
        assertEquals(0.1f, out[0].xRatio, 0.001f)
        assertEquals(0.2f, out[1].xRatio, 0.001f)
        assertEquals(0.1f, out[2].xRatio, 0.001f)
    }

    @Test
    fun twoPoints_twoRounds_insertsGapBeforeSecondRound() {
        val p = ClickProject(
            id = 1, name = "", hour = 8, minute = 0,
            repeatCount = 2, repeatGapMs = 500,
            steps = listOf(step(1, 0.1f, 0.1f, 0), step(2, 0.2f, 0.2f, 400))
        )
        val out = flattenProject(p)
        assertEquals(4, out.size)
        assertEquals(listOf(0L, 400L, 500L, 400L), out.map { it.delayAfterPrevMs })
        assertEquals(0.1f, out[2].xRatio, 0.001f)
        assertEquals(0.2f, out[3].xRatio, 0.001f)
    }

    @Test
    fun repeatCountZero_treatedAsOne() {
        val p = ClickProject(
            id = 1, name = "", hour = 8, minute = 0,
            repeatCount = 0, repeatGapMs = 500,
            steps = listOf(step(1, 0.3f, 0.4f, 0))
        )
        assertEquals(1, flattenProject(p).size)
    }

    @Test
    fun repeatPointSequence_firstDelayZero_restUsesInterval() {
        val out = repeatPointSequence(120f, 340f, 3, 500L)
        assertEquals(3, out.size)
        assertEquals(listOf(0L, 500L, 500L), out.map { it.delayAfterPrevMs })
        assertEquals(120f, out[0].xRatio, 0.001f)
        assertEquals(340f, out[2].yRatio, 0.001f)
    }

    @Test
    fun repeatPointSequence_countZero_isEmpty() {
        assertTrue(repeatPointSequence(1f, 2f, 0, 400L).isEmpty())
    }
}
