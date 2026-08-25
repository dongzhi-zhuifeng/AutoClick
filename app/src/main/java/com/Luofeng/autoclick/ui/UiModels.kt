package com.Luofeng.autoclick.ui

import com.Luofeng.autoclick.domain.ClickProject
import com.Luofeng.autoclick.domain.ClickStep

/** 仅界面使用的临时状态：表单目标、待加点、待改延迟、点位菜单。 */
sealed class FormTarget {
    data class New(val x: Float, val y: Float) : FormTarget()
    data class Edit(val task: ClickProject) : FormTarget()
}

data class PendingAddStep(val projectId: Long, val x: Float, val y: Float)

data class PendingDelayEdit(val projectId: Long, val stepId: Long, val initialMs: Long)

data class StepMenuTarget(val projectId: Long, val step: ClickStep)
