package io.github.warleysr.dechainer.models

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Allowed usage windows. By default the same [dailyWindows] apply every day; when [weeklyWindows] is
 * set the allowed windows vary by weekday instead (indexed by [DayOfWeek.ordinal], Monday = 0).
 * An empty list means "no restriction" / anytime allowed for that day.
 */
data class TimeWindows(
    val dailyWindows: List<TimeWindow> = emptyList(),
    val weeklyWindows: List<List<TimeWindow>>? = null
) {
    val isPerDay: Boolean get() = weeklyWindows != null

    val isSet: Boolean get() = weeklyWindows?.any { it.isNotEmpty() } ?: dailyWindows.isNotEmpty()

    fun isNotEmpty(): Boolean = isSet
    fun isEmpty(): Boolean = !isSet

    fun windowsFor(day: DayOfWeek): List<TimeWindow> =
        weeklyWindows?.getOrNull(day.ordinal) ?: dailyWindows

    fun todayWindows(): List<TimeWindow> = windowsFor(LocalDate.now().dayOfWeek)

    companion object {
        val NONE = TimeWindows()

        fun daily(windows: List<TimeWindow>) = TimeWindows(dailyWindows = windows)

        fun perDay(windows: List<List<TimeWindow>>): TimeWindows {
            require(windows.size == DayOfWeek.entries.size) {
                "weeklyWindows must contain exactly ${DayOfWeek.entries.size} days"
            }
            return TimeWindows(weeklyWindows = windows)
        }

        fun intersect(a: TimeWindows, b: TimeWindows): TimeWindows {
            if (!a.isPerDay && !b.isPerDay) {
                return daily(TimeWindow.intersect(a.dailyWindows, b.dailyWindows))
            }
            val days = DayOfWeek.entries.map { day ->
                TimeWindow.intersect(a.windowsFor(day), b.windowsFor(day))
            }
            return perDay(days)
        }
    }
}
