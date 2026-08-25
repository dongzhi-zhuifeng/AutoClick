package com.Luofeng.autoclick

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