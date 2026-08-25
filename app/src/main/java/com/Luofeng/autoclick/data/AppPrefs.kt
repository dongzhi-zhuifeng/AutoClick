package com.Luofeng.autoclick.data

import android.content.Context
import androidx.core.content.edit

/** 界面偏好：模拟时钟开关等。 */
object AppPrefs {
    private const val PREF = "app_prefs"

    fun isFloatingButtonEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean("floating_button", false)

    fun setFloatingButtonEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit { putBoolean("floating_button", enabled) }
    }

    fun isAnalogClockEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean("analog_clock", false)

    fun setAnalogClockEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit { putBoolean("analog_clock", enabled) }
    }
}