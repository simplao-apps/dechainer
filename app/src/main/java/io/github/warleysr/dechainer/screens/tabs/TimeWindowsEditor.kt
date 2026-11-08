package io.github.warleysr.dechainer.screens.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.models.TimeWindow
import io.github.warleysr.dechainer.models.TimeWindows
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Compact form for day chips: "8-18", "8-22", "2", or "—". */
fun shortWindowsSummary(windows: List<TimeWindow>): String = when {
    windows.isEmpty() -> "—"
    windows.size == 1 -> {
        val w = windows[0]
        val s = if (w.startMinute % 60 == 0) "${w.startMinute / 60}" else "%d:%02d".format(w.startMinute / 60, w.startMinute % 60)
        val e = if (w.endMinute % 60 == 0) "${w.endMinute / 60}" else "%d:%02d".format(w.endMinute / 60, w.endMinute % 60)
        val res = "$s-$e"
        if (res.length <= 7) res else "1"
    }
    else -> "${windows.size}"
}

/**
 * Short description of allowed time windows for buttons and list rows. [compact]
 * leaves out the "varies by day" note, for rows with little room.
 */
@Composable
fun timeWindowsSummary(windows: TimeWindows, compact: Boolean = false): String {
    if (!windows.isPerDay) {
        return windows.dailyWindows.joinToString(", ") { it.formatted() }
    }
    val today = windows.todayWindows()
    val todayFmt = today.joinToString(", ") { it.formatted() }
    return when {
        compact && today.isNotEmpty() -> stringResource(R.string.time_windows_today, todayFmt)
        compact -> stringResource(R.string.time_windows_none_today_short)
        today.isNotEmpty() -> stringResource(R.string.time_windows_varies_today, todayFmt)
        else -> stringResource(R.string.time_windows_none_today)
    }
}

class TimeWindowsEditorState(initial: TimeWindows) {
    var perDay by mutableStateOf(initial.isPerDay)
    val dailyWindows = mutableStateListOf(*initial.dailyWindows.toTypedArray())
    var selectedDay by mutableStateOf(LocalDate.now().dayOfWeek)

    /** Indexed by [DayOfWeek.ordinal]. */
    val weeklyWindows: List<SnapshotStateList<TimeWindow>> = DayOfWeek.entries.map { day ->
        mutableStateListOf(*initial.windowsFor(day).toTypedArray())
    }

    val currentWindows: SnapshotStateList<TimeWindow>
        get() = if (perDay) weeklyWindows[selectedDay.ordinal] else dailyWindows

    fun changePerDay(enabled: Boolean) {
        // Start the per-day editor from the daily windows, unless the days were already customized.
        if (enabled && weeklyWindows.distinctBy { it.toList() }.size <= 1) {
            weeklyWindows.forEach { dayList ->
                dayList.clear()
                dayList.addAll(dailyWindows)
            }
        }
        perDay = enabled
    }

    fun toTimeWindows(): TimeWindows =
        if (perDay) TimeWindows.perDay(weeklyWindows.map { it.toList() })
        else TimeWindows.daily(dailyWindows.toList())
}

@Composable
fun rememberTimeWindowsEditorState(initial: TimeWindows) = remember { TimeWindowsEditorState(initial) }

@Composable
fun TimeWindowsDialog(
    title: String,
    initialWindows: TimeWindows,
    onDismiss: () -> Unit,
    lockedWindows: Set<TimeWindow> = emptySet(),
    emptyMessage: String = stringResource(R.string.no_time_windows),
    allowPerDay: Boolean = true,
    onConfirm: (TimeWindows) -> Unit
) {
    val state = rememberTimeWindowsEditorState(initialWindows)
    val locale = Locale.getDefault()
    val orderedDays = remember(locale) {
        val first = WeekFields.of(locale).firstDayOfWeek
        (0L until 7L).map { first.plus(it) }
    }

    var showAddForm by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var formKey by remember { mutableIntStateOf(0) }
    var startHour by remember { mutableIntStateOf(18) }
    var startMinute by remember { mutableIntStateOf(0) }
    var endHour by remember { mutableIntStateOf(22) }
    var endMinute by remember { mutableIntStateOf(0) }

    fun resetForm() {
        startHour = 18; startMinute = 0; endHour = 22; endMinute = 0
        showError = false
        formKey++
    }

    fun commitPendingWindow(): Boolean {
        if (!showAddForm) return true
        val start = startHour * 60 + startMinute
        val end = endHour * 60 + endMinute
        if (start == end) {
            showError = true
            return false
        }
        val window = TimeWindow(start, end)
        val current = state.currentWindows
        if (window !in current) current.add(window)
        showAddForm = false
        resetForm()
        return true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (allowPerDay) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (commitPendingWindow()) state.changePerDay(!state.perDay)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.time_windows_per_day),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = state.perDay,
                            onCheckedChange = {
                                if (commitPendingWindow()) state.changePerDay(it)
                            }
                        )
                    }

                    if (state.perDay) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            orderedDays.forEach { day ->
                                DayChip(
                                    label = day.getDisplayName(TextStyle.SHORT, locale).trimEnd('.'),
                                    subtext = shortWindowsSummary(state.weeklyWindows[day.ordinal]),
                                    selected = day == state.selectedDay,
                                    onClick = {
                                        if (commitPendingWindow()) state.selectedDay = day
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val dayName = state.selectedDay.getDisplayName(TextStyle.FULL, locale)
                            .replaceFirstChar { it.titlecase(locale) }
                        Text(
                            dayName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                val current = state.currentWindows
                if (current.isEmpty() && !showAddForm) {
                    Text(emptyMessage, style = MaterialTheme.typography.bodySmall)
                }

                current.forEachIndexed { index, window ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(window.formatted(), modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { current.removeAt(index) },
                            enabled = window !in lockedWindows
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (showAddForm) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(
                                        R.string.new_time_window,
                                        TimeWindow(startHour * 60 + startMinute, endHour * 60 + endMinute).formatted()
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { showAddForm = false; resetForm() }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                                }
                            }
                            key(formKey) {
                                TimeOfDayWheels(
                                    label = stringResource(R.string.start_time),
                                    hour = startHour,
                                    minute = startMinute,
                                    onHourChange = { startHour = it; showError = false },
                                    onMinuteChange = { startMinute = it; showError = false }
                                )
                                Spacer(Modifier.height(12.dp))
                                TimeOfDayWheels(
                                    label = stringResource(R.string.end_time),
                                    hour = endHour,
                                    minute = endMinute,
                                    onHourChange = { endHour = it; showError = false },
                                    onMinuteChange = { endMinute = it; showError = false }
                                )
                            }
                            if (showError) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.invalid_time_window),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                TextButton(onClick = { if (commitPendingWindow()) showAddForm = true }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (showAddForm) R.string.add_another_time_window else R.string.add_time_window))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (commitPendingWindow()) onConfirm(state.toTimeWindows()) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun TimeWindowsDialog(
    title: String,
    initialWindows: List<TimeWindow>,
    onDismiss: () -> Unit,
    lockedWindows: Set<TimeWindow> = emptySet(),
    emptyMessage: String = stringResource(R.string.no_time_windows),
    allowPerDay: Boolean = false,
    onConfirm: (List<TimeWindow>) -> Unit
) {
    TimeWindowsDialog(
        title = title,
        initialWindows = TimeWindows.daily(initialWindows),
        onDismiss = onDismiss,
        lockedWindows = lockedWindows,
        emptyMessage = emptyMessage,
        allowPerDay = allowPerDay,
        onConfirm = { onConfirm(it.dailyWindows) }
    )
}
