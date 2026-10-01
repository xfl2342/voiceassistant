package io.github.xfl2342.voiceassistant.ui.calendar

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import io.github.xfl2342.voiceassistant.data.CalendarData
import io.github.xfl2342.voiceassistant.data.EventRepository
import io.github.xfl2342.voiceassistant.domain.EventOccurrence
import io.github.xfl2342.voiceassistant.domain.RecurrenceExpander
import io.github.xfl2342.voiceassistant.reminder.ReminderPermissions
import io.github.xfl2342.voiceassistant.ui.components.EventColors
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
    /** 点某一天的某条行程：带上那一天的日期，重复行程才能知道用户指的是哪一次。 */
    onEventClick: (eventId: String, date: LocalDate) -> Unit,
    onOpenReminderSettings: () -> Unit,
    onOpenSettings: () -> Unit,
    onCreateClick: (LocalDate) -> Unit,
    onOpenList: () -> Unit,
    weekStartDay: DayOfWeek,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 每次进入日历都重新判断一次：从设置页回来时会重新组合，状态自然是最新的。
    val reminderReady = remember { ReminderPermissions.read(context).allGood }
    var visibleMonth by remember { mutableStateOf(YearMonth.now(zone)) }
    var selectedDay by remember { mutableStateOf(LocalDate.now(zone)) }
    /**
     * 月历是否收起成一周。
     *
     * 行程一多，六行月历加上「说一句话」的按钮，就把当天的日程列表挤得只剩一条缝。
     * 上滑把月历收成选中日所在的那一周，腾出来的地方全部让给列表。
     */
    var collapsedToWeek by remember { mutableStateOf(false) }

    val calendarData by repository
        .observeCalendar()
        .collectAsState(initial = CalendarData(emptyList(), emptyList()))

    // 月视图一次算一整屏（6 周）的行程；收起成一周时，要画的那一周也在这一屏里。
    val gridStart = visibleMonth.atDay(1).with(TemporalAdjusters.previousOrSame(weekStartDay))
    val gridEnd = gridStart.plusDays(GRID_DAYS - 1L)

    // 收起时只画选中日所在的那一周，展开时画整月；展开范围取两者的并集，
    // 这样无论翻月翻到哪儿，要画的那几周的行程都算得出来。
    val selectedWeekStart = selectedDay.with(TemporalAdjusters.previousOrSame(weekStartDay))
    val weeksToShow = monthGridWeeks(
        monthStart = visibleMonth.atDay(1),
        selectedDay = selectedDay,
        weekStartDay = weekStartDay,
        collapsed = collapsedToWeek,
    )
    val rangeStart = minOf(gridStart, weeksToShow.first())
    val rangeEnd = maxOf(gridEnd, weeksToShow.last().plusDays(DAYS_PER_WEEK - 1L))

    val occurrencesByDate = remember(calendarData, rangeStart, rangeEnd, weekStartDay) {
        RecurrenceExpander
            .expand(
                events = calendarData.events,
                rules = calendarData.rules,
                from = rangeStart,
                to = rangeEnd,
                zone = zone,
                weekStartDay = weekStartDay,
            )
            .groupBy { it.date }
    }

    val swipeThresholdPx = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }

    /** 收起状态下翻页翻的是一周，展开状态下才是翻月。 */
    fun pageBackward() {
        if (collapsedToWeek) {
            selectedDay = selectedDay.minusWeeks(1)
            visibleMonth = YearMonth.from(selectedDay)
        } else {
            visibleMonth = visibleMonth.minusMonths(1)
        }
    }

    fun pageForward() {
        if (collapsedToWeek) {
            selectedDay = selectedDay.plusWeeks(1)
            visibleMonth = YearMonth.from(selectedDay)
        } else {
            visibleMonth = visibleMonth.plusMonths(1)
        }
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
                title = if (collapsedToWeek) {
                    weekRangeLabel(selectedWeekStart)
                } else {
                    monthTitleFormatter.format(visibleMonth)
                },
                collapsedToWeek = collapsedToWeek,
                onToggleWeekMode = {
                    collapsedToWeek = !collapsedToWeek
                    // 收起时把月份也对准选中日，否则滑动窗口可能落在网格外面。
                    if (collapsedToWeek) visibleMonth = YearMonth.from(selectedDay)
                },
                onPrevious = { pageBackward() },
                onNext = { pageForward() },
                onOpenSettings = onOpenSettings,
                onToday = {
                    val today = LocalDate.now(zone)
                    visibleMonth = YearMonth.from(today)
                    selectedDay = today
                },
            )
            // 日历主体整体接横向手势：在月历上或下面的日程列表上左右滑都能翻，
            // 展开时翻一个月，收起成一周时翻一周。上面那行月份标题不参与，免得误触。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(swipeThresholdPx) {
                        // 和纵向一样，按整段手势的方向判断，滑过阈值才翻页。
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onDragEnd = {
                                when {
                                    // 手指往左滑，是把日历往前推：下一月 / 下一周。
                                    totalDrag <= -swipeThresholdPx -> pageForward()
                                    totalDrag >= swipeThresholdPx -> pageBackward()
                                }
                            },
                        ) { _, dragAmount -> totalDrag += dragAmount }
                    },
            ) {
                WeekdayHeader(weekStartDay)
                MonthGrid(
                    weeks = weeksToShow,
                    month = visibleMonth,
                    selectedDay = selectedDay,
                    occurrencesByDate = occurrencesByDate,
                    swipeThresholdPx = swipeThresholdPx,
                    onDayClick = { selectedDay = it },
                    onSwipeUp = { collapsedToWeek = true },
                    onSwipeDown = { collapsedToWeek = false },
                )
                HorizontalDivider()
                DayAgenda(
                    day = selectedDay,
                    occurrences = occurrencesByDate[selectedDay].orEmpty(),
                    onEventClick = onEventClick,
                    onCreateClick = onCreateClick,
                    onOpenList = onOpenList,
                    modifier = Modifier.weight(1f),
                )
            }
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
    title: String,
    collapsedToWeek: Boolean,
    onToggleWeekMode: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenSettings: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 标题本身就是「收起 / 展开月历」的开关，收起时显示的是这一周的日期范围。
        // 箭头的方向说的是「点下去会怎样」：展开着按下去收起，收起时按下去展开。
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable { onToggleWeekMode() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (collapsedToWeek) "⌄" else "⌃",
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onToday) { Text("今天") }
        TextButton(onClick = onPrevious) { Text("‹") }
        TextButton(onClick = onNext) { Text("›") }
        TextButton(onClick = onOpenSettings) { Text("设置") }
    }
}

@Composable
private fun WeekdayHeader(weekStartDay: DayOfWeek) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        (0 until DAYS_PER_WEEK).forEach { offset ->
            val label = WEEKDAY_LABELS[weekStartDay.plus(offset.toLong()).value - 1]
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
    weeks: List<LocalDate>,
    month: YearMonth,
    selectedDay: LocalDate,
    occurrencesByDate: Map<LocalDate, List<EventOccurrence>>,
    swipeThresholdPx: Float,
    onDayClick: (LocalDate) -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
) {
    val today = LocalDate.now(zone)

    // 只画该画的周：展开时六行，收起时一行。高度由内容自己决定，
    // 不去裁剪也没有位移，窗口里显示的永远正好是这几周。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(GRID_ANIMATION_MILLIS))
            .pointerInput(swipeThresholdPx) {
                // 上滑收起、下滑展开。按整段手势的方向判断，不看单次拖动的方向，
                // 免得手指抖一下就把月历收起来。
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        when {
                            totalDrag <= -swipeThresholdPx -> onSwipeUp()
                            totalDrag >= swipeThresholdPx -> onSwipeDown()
                        }
                    },
                ) { _, dragAmount -> totalDrag += dragAmount }
            }
            .padding(horizontal = 8.dp),
    ) {
        weeks.forEach { weekStart ->
            Row(
                modifier = Modifier.fillMaxWidth().height(WEEK_ROW_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(DAYS_PER_WEEK) { weekday ->
                    val date = weekStart.plusDays(weekday.toLong())
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
            .clickable { onClick() }
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
                    // 颜色表示急迫程度（紧急偏红、常规偏蓝、不急偏灰）；
                    // 深浅区分全天与定时——全天填得更实，一眼能看出它占一整天。
                    .background(
                        EventColors.accent(occurrence.urgency).copy(
                            alpha = if (occurrence.allDay) 0.32f else 0.16f,
                        ),
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
    onEventClick: (eventId: String, date: LocalDate) -> Unit,
    onCreateClick: (LocalDate) -> Unit,
    onOpenList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${dayTitleFormatter.format(day)} ${weekdayLabel(day.dayOfWeek)}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onOpenList) { Text("全部日程") }
            TextButton(onClick = { onCreateClick(day) }) { Text("＋ 新建") }
        }

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
                    .clickable { onEventClick(occurrence.eventId, occurrence.date) },
            ) {
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    // 左侧色条：这条行程有多急，一眼能扫出来。
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .fillMaxHeight()
                            .background(EventColors.accent(occurrence.urgency)),
                    )
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

/**
 * 月历要画出来的那几周的周首日期。
 *
 * 展开时固定六行：不管这个月是 28 天还是 31 天，翻月时高度都不跳。
 * 收起时只有选中日所在的那一周，行数由这里决定，画出来的就是看到的。
 */
internal fun monthGridWeeks(
    monthStart: LocalDate,
    selectedDay: LocalDate,
    weekStartDay: DayOfWeek,
    collapsed: Boolean,
): List<LocalDate> {
    val selectedWeekStart = selectedDay.with(TemporalAdjusters.previousOrSame(weekStartDay))
    if (collapsed) return listOf(selectedWeekStart)

    val gridStart = monthStart.with(TemporalAdjusters.previousOrSame(weekStartDay))
    return List(GRID_WEEKS) { gridStart.plusWeeks(it.toLong()) }
}

/** 收起成一周时标题上的日期范围，例如 `9/29 – 10/5`。 */
internal fun weekRangeLabel(weekStart: LocalDate): String {
    val end = weekStart.plusDays(DAYS_PER_WEEK - 1L)
    return "${weekStart.monthValue}/${weekStart.dayOfMonth} – " +
        "${end.monthValue}/${end.dayOfMonth}"
}

private const val DAYS_PER_WEEK = 7
private const val GRID_WEEKS = 6
private const val GRID_DAYS = DAYS_PER_WEEK * GRID_WEEKS
private const val MAX_LINES_PER_CELL = 2

/** 月历一行的固定高度；收起时窗口正好是这一行。 */
private val WEEK_ROW_HEIGHT = 72.dp

/** 一次手势要拖过这么多距离才认，横向纵向都一样，避免手抖误触。 */
private val SWIPE_THRESHOLD = 24.dp
private const val GRID_ANIMATION_MILLIS = 220

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
