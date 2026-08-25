package com.Luofeng.autoclick



/** Timing constants frozen at current production values. Do not change the numbers. */
object AppTiming {
    const val PRE_ALARM_LEAD_MS = 30_000L
    const val DEDUP_WINDOW_MS = 2_000L
    const val TEST_CLICK_DELAY_MS = 3_000L
    const val TEST_CLICK_INTERVAL_MS = 500L
    const val GESTURE_STROKE_DURATION_MS = 200L
    const val RIPPLE_SIZE_PX = 80
    const val RIPPLE_REMOVE_DELAY_MS = 500L
    const val COUNTDOWN_TICK_MS = 50L
    const val COUNTDOWN_FAST_TICK_MS = 5L
    const val COUNTDOWN_FAST_THRESHOLD_MS = 200L
    const val DEFAULT_INTERVAL_MS = 500L
    const val INTENT_DEFAULT_INTERVAL_MS = 1000L
    const val DELAY_OFFSET_MIN = -1000
    const val DELAY_OFFSET_MAX = 1000
    const val UI_TICK_MS = 1_000L
}
