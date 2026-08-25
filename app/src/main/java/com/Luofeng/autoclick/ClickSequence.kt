package com.Luofeng.autoclick

fun flattenProject(project: ClickProject): List<ScheduledClick> {
    if (project.steps.isEmpty()) return emptyList()
    val rounds = project.repeatCount.coerceAtLeast(1)
    val out = ArrayList<ScheduledClick>(rounds * project.steps.size)
    repeat(rounds) { round ->
        project.steps.forEachIndexed { index, step ->
            val delay = when {
                index > 0 -> step.delayFromPrevMs
                round == 0 -> 0L
                else -> project.repeatGapMs
            }
            out.add(ScheduledClick(step.xRatio, step.yRatio, delay))
        }
    }
    return out
}

fun repeatPointSequence(x: Float, y: Float, count: Int, intervalMs: Long): List<ScheduledClick> {
    if (count <= 0) return emptyList()
    return List(count) { i ->
        ScheduledClick(x, y, if (i == 0) 0L else intervalMs)
    }
}
