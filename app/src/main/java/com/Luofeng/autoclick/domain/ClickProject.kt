package com.Luofeng.autoclick.domain



/** 点击项目领域模型：项目、点位、展开后的计划点击。 */
data class ClickStep(
    val id: Long,
    val xRatio: Float,
    val yRatio: Float,
    val delayFromPrevMs: Long
)

data class ClickProject(
    val id: Long,
    val name: String,
    val hour: Int,
    val minute: Int,
    val second: Int = 0,
    val millisecond: Int = 0,
    val delayOffsetMs: Int = 0,
    val repeatCount: Int,
    val repeatGapMs: Long,
    val enabled: Boolean = true,
    val steps: List<ClickStep>
)

data class ScheduledClick(
    val xRatio: Float,
    val yRatio: Float,
    val delayAfterPrevMs: Long
)
