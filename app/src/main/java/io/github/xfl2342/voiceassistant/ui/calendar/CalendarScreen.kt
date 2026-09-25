package io.github.xfl2342.voiceassistant.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import io.github.xfl2342.voiceassistant.data.CalendarData
import io.github.xfl2342.voiceassistant.data.EventRepository
import io.github.xfl2342.voiceassistant.domain.EventOccurrence
import io.github.xfl2342.voiceassistant.domain.RecurrenceExpander
import io.github.xfl2342.voiceassistant.reminder.ReminderPermissions
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val monthTitleFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy 年 M 月")
private val dayTitleFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M 月 d 日")

/**
 * 日历主页：月视图 + 当日行程列表。
 *
 * 重复行程不预先展开入库，这里按当前显示的月份现算，所以翻月时看到的是实时结果。
 */
@Composable
fun CalendarScreen(
    repository: EventRepository,
    onRecordClick: () -> Unit,
    onEventClick: (String) -> Unit,
    onOpenReminderSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 每次进入日历都重新判断一次：从设置页回来时会重新组合，状态自然是最新的。
    val reminderReady = remember { ReminderPermissions.read(context).allGood }
    var visibleMonth by remember { mutableStateOf(YearMonth.now(zone)) }
    var selectedDay by remember { mutableStateOf(LocalDate.now(zone)) }

    val calendarData by repository
        .observeCalendar()
        .collectAsState(initial = CalendarData(emptyList(), emptyList()))

    // 一次算出一整屏（6 周）的行程，翻月时只需要重算这一块。
    val gridStart = visibleMonth.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val gridEnd = gridStart.plusDays(GRID_DAYS - 1L)

    val occurrencesByDate = remember(calendarData, gridStart) {
        RecurrenceExpander
            .expand(calendarData.events, calendarData.rules, gridStart, gridEnd, zone)
            .groupBy { it.date }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!reminderReady) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable { onOpenReminderSettings() },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = "提醒可能不准时：还有系统设置没开启，点这里处理",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            MonthHeader(
                month = visibleMonth,
                onPrevious = { visibleMonth = visibleMonth.minusMonths(1) },
                onNext = { visibleMonth = visibleMonth.plusMonths(1) },
                onToday = {
                    val today = LocalDate.now(zone)
                    visibleMonth = YearMonth.from(today)
                    selectedDay = today
                },
            )
            WeekdayHeader()
            MonthGrid(
                gridStart = gridStart,
                month = visibleMonth,
                selectedDay = selectedDay,
                occurrencesByDate = occurrencesByDate,
                onDayClick = { selectedDay = it },
            )
            HorizontalDivider()
            DayAgenda(
                day = selectedDay,
                occurrences = occurrencesByDate[selectedDay].orEmpty(),
                onEventClick = onEventClick,
                modifier = Modifier.weight(1f),
            )
        }

        ExtendedFloatingActionButton(
            onClick = onRecordClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Text("说一句话")
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = monthTitleFormatter.format(month),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        TextButton(onClick = onToday) { Text("今天") }
        TextButton(onClick = onPrevious) { Text("‹") }
        TextButton(onClick = onNext) { Text("›") }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        WEEKDAY_LABELS.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    gridStart: LocalDate,
    month: YearMonth,
    selectedDay: LocalDate,
    occurrencesByDate: Map<LocalDate, List<EventOccurrence>>,
    onDayClick: (LocalDate) -> Unit,
) {
    val today = LocalDate.now(zone)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        repeat(GRID_WEEKS) { week ->
            Row(
                modifier = Modifier.fillMaxWidth().height(72.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(DAYS_PER_WEEK) { weekday ->
                    val date = gridStart.plusDays((week * DAYS_PER_WEEK + weekday).toLong())
                    DayCell(
                        date = date,
                        inCurrentMonth = YearMonth.from(date) == month,
                        isToday = date == today,
                        isSelected = date == selectedDay,
                        occurrences = occurrencesByDate[date].orEmpty(),
                        onClick = { onDayClick(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inCurrentMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    occurrences: List<EventOccurrence>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isToday -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val dayNumberColor = when {
        !inCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = dayNumberColor,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )

        // 全天行程排在前面，用实心色条表示，不显示时刻。
        val visible = occurrences.take(MAX_LINES_PER_CELL)
        visible.forEach { occurrence ->
            Text(
                text = occurrence.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (occurrence.allDay) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                        }
                    )
                    .padding(horizontal = 3.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (occurrences.size > MAX_LINES_PER_CELL) {
            Text(
                text = "+${occurrences.size - MAX_LINES_PER_CELL}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayAgenda(
    day: LocalDate,
    occurrences: List<EventOccurrence>,
    onEventClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${dayTitleFormatter.format(day)} ${weekdayLabel(day.dayOfWeek)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        if (occurrences.isEmpty()) {
            Text(
                text = "这天没有行程",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        occurrences.forEach { occurrence ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEventClick(occurrence.eventId) },
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = occurrence.title,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = occurrenceTimeText(occurrence),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    occurrence.location?.takeIf { it.isNotBlank() }?.let { location ->
                        Text(
                            text = location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun occurrenceTimeText(occurrence: EventOccurrence): String {
    if (occurrence.allDay) return "全天"
    val start = occurrence.startAt ?: return "时间未知"
    val end = occurrence.endAt
    val startText = timeFormatter.format(start.atZone(zone))
    if (end == null) return startText
    return "$startText - ${timeFormatter.format(end.atZone(zone))}"
}

private fun weekdayLabel(dayOfWeek: DayOfWeek): String = "周" + WEEKDAY_LABELS[dayOfWeek.value - 1]

private const val DAYS_PER_WEEK = 7
private const val GRID_WEEKS = 6
private const val GRID_DAYS = DAYS_PER_WEEK * GRID_WEEKS
private const val MAX_LINES_PER_CELL = 2

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
