package com.Luofeng.autoclick.schedule

import com.Luofeng.autoclick.data.TaskRepository
import com.Luofeng.autoclick.overlay.CountdownOverlayService
import com.Luofeng.autoclick.AppLog


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** 到点前启动倒计时浮层。 */
class PreAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Settings.canDrawOverlays(context)) return

        val id = intent.getLongExtra("id", -1L)
        if (id < 0L) {
            AppLog.w("event=pre_alarm_bad_id")
            return
        }
        val targetTime = intent.getLongExtra("targetTime", System.currentTimeMillis())

        val task = TaskRepository.loadTasks(context).find { it.id == id }
        if (task == null) {
            AppLog.w("event=pre_alarm_missing_project taskId=$id")
            return
        }
        if (task.steps.isEmpty()) {
            AppLog.w("event=pre_alarm_empty_steps taskId=$id")
            return
        }

        val serviceIntent = Intent(context, CountdownOverlayService::class.java).apply {
            putExtra("taskId", id)
            putExtra("targetTime", targetTime)
        }
        try {
            context.startService(serviceIntent)
        } catch (t: IllegalStateException) {
            AppLog.e("event=pre_alarm_start_denied taskId=$id", t)
        }
    }
}
