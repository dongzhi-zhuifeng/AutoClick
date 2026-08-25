package com.Luofeng.autoclick

import android.util.Log

/** 统一日志，事件名用 event= 前缀方便过滤。 */
object AppLog {
    const val TAG = "AutoClick"

    fun i(event: String, details: String = "") {
        Log.i(TAG, if (details.isEmpty()) event else "$event $details")
    }

    fun w(event: String, t: Throwable? = null) {
        if (t == null) Log.w(TAG, event) else Log.w(TAG, event, t)
    }

    fun e(event: String, t: Throwable? = null) {
        if (t == null) Log.e(TAG, event) else Log.e(TAG, event, t)
    }

    fun d(event: String, details: String = "") {
        Log.d(TAG, if (details.isEmpty()) event else "$event $details")
    }
}
