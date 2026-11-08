package io.github.warleysr.dechainer.models

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.warleysr.dechainer.data.AppTimeWindows
import io.github.warleysr.dechainer.screens.common.isLooserWindows
import io.github.warleysr.dechainer.screens.tabs.shortWindowsSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek

@RunWith(RobolectricTestRunner::class)
class TimeWindowsTest {

    @Test
    fun `default TimeWindows is not set and has empty windows`() {
        val tw = TimeWindows.NONE
        assertFalse(tw.isSet)
        assertFalse(tw.isPerDay)
        assertTrue(tw.isEmpty())
        assertFalse(tw.isNotEmpty())
        assertTrue(tw.dailyWindows.isEmpty())
        assertNull(tw.weeklyWindows)
        assertTrue(tw.windowsFor(DayOfWeek.MONDAY).isEmpty())
        assertTrue(tw.todayWindows().isEmpty())
    }

    @Test
    fun `daily TimeWindows returns same windows for every day`() {
        val window = TimeWindow(8 * 60, 18 * 60)
        val tw = TimeWindows.daily(listOf(window))

        assertTrue(tw.isSet)
        assertFalse(tw.isPerDay)
        assertEquals(listOf(window), tw.dailyWindows)
        assertNull(tw.weeklyWindows)

        for (day in DayOfWeek.entries) {
            assertEquals(listOf(window), tw.windowsFor(day))
        }
    }

    @Test
    fun `perDay TimeWindows returns different windows for weekdays and weekends`() {
        val weekdayWindow = TimeWindow(8 * 60, 18 * 60)
        val weekendWindow = TimeWindow(8 * 60, 22 * 60)

        val weekly = DayOfWeek.entries.map { day ->
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                listOf(weekendWindow)
            } else {
                listOf(weekdayWindow)
            }
        }

        val tw = TimeWindows.perDay(weekly)
        assertTrue(tw.isSet)
        assertTrue(tw.isPerDay)
        assertNotNull(tw.weeklyWindows)

        assertEquals(listOf(weekdayWindow), tw.windowsFor(DayOfWeek.MONDAY))
        assertEquals(listOf(weekdayWindow), tw.windowsFor(DayOfWeek.FRIDAY))
        assertEquals(listOf(weekendWindow), tw.windowsFor(DayOfWeek.SATURDAY))
        assertEquals(listOf(weekendWindow), tw.windowsFor(DayOfWeek.SUNDAY))
    }

    @Test
    fun `TimeWindows intersect combines daily and per-day schedules correctly`() {
        // Group allows 8:00 - 20:00 every day
        val groupWindows = TimeWindows.daily(listOf(TimeWindow(8 * 60, 20 * 60)))
        // App allows 8:00 - 18:00 on weekdays, 8:00 - 22:00 on weekends
        val appWindows = TimeWindows.perDay(DayOfWeek.entries.map { day ->
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                listOf(TimeWindow(8 * 60, 22 * 60))
            } else {
                listOf(TimeWindow(8 * 60, 18 * 60))
            }
        })

        val intersected = TimeWindows.intersect(groupWindows, appWindows)
        assertTrue(intersected.isPerDay)

        // Monday: overlap of [8:00-20:00] and [8:00-18:00] -> [8:00-18:00]
        val mon = intersected.windowsFor(DayOfWeek.MONDAY)
        assertEquals(1, mon.size)
        assertEquals(8 * 60, mon[0].startMinute)
        assertEquals(18 * 60, mon[0].endMinute)

        // Saturday: overlap of [8:00-20:00] and [8:00-22:00] -> [8:00-20:00]
        val sat = intersected.windowsFor(DayOfWeek.SATURDAY)
        assertEquals(1, sat.size)
        assertEquals(8 * 60, sat[0].startMinute)
        assertEquals(20 * 60, sat[0].endMinute)
    }

    @Test
    fun `AppTimeWindows read and write handles daily and weekly persistence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dailyPrefs = context.getSharedPreferences("test_daily_windows", Context.MODE_PRIVATE)
        val weeklyPrefs = context.getSharedPreferences("test_weekly_windows", Context.MODE_PRIVATE)

        val pkg = "com.example.app"

        // 1. Initial read is empty
        val initial = AppTimeWindows.read(dailyPrefs, weeklyPrefs, pkg)
        assertTrue(initial.isEmpty())

        // 2. Write daily windows
        val dailyTw = TimeWindows.daily(listOf(TimeWindow(8 * 60, 18 * 60)))
        AppTimeWindows.write(dailyPrefs, weeklyPrefs, pkg, dailyTw)

        val readDaily = AppTimeWindows.read(dailyPrefs, weeklyPrefs, pkg)
        assertFalse(readDaily.isPerDay)
        assertEquals(1, readDaily.dailyWindows.size)
        assertEquals(8 * 60, readDaily.dailyWindows[0].startMinute)
        assertEquals(18 * 60, readDaily.dailyWindows[0].endMinute)
        assertNull(weeklyPrefs.getString(pkg, null))

        // 3. Write weekly windows (should remove from dailyPrefs and store in weeklyPrefs)
        val weeklyTw = TimeWindows.perDay(DayOfWeek.entries.map { day ->
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                listOf(TimeWindow(8 * 60, 22 * 60))
            } else {
                listOf(TimeWindow(8 * 60, 18 * 60))
            }
        })
        AppTimeWindows.write(dailyPrefs, weeklyPrefs, pkg, weeklyTw)

        assertNull(dailyPrefs.getString(pkg, null))
        assertNotNull(weeklyPrefs.getString(pkg, null))

        val readWeekly = AppTimeWindows.read(dailyPrefs, weeklyPrefs, pkg)
        assertTrue(readWeekly.isPerDay)
        assertEquals(18 * 60, readWeekly.windowsFor(DayOfWeek.MONDAY)[0].endMinute)
        assertEquals(22 * 60, readWeekly.windowsFor(DayOfWeek.SATURDAY)[0].endMinute)

        // 4. Clear windows
        AppTimeWindows.write(dailyPrefs, weeklyPrefs, pkg, TimeWindows.NONE)
        assertNull(dailyPrefs.getString(pkg, null))
        assertNull(weeklyPrefs.getString(pkg, null))
        assertTrue(AppTimeWindows.read(dailyPrefs, weeklyPrefs, pkg).isEmpty())
    }

    @Test
    fun `isLooserWindows detects loosening across days`() {
        val window8to18 = TimeWindow(8 * 60, 18 * 60)
        val window8to22 = TimeWindow(8 * 60, 22 * 60)
        val window8to12 = TimeWindow(8 * 60, 12 * 60)

        val originalDaily = TimeWindows.daily(listOf(window8to18))

        // Loosened on weekends (8-22 vs 8-18)
        val loosenedWeekend = TimeWindows.perDay(DayOfWeek.entries.map { day ->
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) listOf(window8to22)
            else listOf(window8to18)
        })
        assertTrue(isLooserWindows(originalDaily, loosenedWeekend))

        // Tightened on weekends (8-12 vs 8-18)
        val tightenedWeekend = TimeWindows.perDay(DayOfWeek.entries.map { day ->
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) listOf(window8to12)
            else listOf(window8to18)
        })
        assertFalse(isLooserWindows(originalDaily, tightenedWeekend))

        // Removing all windows is loosening (unrestricted usage allowed)
        assertTrue(isLooserWindows(originalDaily, TimeWindows.NONE))

        // Adding windows from unrestricted is tightening
        assertFalse(isLooserWindows(TimeWindows.NONE, originalDaily))
    }

    @Test
    fun `shortWindowsSummary formats concise chip labels`() {
        assertEquals("—", shortWindowsSummary(emptyList()))
        assertEquals("8-18", shortWindowsSummary(listOf(TimeWindow(8 * 60, 18 * 60))))
        assertEquals("8-22", shortWindowsSummary(listOf(TimeWindow(8 * 60, 22 * 60))))
        assertEquals("2", shortWindowsSummary(listOf(
            TimeWindow(8 * 60, 12 * 60),
            TimeWindow(14 * 60, 18 * 60)
        )))
    }
}
