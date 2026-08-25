package com.Luofeng.autoclick.domain

import com.Luofeng.autoclick.AppTiming

/** 同一项目在短窗口内只触发一次，避免闹钟与倒计时重复点击。 */
object ClickDedupGuard {
    private val lastTriggered = mutableMapOf<Long, Long>()

    @Synchronized
    fun tryMark(taskId: Long, windowMs: Long = AppTiming.DEDUP_WINDOW_MS): Boolean {
        val now = System.currentTimeMillis()
        val last = lastTriggered[taskId]
        return if (last == null || now - last > windowMs) {
            lastTriggered[taskId] = now
            true
        } else {
            false
        }
    }

    @Synchronized
    fun clear(taskId: Long) {
        lastTriggered.remove(taskId)
    }
}