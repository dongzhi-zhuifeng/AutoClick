package com.Luofeng.autoclick

import android.util.Log

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
