package com.Luofeng.autoclick.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import com.Luofeng.autoclick.overlay.OverlayPointPickerService

/** 检查悬浮窗权限并启动选点服务。 */
fun startPointPicker(
    context: Context,
    purpose: String,
    projectId: Long? = null,
    stepId: Long? = null,
    initialX: Float? = null,
    initialY: Float? = null
) {
    if (!Settings.canDrawOverlays(context)) {
        Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
        )
        return
    }
    Toast.makeText(context, "可切换到目标App后再选点", Toast.LENGTH_LONG).show()
    context.startService(
        Intent(context, OverlayPointPickerService::class.java).apply {
            putExtra("purpose", purpose)
            if (projectId != null) putExtra("projectId", projectId)
            if (stepId != null) putExtra("stepId", stepId)
            if (initialX != null) putExtra("initialX", initialX)
            if (initialY != null) putExtra("initialY", initialY)
        }
    )
}
