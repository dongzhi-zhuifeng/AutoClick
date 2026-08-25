package com.Luofeng.autoclick

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object TaskRepository {
    private const val PREF_NAME = "click_tasks_pref"
    private const val KEY_TASKS = "tasks_json"

    fun loadTasks(context: Context): MutableList<ClickProject> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_TASKS, "[]") ?: "[]"
        val screen = ScreenUtils.getRealScreenSize(context)
        return parseTasksJson(json, screen.x, screen.y).toMutableList()
    }

    fun saveTasks(context: Context, tasks: List<ClickProject>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_TASKS, tasksToJson(tasks)) }
        AppLog.i("event=tasks_saved", "count=${tasks.size}")
    }

    internal fun tasksToJson(tasks: List<ClickProject>): String {
        val arr = JSONArray()
        tasks.forEach { t ->
            val o = JSONObject()
            o.put("id", t.id)
            o.put("name", t.name)
            o.put("hour", t.hour)
            o.put("minute", t.minute)
            o.put("second", t.second)
            o.put("millisecond", t.millisecond)
            o.put("delayOffsetMs", t.delayOffsetMs)
            o.put("repeatCount", t.repeatCount)
            o.put("repeatGapMs", t.repeatGapMs)
            o.put("enabled", t.enabled)
            val steps = JSONArray()
            t.steps.forEach { step ->
                val s = JSONObject()
                s.put("id", step.id)
                s.put("xRatio", step.xRatio.toDouble())
                s.put("yRatio", step.yRatio.toDouble())
                s.put("delayFromPrevMs", step.delayFromPrevMs)
                steps.put(s)
            }
            o.put("steps", steps)
            arr.put(o)
        }
        return arr.toString()
    }

    internal fun parseTasksJson(json: String, screenWidth: Int, screenHeight: Int): List<ClickProject> {
        val arr = try {
            JSONArray(json)
        } catch (e: JSONException) {
            AppLog.w("event=tasks_json_corrupt", e)
            return emptyList()
        }

        val list = mutableListOf<ClickProject>()
        for (i in 0 until arr.length()) {
            try {
                list.add(parseTaskObject(arr.getJSONObject(i), screenWidth, screenHeight))
            } catch (e: JSONException) {
                AppLog.w("event=tasks_json_skip_item index=$i", e)
            }
        }
        return list
    }

    private fun parseTaskObject(o: JSONObject, screenWidth: Int, screenHeight: Int): ClickProject {
        val rawSteps = if (o.has("steps")) o.getJSONArray("steps") else null
        val steps = if (rawSteps != null) {
            parseStepsArray(rawSteps)
        } else {
            listOf(legacySingleStep(o, screenWidth, screenHeight))
        }
        if (steps.isEmpty()) {
            val wellFormedEmpty = rawSteps != null && rawSteps.length() == 0
            if (!wellFormedEmpty) {
                throw JSONException("no valid steps")
            }
        }
        val repeatCount: Int
        val repeatGapMs: Long
        if (o.has("steps")) {
            repeatCount = o.getInt("repeatCount")
            repeatGapMs = o.getLong("repeatGapMs")
        } else {
            repeatCount = o.getInt("count")
            repeatGapMs = o.getLong("intervalMs")
        }
        return ClickProject(
            id = o.getLong("id"),
            name = o.optString("name", ""),
            hour = o.getInt("hour"),
            minute = o.getInt("minute"),
            second = o.optInt("second", 0),
            millisecond = o.optInt("millisecond", 0),
            delayOffsetMs = o.optInt("delayOffsetMs", 0),
            repeatCount = repeatCount,
            repeatGapMs = repeatGapMs,
            enabled = if (steps.isEmpty()) false else o.optBoolean("enabled", true),
            steps = steps
        )
    }

    private fun parseStepsArray(arr: JSONArray): List<ClickStep> {
        val steps = mutableListOf<ClickStep>()
        for (i in 0 until arr.length()) {
            try {
                val s = arr.getJSONObject(i)
                steps.add(
                    ClickStep(
                        id = s.getLong("id"),
                        xRatio = s.getDouble("xRatio").toFloat(),
                        yRatio = s.getDouble("yRatio").toFloat(),
                        delayFromPrevMs = s.getLong("delayFromPrevMs")
                    )
                )
            } catch (e: JSONException) {
                AppLog.w("event=tasks_json_skip_step index=$i", e)
            }
        }
        return steps
    }

    private fun legacySingleStep(o: JSONObject, screenWidth: Int, screenHeight: Int): ClickStep {
        val xRatio: Float
        val yRatio: Float
        if (o.has("xRatio") && o.has("yRatio")) {
            xRatio = o.getDouble("xRatio").toFloat()
            yRatio = o.getDouble("yRatio").toFloat()
        } else {
            val oldX = o.optDouble("x", 0.0).toFloat()
            val oldY = o.optDouble("y", 0.0).toFloat()
            xRatio = if (screenWidth > 0) oldX / screenWidth else 0f
            yRatio = if (screenHeight > 0) oldY / screenHeight else 0f
        }
        return ClickStep(
            id = o.getLong("id"),
            xRatio = xRatio,
            yRatio = yRatio,
            delayFromPrevMs = 0
        )
    }
}
