package com.Luofeng.autoclick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectListLogicTest {

    private fun project(
        id: Long = 10L,
        name: String = "",
        delayOffsetMs: Int = 0,
        repeatCount: Int = 2,
        enabled: Boolean = true,
        steps: List<ClickStep> = listOf(ClickStep(11L, 0.5f, 0.25f, 0L))
    ) = ClickProject(
        id = id,
        name = name,
        hour = 8,
        minute = 30,
        second = 0,
        delayOffsetMs = delayOffsetMs,
        repeatCount = repeatCount,
        repeatGapMs = 500L,
        enabled = enabled,
        steps = steps
    )

    @Test
    fun collapsedSummary_countsStepsAndRepeats() {
        val p = project(repeatCount = 3, steps = listOf(
            ClickStep(1, 0.1f, 0.1f, 0L),
            ClickStep(2, 0.2f, 0.2f, 400L)
        ))
        assertEquals("2 个点位 · 重复 3 次", projectCardSummary(p))
    }

    @Test
    fun collapsedSummary_appendsDelayOffsetWhenNonZero() {
        val plus = project(delayOffsetMs = 200)
        val minus = project(delayOffsetMs = -80)
        assertEquals("1 个点位 · 重复 2 次 · 延迟 +200ms", projectCardSummary(plus))
        assertEquals("1 个点位 · 重复 2 次 · 延迟 -80ms", projectCardSummary(minus))
    }

    @Test
    fun firstStepChip_isImmediateAndNotEditable() {
        assertEquals("开始后立即", stepDelayChipLabel(0, 0L))
        assertFalse(isStepDelayChipEditable(0))
        assertTrue(isStepDelayChipEditable(1))
        assertEquals("延迟 400 ms", stepDelayChipLabel(1, 400L))
    }

    @Test
    fun stepPercentLabel_usesIntegerPercents() {
        assertEquals("50%, 25%", stepPercentLabel(0.5f, 0.25f))
    }

    @Test
    fun displayName_defaultsToProject() {
        assertEquals("项目", projectDisplayName(""))
        assertEquals("晨跑", projectDisplayName("晨跑"))
    }

    @Test
    fun defaultAddStepDelay_emptyIsZero_nonEmptyUsesLast() {
        assertEquals(0L, defaultAddStepDelayMs(project(steps = emptyList())))
        assertEquals(0L, defaultAddStepDelayMs(project(steps = listOf(ClickStep(1, 0.1f, 0.1f, 0L)))))
        val two = project(steps = listOf(
            ClickStep(1, 0.1f, 0.1f, 0L),
            ClickStep(2, 0.2f, 0.2f, 350L)
        ))
        assertEquals(350L, defaultAddStepDelayMs(two))
    }

    @Test
    fun shouldPromptAddStepDelay_emptyFalse_nonEmptyTrue() {
        assertFalse(shouldPromptAddStepDelay(project(steps = emptyList())))
        assertTrue(shouldPromptAddStepDelay(project()))
    }

    @Test
    fun appendStep_ontoEmpty_storesDelayZeroEvenIfCallerPasses500() {
        val empty = project(steps = emptyList(), enabled = false)
        val next = appendStep(empty, 0.3f, 0.4f, 500L, 99L)
        assertEquals(1, next.steps.size)
        assertEquals(99L, next.steps[0].id)
        assertEquals(0L, next.steps[0].delayFromPrevMs)
        assertEquals(0.3f, next.steps[0].xRatio, 0.0001f)
    }

    @Test
    fun parseRequiredDelay_rejectsBlankAndNegative() {
        assertNull(parseRequiredDelayMs(""))
        assertNull(parseRequiredDelayMs("abc"))
        assertNull(parseRequiredDelayMs("-1"))
        assertEquals(0L, parseRequiredDelayMs("0"))
        assertEquals(500L, parseRequiredDelayMs("500"))
    }

    @Test
    fun pixelsToRatio_dividesByScreen() {
        val (x, y) = pixelsToRatio(540f, 960f, 1080, 1920)
        assertEquals(0.5f, x, 0.0001f)
        assertEquals(0.5f, y, 0.0001f)
    }

    @Test
    fun updateStepCoordinates_keepsDelayAndOtherSteps() {
        val p = project(steps = listOf(
            ClickStep(1, 0.1f, 0.1f, 0L),
            ClickStep(2, 0.2f, 0.2f, 400L)
        ))
        val updated = updateStepCoordinates(p, 2L, 0.8f, 0.9f)
        assertEquals(0.1f, updated.steps[0].xRatio, 0.0001f)
        assertEquals(0.8f, updated.steps[1].xRatio, 0.0001f)
        assertEquals(0.9f, updated.steps[1].yRatio, 0.0001f)
        assertEquals(400L, updated.steps[1].delayFromPrevMs)
        assertEquals(2, updated.steps.size)
    }

    @Test
    fun appendStep_addsTailWithNewIdAndDelay() {
        val p = project()
        val next = appendStep(p, 0.3f, 0.4f, 220L, 99L)
        assertEquals(2, next.steps.size)
        assertEquals(99L, next.steps[1].id)
        assertEquals(220L, next.steps[1].delayFromPrevMs)
        assertEquals(0.3f, next.steps[1].xRatio, 0.0001f)
    }

    @Test
    fun updateStepDelay_changesOnlyThatStep() {
        val p = project(steps = listOf(
            ClickStep(1, 0.1f, 0.1f, 0L),
            ClickStep(2, 0.2f, 0.2f, 400L)
        ))
        val updated = updateStepDelay(p, 2L, 880L)
        assertEquals(0L, updated.steps[0].delayFromPrevMs)
        assertEquals(880L, updated.steps[1].delayFromPrevMs)
    }

    @Test
    fun copyStep_insertsCloneBelowWithNewId() {
        val p = project(steps = listOf(
            ClickStep(1, 0.1f, 0.1f, 0L),
            ClickStep(2, 0.2f, 0.2f, 400L)
        ))
        val copied = copyStepAt(p, 0, 77L)
        assertEquals(3, copied.steps.size)
        assertEquals(77L, copied.steps[1].id)
        assertEquals(0.1f, copied.steps[1].xRatio, 0.0001f)
        assertEquals(0.1f, copied.steps[1].yRatio, 0.0001f)
        assertEquals(AppTiming.DEFAULT_INTERVAL_MS, copied.steps[1].delayFromPrevMs)
        assertEquals(2L, copied.steps[2].id)
    }

    @Test
    fun copyStep_nonHead_preservesCoordinatesAndDelay() {
        val p = project(steps = listOf(
            ClickStep(1, 0.1f, 0.1f, 0L),
            ClickStep(2, 0.2f, 0.3f, 400L)
        ))
        val copied = copyStepAt(p, 1, 88L)
        assertEquals(3, copied.steps.size)
        assertEquals(88L, copied.steps[2].id)
        assertEquals(0.2f, copied.steps[2].xRatio, 0.0001f)
        assertEquals(0.3f, copied.steps[2].yRatio, 0.0001f)
        assertEquals(400L, copied.steps[2].delayFromPrevMs)
        assertEquals(400L, copied.steps[1].delayFromPrevMs)
    }

    @Test
    fun deleteLastStep_disablesProject() {
        val p = project(enabled = true)
        val emptied = deleteStepAt(p, 0)
        assertTrue(emptied.steps.isEmpty())
        assertFalse(emptied.enabled)
    }

    @Test
    fun deleteFirstStep_zerosNewHeadDelayAndKeepsEnabled() {
        val p = project(
            enabled = true,
            steps = listOf(
                ClickStep(1, 0.1f, 0.1f, 0L),
                ClickStep(2, 0.2f, 0.2f, 400L)
            )
        )
        val remaining = deleteStepAt(p, 0)
        assertEquals(1, remaining.steps.size)
        assertEquals(2L, remaining.steps[0].id)
        assertEquals(0L, remaining.steps[0].delayFromPrevMs)
        assertTrue(remaining.enabled)
    }

    @Test
    fun copyProject_newIdsDisabledAndNamedCopy() {
        val p = project(
            id = 10L,
            name = "",
            enabled = true,
            steps = listOf(ClickStep(1, 0.1f, 0.1f, 0L), ClickStep(2, 0.2f, 0.2f, 400L))
        )
        val copy = copyProject(p, nowMs = 5000L)
        assertEquals(5000L, copy.id)
        assertEquals("项目 副本", copy.name)
        assertFalse(copy.enabled)
        assertEquals(2, copy.steps.size)
        assertTrue(copy.steps.none { step -> p.steps.any { it.id == step.id } })
        assertEquals(0.2f, copy.steps[1].xRatio, 0.0001f)
        assertEquals(400L, copy.steps[1].delayFromPrevMs)
    }

    @Test
    fun copyProject_keepsCustomNameSuffix() {
        val copy = copyProject(project(name = "打卡"), nowMs = 1L)
        assertEquals("打卡 副本", copy.name)
    }

    @Test
    fun insertAfter_placesCopyBelowSource() {
        val a = project(id = 1L)
        val b = project(id = 2L)
        val copy = a.copy(id = 9L)
        val list = insertProjectAfter(listOf(a, b), afterId = 1L, newProject = copy)
        assertEquals(listOf(1L, 9L, 2L), list.map { it.id })
    }

    @Test
    fun createProject_firstStepDelayZeroAndEnabled() {
        val created = createProject(
            nowMs = 42L,
            xRatio = 0.4f,
            yRatio = 0.6f,
            hour = 7,
            minute = 8,
            second = 9,
            delayOffsetMs = 10,
            repeatCount = 1,
            repeatGapMs = 500L,
            name = ""
        )
        assertEquals(42L, created.id)
        assertTrue(created.enabled)
        assertEquals(1, created.steps.size)
        assertEquals(0L, created.steps[0].delayFromPrevMs)
        assertEquals(42L, created.steps[0].id)
        assertEquals(1, created.repeatCount)
        assertEquals(500L, created.repeatGapMs)
    }

    @Test
    fun updateProjectSchedule_preservesNameAndSteps() {
        val p = project(name = "保留", steps = listOf(ClickStep(1, 0.1f, 0.1f, 0L)))
        val updated = updateProjectSchedule(
            project = p,
            hour = 9,
            minute = 10,
            second = 11,
            delayOffsetMs = -20,
            repeatCount = 4,
            repeatGapMs = 800L,
            name = "保留"
        )
        assertEquals("保留", updated.name)
        assertEquals(9, updated.hour)
        assertEquals(1, updated.steps.size)
        assertEquals(0.1f, updated.steps[0].xRatio, 0.0001f)
    }

    @Test
    fun applyEnabledToggle_emptyStepsStayDisabled() {
        val empty = project(enabled = false, steps = emptyList())
        assertFalse(applyEnabledToggle(empty, true).enabled)
        val filled = project(enabled = false)
        assertTrue(applyEnabledToggle(filled, true).enabled)
    }

    @Test
    fun renameProject_updatesNameOnly() {
        val renamed = renameProject(project(name = ""), "新名字")
        assertEquals("新名字", renamed.name)
        assertEquals(8, renamed.hour)
    }
}
