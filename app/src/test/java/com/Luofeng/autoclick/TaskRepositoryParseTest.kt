package com.Luofeng.autoclick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRepositoryParseTest {
    @Test
    fun roundTrip_preservesFields() {
        val t = ClickProject(
            id = 1L,
            name = "demo",
            hour = 8,
            minute = 30,
            second = 15,
            millisecond = 0,
            delayOffsetMs = 100,
            repeatCount = 3,
            repeatGapMs = 500L,
            enabled = true,
            steps = listOf(ClickStep(10L, 0.5f, 0.25f, 0L))
        )
        val json = TaskRepository.tasksToJson(listOf(t))
        val back = TaskRepository.parseTasksJson(json, 1080, 1920)
        assertEquals(1, back.size)
        assertEquals(t, back[0])
    }

    @Test
    fun newStepsJson_roundTrip_preservesStepIdAndDelay() {
        val t = ClickProject(
            id = 2L,
            name = "seq",
            hour = 9,
            minute = 1,
            repeatCount = 2,
            repeatGapMs = 250L,
            steps = listOf(
                ClickStep(41L, 0.1f, 0.2f, 0L),
                ClickStep(42L, 0.3f, 0.7f, 400L)
            )
        )
        val json = TaskRepository.tasksToJson(listOf(t))
        val back = TaskRepository.parseTasksJson(json, 1080, 1920)
        assertEquals(2, back[0].steps.size)
        assertEquals(42L, back[0].steps[1].id)
        assertEquals(400L, back[0].steps[1].delayFromPrevMs)
    }

    @Test
    fun legacyXy_convertsToRatio() {
        val json = """[{"id":1,"x":540,"y":960,"hour":1,"minute":2,"intervalMs":500,"count":1}]"""
        val back = TaskRepository.parseTasksJson(json, 1080, 1920)
        assertEquals(1, back[0].steps.size)
        assertEquals(0L, back[0].steps[0].delayFromPrevMs)
        assertEquals(1, back[0].repeatCount)
        assertEquals(500L, back[0].repeatGapMs)
        assertEquals("", back[0].name)
        assertEquals(0.5f, back[0].steps[0].xRatio, 0.001f)
        assertEquals(0.5f, back[0].steps[0].yRatio, 0.001f)
    }

    @Test
    fun corruptJson_returnsEmpty_withoutThrowing() {
        val back = TaskRepository.parseTasksJson("{not-json", 1080, 1920)
        assertTrue(back.isEmpty())
    }

    @Test
    fun oneBadItem_skipsIt_keepsGood() {
        val json = """[{"id":1,"xRatio":0.1,"yRatio":0.2,"hour":1,"minute":0,"intervalMs":1,"count":1},{"id":"bad"}]"""
        val back = TaskRepository.parseTasksJson(json, 1080, 1920)
        assertEquals(1, back.size)
        assertEquals(1L, back[0].id)
    }

    @Test
    fun projectWithNoValidSteps_isSkipped() {
        val json = """[{"id":1,"name":"","hour":1,"minute":0,"repeatCount":1,"repeatGapMs":500,"steps":[{"id":"bad"}]},{"id":2,"name":"ok","hour":2,"minute":0,"repeatCount":1,"repeatGapMs":500,"steps":[{"id":3,"xRatio":0.1,"yRatio":0.2,"delayFromPrevMs":0}]}]"""
        val back = TaskRepository.parseTasksJson(json, 1080, 1920)
        assertEquals(1, back.size)
        assertEquals(2L, back[0].id)
    }

    @Test
    fun emptySteps_roundTrip_keepsProjectDisabled() {
        val t = ClickProject(
            id = 7L,
            name = "empty",
            hour = 10,
            minute = 20,
            second = 5,
            millisecond = 0,
            delayOffsetMs = 0,
            repeatCount = 2,
            repeatGapMs = 500L,
            enabled = false,
            steps = emptyList()
        )
        val json = TaskRepository.tasksToJson(listOf(t))
        val back = TaskRepository.parseTasksJson(json, 1080, 1920)
        assertEquals(1, back.size)
        assertEquals(7L, back[0].id)
        assertEquals("empty", back[0].name)
        assertEquals(10, back[0].hour)
        assertEquals(20, back[0].minute)
        assertEquals(5, back[0].second)
        assertEquals(2, back[0].repeatCount)
        assertEquals(500L, back[0].repeatGapMs)
        assertTrue(back[0].steps.isEmpty())
        assertFalse(back[0].enabled)
    }

    @Test
    fun emptyStepsJson_enabledTrue_isKeptAndForcedDisabled() {
        val json = """[{"id":8,"name":"kept","hour":3,"minute":4,"second":0,"repeatCount":1,"repeatGapMs":500,"enabled":true,"steps":[]}]"""
        val back = TaskRepository.parseTasksJson(json, 1080, 1920)
        assertEquals(1, back.size)
        assertEquals(8L, back[0].id)
        assertEquals("kept", back[0].name)
        assertTrue(back[0].steps.isEmpty())
        assertFalse(back[0].enabled)
    }
}
