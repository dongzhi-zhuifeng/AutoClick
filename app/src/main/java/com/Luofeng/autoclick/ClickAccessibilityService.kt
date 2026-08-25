package com.Luofeng.autoclick

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.AnimationUtils

class ClickAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ClickAccessibilityService? = null
        // 水波纹视觉反馈开关，方便随时关闭，不影响真实点击功能
        var rippleEffectEnabled = false
    }

    private lateinit var windowManager: WindowManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        AppLog.d("event=a11y_connected")
    }

    override fun onDestroy() {
        instance = null
        AppLog.d("event=a11y_destroyed")
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun performClicks(x: Float, y: Float, count: Int, intervalMs: Long) {
        AppLog.d("event=perform_clicks", "x=$x y=$y count=$count intervalMs=$intervalMs")
        performSequence(repeatPointSequence(x, y, count, intervalMs), 1, 1)
    }

    fun performSequence(clicks: List<ScheduledClick>, screenWidth: Int, screenHeight: Int) {
        AppLog.d("event=perform_sequence", "size=${clicks.size} screen=${screenWidth}x${screenHeight}")
        val handler = Handler(Looper.getMainLooper())
        var elapsed = 0L
        for (click in clicks) {
            elapsed += click.delayAfterPrevMs
            val x = click.xRatio * screenWidth
            val y = click.yRatio * screenHeight
            handler.postDelayed({
                clickAt(x, y)
                AppLog.d("event=click_dispatched", "at ($x, $y)")
            }, elapsed)
        }
    }

    /** 核心点击逻辑：先尝试节点点击，找不到节点再用手势点击兜底；点击后展示水波纹反馈 */
    private fun clickAt(x: Float, y: Float) {
        val clickedByNode = tryClickByNode(x, y)
        if (!clickedByNode) {
            AppLog.d("event=click_gesture_fallback")
            dispatchClickGesture(x, y)
        } else {
            AppLog.d("event=click_node_success")
            if (rippleEffectEnabled) showRippleEffect(x, y)
        }
    }

    private fun tryClickByNode(x: Float, y: Float): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = findClickableNodeAt(root, x, y)
        return if (target != null) {
            val result = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            AppLog.d("event=node_click_result", "result=$result")
            result
        } else {
            false
        }
    }

    private fun findClickableNodeAt(node: AccessibilityNodeInfo, x: Float, y: Float): AccessibilityNodeInfo? {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (!rect.contains(x.toInt(), y.toInt())) return null

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findClickableNodeAt(child, x, y)
            if (found != null) return found
        }

        return if (node.isClickable && node.isVisibleToUser) node else null
    }

    private fun dispatchClickGesture(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 1, y + 1)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, AppTiming.GESTURE_STROKE_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val result = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                AppLog.d("event=gesture_completed")
                if (rippleEffectEnabled) showRippleEffect(x, y)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                AppLog.d("event=gesture_cancelled")
            }
        }, null)
        AppLog.d("event=gesture_submit", "result=$result")
    }

    /**
     * 在点击完成之后展示一个短暂的水波纹扩散动画，纯视觉反馈，
     * 使用 FLAG_NOT_TOUCHABLE 确保不会拦截或干扰任何触摸事件
     */
    private fun showRippleEffect(x: Float, y: Float) {
        try {
            val rippleView = View(this).apply {
                setBackgroundResource(R.drawable.ripple_circle_shape)
            }
            val size = AppTiming.RIPPLE_SIZE_PX
            val params = WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or   // 关键修复：坐标系对齐屏幕物理左上角，消除偏移
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = (x - size / 2).toInt()
                this.y = (y - size / 2).toInt()
            }
            windowManager.addView(rippleView, params)

            val anim = AnimationUtils.loadAnimation(this, R.anim.ripple_expand)
            rippleView.startAnimation(anim)

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    windowManager.removeView(rippleView)
                } catch (e: Exception) {
                    AppLog.w("event=ripple_remove_failed", e)
                }
            }, AppTiming.RIPPLE_REMOVE_DELAY_MS)
        } catch (e: Exception) {
            AppLog.e("event=ripple_failed", e)
        }
    }
}