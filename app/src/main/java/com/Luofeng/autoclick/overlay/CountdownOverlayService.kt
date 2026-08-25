package com.Luofeng.autoclick.overlay

import com.Luofeng.autoclick.R
import com.Luofeng.autoclick.domain.flattenProject
import com.Luofeng.autoclick.domain.ClickDedupGuard
import com.Luofeng.autoclick.data.TaskRepository
import com.Luofeng.autoclick.click.ClickAccessibilityService
import com.Luofeng.autoclick.AppLog
import com.Luofeng.autoclick.AppTiming
import com.Luofeng.autoclick.ScreenUtils


import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/** 到点前的倒计时药丸与第一点标记。 */
class CountdownOverlayService : Service() {

    companion object {
        private const val ACTION_STOP_TASK = "com.Luofeng.autoclick.STOP_COUNTDOWN_TASK"

        /** 关闭指定任务的倒计时显示(如果正在显示的话)，不影响其他任务的倒计时 */
        fun stopIfTask(context: Context, taskId: Long) {
            val intent = Intent(context, CountdownOverlayService::class.java).apply {
                action = ACTION_STOP_TASK
                putExtra("taskId", taskId)
            }
            context.startService(intent)
        }
    }

    private lateinit var windowManager: WindowManager

    // 关键修复：用Map管理任意数量的任务倒计时，每个任务拥有独立的视图和定时器，互不覆盖
    private val overlays = mutableMapOf<Long, TaskOverlay>()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_TASK) {
            val id = intent.getLongExtra("taskId", -1L)
            removeOverlay(id)
            if (overlays.isEmpty()) stopSelf()
            return START_NOT_STICKY
        }

        val taskId = intent?.getLongExtra("taskId", -1L) ?: -1L
        val targetTime = intent?.getLongExtra("targetTime", System.currentTimeMillis())
            ?: System.currentTimeMillis()

        val project = TaskRepository.loadTasks(this).find { it.id == taskId }
        val first = project?.steps?.firstOrNull()
        if (project == null || first == null) {
            AppLog.w("event=countdown_missing_project taskId=$taskId")
            if (overlays.isEmpty()) stopSelf()
            return START_NOT_STICKY
        }

        val screen = ScreenUtils.getRealScreenSize(this)
        val x = first.xRatio * screen.x
        val y = first.yRatio * screen.y

        // 同一任务重新排期时，先清理旧的展示，避免残留
        removeOverlay(taskId)

        val overlay = TaskOverlay(taskId, x, y, targetTime)
        overlays[taskId] = overlay
        overlay.show()

        return START_NOT_STICKY
    }

    private fun removeOverlay(taskId: Long) {
        overlays.remove(taskId)?.destroy()
    }

    override fun onDestroy() {
        overlays.values.forEach { it.destroy() }
        overlays.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 每个任务独立的倒计时悬浮展示单元：独立视图、独立Handler，互不干扰 */
    private inner class TaskOverlay(
        private val taskId: Long,
        private val targetX: Float,
        private val targetY: Float,
        private val targetTime: Long
    ) {
        private var markerView: View? = null
        private var pillView: View? = null
        private var pillParams: WindowManager.LayoutParams? = null
        private var markerSizePx: Int = 0

        private val countdownHandler = Handler(Looper.getMainLooper())
        private val blinkHandler = Handler(Looper.getMainLooper())

        private var remainingSecondsCache: Long = 999L
        private var markerBright = true
        private var hasTriggeredClick = false

        fun show() {
            markerSizePx = resources.getDimension(R.dimen.countdown_marker_size).toInt()

            markerView = LayoutInflater.from(this@CountdownOverlayService)
                .inflate(R.layout.countdown_marker, null)
            val markerParams = buildOverlayParams(markerSizePx, markerSizePx).apply {
                x = (targetX - markerSizePx / 2).toInt()
                y = (targetY - markerSizePx / 2).toInt()
            }
            windowManager.addView(markerView, markerParams)
            markerBright = true
            markerView?.alpha = 1f

            pillView = LayoutInflater.from(this@CountdownOverlayService)
                .inflate(R.layout.countdown_pill, null)
            val initialRemainMs = (targetTime - System.currentTimeMillis()).coerceAtLeast(0)
            pillView?.findViewById<TextView>(R.id.tvCountdown)?.text = formatCountdownText(initialRemainMs)

            // 关键修复：先隐藏，等测量出真实宽度、纠正到精确居中位置之后，再显示出来，避免视觉上的位置跳动
            pillView?.visibility = View.INVISIBLE

            pillParams = buildOverlayParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            ).apply {
                x = (targetX - 80).toInt()
                y = (targetY - 100).toInt()
            }
            windowManager.addView(pillView, pillParams)

            recenterPillThenReveal()
            startCountdownLoop()
            startBlinkLoop()
        }

        private fun buildOverlayParams(width: Int, height: Int): WindowManager.LayoutParams {
            return WindowManager.LayoutParams(
                width, height,
                ScreenUtils.overlayWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                ScreenUtils.applyDisplayCutout(this)
            }
        }

        private fun formatCountdownText(remainMs: Long): String {
            val seconds = remainMs / 1000
            val millis = remainMs % 1000
            return "即将自动点击：%02d.%03ds".format(seconds, millis)
        }

        private fun recenterPillThenReveal() {
            val pv = pillView ?: return
            pv.post {
                val width = pv.width
                val height = pv.height
                if (width > 0 && height > 0) {
                    pillParams?.x = (targetX - width / 2f).toInt()
                    pillParams?.y = (targetY - markerSizePx / 2f - height - 16).toInt()
                    try {
                        windowManager.updateViewLayout(pv, pillParams)
                    } catch (e: Exception) {
                        AppLog.w("event=countdown_pill_layout_failed", e)
                    }
                }
                pv.visibility = View.VISIBLE
            }
        }

        private fun startCountdownLoop() {
            val task = object : Runnable {
                override fun run() {
                    val remainMs = targetTime - System.currentTimeMillis()
                    if (remainMs <= 0) {
                        triggerClickImmediately()
                        finishAndRemove()
                        return
                    }
                    remainingSecondsCache = remainMs / 1000
                    pillView?.findViewById<TextView>(R.id.tvCountdown)?.text = formatCountdownText(remainMs)
                    val nextDelay = if (remainMs < AppTiming.COUNTDOWN_FAST_THRESHOLD_MS) {
                        AppTiming.COUNTDOWN_FAST_TICK_MS
                    } else {
                        AppTiming.COUNTDOWN_TICK_MS
                    }
                    countdownHandler.postDelayed(this, nextDelay)
                }
            }
            countdownHandler.post(task)
        }

        private fun triggerClickImmediately() {
            if (hasTriggeredClick) return
            hasTriggeredClick = true

            if (!ClickDedupGuard.tryMark(taskId)) return

            val project = TaskRepository.loadTasks(this@CountdownOverlayService).find { it.id == taskId }
            if (project == null) {
                AppLog.w("event=countdown_fire_missing_project taskId=$taskId")
                return
            }
            val screen = ScreenUtils.getRealScreenSize(this@CountdownOverlayService)
            val service = ClickAccessibilityService.instance
            service?.performSequence(flattenProject(project), screen.x, screen.y)
        }

        private fun startBlinkLoop() {
            val blinkTask = object : Runnable {
                override fun run() {
                    val marker = markerView ?: return
                    markerBright = !markerBright
                    marker.alpha = if (markerBright) 1f else 0.25f

                    val blinksPerSecond = when {
                        remainingSecondsCache <= 5 -> 3
                        remainingSecondsCache <= 10 -> 2
                        else -> 1
                    }
                    val halfPeriod = 500L / blinksPerSecond
                    blinkHandler.postDelayed(this, halfPeriod)
                }
            }
            blinkHandler.post(blinkTask)
        }

        private fun finishAndRemove() {
            destroy()
            overlays.remove(taskId)
            if (overlays.isEmpty()) stopSelf()
        }

        fun destroy() {
            countdownHandler.removeCallbacksAndMessages(null)
            blinkHandler.removeCallbacksAndMessages(null)
            pillView?.let {
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {
                    AppLog.w("event=countdown_pill_remove_failed", e)
                }
            }
            markerView?.let {
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {
                    AppLog.w("event=countdown_marker_remove_failed", e)
                }
            }
            pillView = null
            markerView = null
        }
    }
}