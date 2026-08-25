package com.Luofeng.autoclick

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.util.Calendar

object TaskScheduler {

    private fun mainPendingIntent(context: Context, project: ClickProject): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("id", project.id)
        }
        return PendingIntent.getBroadcast(
            context, ("main_" + project.id).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun prePendingIntent(context: Context, project: ClickProject, targetTime: Long): PendingIntent {
        val intent = Intent(context, PreAlarmReceiver::class.java).apply {
            putExtra("id", project.id)
            putExtra("targetTime", targetTime)
        }
        return PendingIntent.getBroadcast(
            context, ("pre_" + project.id).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun baseTriggerMillis(task: ClickProject): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, task.hour)
            set(Calendar.MINUTE, task.minute)
            set(Calendar.SECOND, task.second)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }

    fun nextTriggerMillis(task: ClickProject): Long {
        return baseTriggerMillis(task) + task.delayOffsetMs
    }

    fun remainingSeconds(task: ClickProject): Long {
        val diff = nextTriggerMillis(task) - System.currentTimeMillis()
        return if (diff < 0) 0 else diff / 1000
    }

    fun scheduleTask(context: Context, task: ClickProject) {
        if (!task.enabled) {
            cancelTask(context, task)
            return
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextTriggerMillis(task)
        val now = System.currentTimeMillis()

        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, mainPendingIntent(context, task))
        } catch (t: SecurityException) {
            AppLog.e("event=schedule_exact_denied taskId=${task.id}", t)
        }

        val preTriggerAt = triggerAt - AppTiming.PRE_ALARM_LEAD_MS
        am.cancel(prePendingIntent(context, task, 0L))

        // 重新排期前先清除旧的去重标记，避免"重新打开任务后被误判为已触发过"
        ClickDedupGuard.clear(task.id)
        AppLog.i("event=schedule", "taskId=${task.id} triggerAt=$triggerAt")

        if (preTriggerAt > now) {
            // 关键修复：不再调用 stopIfTask 再重新 startCountdownNow 这种"先停后启"的写法，
            // 直接取消未来的预告闹钟即可，不需要碰正在显示的倒计时视图
            try {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, preTriggerAt,
                    prePendingIntent(context, task, triggerAt)
                )
            } catch (t: SecurityException) {
                AppLog.e("event=schedule_pre_exact_denied taskId=${task.id}", t)
            }
        } else if (triggerAt > now) {
            // 已经进入30秒倒计时窗口(常见于:任务暂停后又重新打开)，直接启动显示，
            // 内部会通过taskId自动覆盖同一任务的旧展示，不会出现闪烁消失的问题
            startCountdownNow(context, task, triggerAt)
        }
    }

    private fun startCountdownNow(context: Context, task: ClickProject, triggerAt: Long) {
        if (!Settings.canDrawOverlays(context)) return
        val intent = Intent(context, CountdownOverlayService::class.java).apply {
            putExtra("taskId", task.id)
            putExtra("targetTime", triggerAt)
        }
        try {
            context.startService(intent)
        } catch (t: IllegalStateException) {
            AppLog.e("event=countdown_start_denied taskId=${task.id}", t)
        }
    }

    fun cancelTask(context: Context, task: ClickProject) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(mainPendingIntent(context, task))
        am.cancel(prePendingIntent(context, task, 0L))
        CountdownOverlayService.stopIfTask(context, task.id)
        AppLog.i("event=cancel", "taskId=${task.id}")
    }

    fun rescheduleAll(context: Context) {
        val tasks = TaskRepository.loadTasks(context)
        tasks.filter { it.enabled }.forEach { scheduleTask(context, it) }
    }
}