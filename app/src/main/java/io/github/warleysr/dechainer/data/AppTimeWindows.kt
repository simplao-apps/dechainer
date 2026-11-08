package io.github.warleysr.dechainer.data

import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.warleysr.dechainer.models.TimeWindow
import io.github.warleysr.dechainer.models.TimeWindows
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

object AppTimeWindows {
    const val PREFS_NAME = "app_time_windows"
    const val WEEKLY_PREFS_NAME = "app_weekly_time_windows"

    fun decode(json: String?): List<TimeWindow> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                TimeWindow(obj.getInt("s"), obj.getInt("e"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encode(windows: List<TimeWindow>): String = encodeList(windows).toString()

    fun encodeList(windows: List<TimeWindow>): JSONArray {
        val array = JSONArray()
        windows.forEach { window ->
            array.put(JSONObject().apply {
                put("s", window.startMinute)
                put("e", window.endMinute)
            })
        }
        return array
    }

    fun decodeWeekly(json: String?): List<List<TimeWindow>>? {
        if (json.isNullOrEmpty()) return null
        return try {
            decodeWeekly(JSONArray(json))
        } catch (_: Exception) {
            null
        }
    }

    fun decodeWeekly(array: JSONArray?): List<List<TimeWindow>>? {
        if (array == null || array.length() != DayOfWeek.entries.size) return null
        return try {
            (0 until array.length()).map { i ->
                val dayArray = array.getJSONArray(i)
                (0 until dayArray.length()).map { j ->
                    val obj = dayArray.getJSONObject(j)
                    TimeWindow(obj.getInt("s"), obj.getInt("e"))
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun encodeWeekly(weeklyWindows: List<List<TimeWindow>>): JSONArray {
        val array = JSONArray()
        weeklyWindows.forEach { dayWindows ->
            array.put(encodeList(dayWindows))
        }
        return array
    }

    fun read(daily: SharedPreferences, weekly: SharedPreferences, packageName: String): TimeWindows {
        decodeWeekly(weekly.getString(packageName, null))?.let { return TimeWindows.perDay(it) }
        return TimeWindows.daily(decode(daily.getString(packageName, null)))
    }

    fun readAll(daily: SharedPreferences, weekly: SharedPreferences): Map<String, TimeWindows> {
        val result = mutableMapOf<String, TimeWindows>()
        daily.all.forEach { (pkg, value) ->
            (value as? String)?.let { decode(it) }?.takeIf { it.isNotEmpty() }?.let { result[pkg] = TimeWindows.daily(it) }
        }
        weekly.all.forEach { (pkg, value) ->
            decodeWeekly(value as? String)?.let { result[pkg] = TimeWindows.perDay(it) }
        }
        return result.filterValues { it.isSet }
    }

    fun write(daily: SharedPreferences, weekly: SharedPreferences, packageName: String, windows: TimeWindows) {
        val perDay = windows.weeklyWindows?.takeIf { windows.isSet }
        if (perDay != null) {
            daily.edit { remove(packageName) }
            weekly.edit { putString(packageName, encodeWeekly(perDay).toString()) }
        } else {
            weekly.edit { remove(packageName) }
            daily.edit {
                if (windows.dailyWindows.isNotEmpty()) {
                    putString(packageName, encode(windows.dailyWindows))
                } else {
                    remove(packageName)
                }
            }
        }
    }
}
