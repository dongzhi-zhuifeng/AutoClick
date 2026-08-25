package com.Luofeng.autoclick.domain

import com.Luofeng.autoclick.AppTiming

/** 项目列表与点位链的纯函数：展示文案、增删改、复制。 */
fun projectDisplayName(name: String): String = name.ifBlank { "项目" }

fun projectCardSummary(project: ClickProject): String {
    val base = "${project.steps.size} 个点位 · 重复 ${project.repeatCount} 次"
    if (project.delayOffsetMs == 0) return base
    val sign = if (project.delayOffsetMs > 0) "+" else ""
    return "$base · 延迟 $sign${project.delayOffsetMs}ms"
}

fun stepDelayChipLabel(index: Int, delayFromPrevMs: Long): String {
    return if (index == 0) "开始后立即" else "延迟 $delayFromPrevMs ms"
}

fun isStepDelayChipEditable(index: Int): Boolean = index > 0

fun stepPercentLabel(xRatio: Float, yRatio: Float): String {
    return "${(xRatio * 100).toInt()}%, ${(yRatio * 100).toInt()}%"
}

fun defaultAddStepDelayMs(project: ClickProject): Long {
    return project.steps.lastOrNull()?.delayFromPrevMs ?: 0L
}

fun shouldPromptAddStepDelay(project: ClickProject): Boolean = project.steps.isNotEmpty()

fun parseRequiredDelayMs(text: String): Long? {
    val value = text.toLongOrNull() ?: return null
    return if (value >= 0) value else null
}

fun pixelsToRatio(xPx: Float, yPx: Float, screenW: Int, screenH: Int): Pair<Float, Float> {
    val xRatio = if (screenW > 0) xPx / screenW else 0f
    val yRatio = if (screenH > 0) yPx / screenH else 0f
    return xRatio to yRatio
}

fun updateStepCoordinates(
    project: ClickProject,
    stepId: Long,
    xRatio: Float,
    yRatio: Float
): ClickProject {
    return project.copy(
        steps = project.steps.map { step ->
            if (step.id == stepId) step.copy(xRatio = xRatio, yRatio = yRatio) else step
        }
    )
}

fun updateStepDelay(project: ClickProject, stepId: Long, delayFromPrevMs: Long): ClickProject {
    return project.copy(
        steps = project.steps.map { step ->
            if (step.id == stepId) step.copy(delayFromPrevMs = delayFromPrevMs) else step
        }
    )
}

fun appendStep(
    project: ClickProject,
    xRatio: Float,
    yRatio: Float,
    delayFromPrevMs: Long,
    newStepId: Long
): ClickProject {
    val storedDelay = if (project.steps.isEmpty()) 0L else delayFromPrevMs
    return project.copy(
        steps = project.steps + ClickStep(newStepId, xRatio, yRatio, storedDelay)
    )
}

fun copyStepAt(project: ClickProject, index: Int, newStepId: Long): ClickProject {
    val source = project.steps.getOrNull(index) ?: return project
    val delay = if (index == 0) AppTiming.DEFAULT_INTERVAL_MS else source.delayFromPrevMs
    val steps = project.steps.toMutableList()
    steps.add(index + 1, source.copy(id = newStepId, delayFromPrevMs = delay))
    return project.copy(steps = steps)
}

fun deleteStepAt(project: ClickProject, index: Int): ClickProject {
    if (index !in project.steps.indices) return project
    val steps = project.steps.toMutableList().also { it.removeAt(index) }
    if (steps.isNotEmpty()) {
        steps[0] = steps[0].copy(delayFromPrevMs = 0)
    }
    return project.copy(
        steps = steps,
        enabled = if (steps.isEmpty()) false else project.enabled
    )
}

fun copyProject(project: ClickProject, nowMs: Long): ClickProject {
    return project.copy(
        id = nowMs,
        name = projectDisplayName(project.name) + " 副本",
        enabled = false,
        steps = project.steps.mapIndexed { i, step -> step.copy(id = nowMs + 1 + i) }
    )
}

fun insertProjectAfter(
    list: List<ClickProject>,
    afterId: Long,
    newProject: ClickProject
): List<ClickProject> {
    val idx = list.indexOfFirst { it.id == afterId }
    if (idx < 0) return list + newProject
    return list.toMutableList().also { it.add(idx + 1, newProject) }
}

fun replaceProject(list: List<ClickProject>, updated: ClickProject): List<ClickProject> {
    return list.map { if (it.id == updated.id) updated else it }
}

fun removeProject(list: List<ClickProject>, id: Long): List<ClickProject> {
    return list.filter { it.id != id }
}

fun createProject(
    nowMs: Long,
    xRatio: Float,
    yRatio: Float,
    hour: Int,
    minute: Int,
    second: Int,
    delayOffsetMs: Int,
    repeatCount: Int,
    repeatGapMs: Long,
    name: String
): ClickProject {
    return ClickProject(
        id = nowMs,
        name = name,
        hour = hour,
        minute = minute,
        second = second,
        delayOffsetMs = delayOffsetMs,
        repeatCount = repeatCount.coerceAtLeast(1),
        repeatGapMs = repeatGapMs.coerceAtLeast(1L),
        enabled = true,
        steps = listOf(ClickStep(nowMs, xRatio, yRatio, 0L))
    )
}

fun updateProjectSchedule(
    project: ClickProject,
    hour: Int,
    minute: Int,
    second: Int,
    delayOffsetMs: Int,
    repeatCount: Int,
    repeatGapMs: Long,
    name: String
): ClickProject {
    return project.copy(
        name = name,
        hour = hour,
        minute = minute,
        second = second,
        delayOffsetMs = delayOffsetMs,
        repeatCount = repeatCount.coerceAtLeast(1),
        repeatGapMs = repeatGapMs.coerceAtLeast(1L)
    )
}

fun applyEnabledToggle(project: ClickProject, enabled: Boolean): ClickProject {
    if (enabled && project.steps.isEmpty()) return project.copy(enabled = false)
    return project.copy(enabled = enabled)
}

fun renameProject(project: ClickProject, name: String): ClickProject = project.copy(name = name)
