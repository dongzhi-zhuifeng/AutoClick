package com.Luofeng.autoclick.schedule

import com.Luofeng.autoclick.domain.flattenProject
import com.Luofeng.autoclick.domain.ClickDedupGuard
import com.Luofeng.autoclick.data.TaskRepository
import com.Luofeng.autoclick.click.ClickAccessibilityService
import com.Luofeng.autoclick.AppLog
import com.Luofeng.autoclick.ScreenUtils


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** 到点后读取最新项目并交给无障碍服务执行。 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1L)
        if (id < 0L) {
            AppLog.w("event=alarm_bad_id")
            return
        }

        val tasks = TaskRepository.loadTasks(context)
        val task = tasks.find { it.id == id }
        if (task == null) {
            AppLog.w("event=alarm_missing_project taskId=$id")
        }

        if (!ClickDedupGuard.tryMark(id)) {
            AppLog.d("event=alarm_skipped_dedup", "taskId=$id")
        } else if (task != null) {
            val screen = ScreenUtils.getRealScreenSize(context)
            val clicks = flattenProject(task)
            AppLog.d("event=alarm_triggered", "taskId=$id clicks=${clicks.size}")

            val service = ClickAccessibilityService.instance
            if (service == null) {
                Toast.makeText(context, "请先开启无障碍服务，否则无法自动点击", Toast.LENGTH_LONG).show()
            } else {
                service.performSequence(clicks, screen.x, screen.y)
            }
        }

        if (task != null && task.enabled) {
            TaskScheduler.scheduleTask(context, task)
        }
    }
}
