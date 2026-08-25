package com.Luofeng.autoclick.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机后恢复所有启用项目的闹钟。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            TaskScheduler.rescheduleAll(context)
        }
    }
}