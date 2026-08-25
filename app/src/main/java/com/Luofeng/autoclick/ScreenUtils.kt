package com.Luofeng.autoclick

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager

/**
 * 屏幕适配工具类：统一获取设备真实物理分辨率（含状态栏、导航栏在内的完整屏幕区域），
 * 确保"选点"和"点击"两个环节使用完全一致的坐标系统，避免坐标偏移。
 */
object ScreenUtils {

    fun getRealScreenSize(context: Context): Point {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val point = Point()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            point.x = bounds.width()
            point.y = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(point)
        }
        return point
    }

    fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    fun applyDisplayCutout(params: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}
