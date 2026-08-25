package com.Luofeng.autoclick

import kotlinx.coroutines.flow.MutableStateFlow

sealed class PickerPurpose {
    data object NewProject : PickerPurpose()
    data class AddStep(val projectId: Long) : PickerPurpose()
    data class EditStep(val projectId: Long, val stepId: Long) : PickerPurpose()
}

object PointPickerBus {
    data class PickedPoint(
        val x: Float,
        val y: Float,
        val purpose: PickerPurpose
    )
    val pickedPoint = MutableStateFlow<PickedPoint?>(null)
}
