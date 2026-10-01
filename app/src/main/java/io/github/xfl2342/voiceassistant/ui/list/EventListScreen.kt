package io.github.xfl2342.voiceassistant.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.xfl2342.voiceassistant.data.CalendarData
import io.github.xfl2342.voiceassistant.data.EventRepository
import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.data.db.RecurrenceRuleEntity
import io.github.xfl2342.voiceassistant.ui.components.EventColors
import io.github.xfl2342.voiceassistant.ui.components.EventFormat
import kotlinx.coroutines.launch

/**
 * 全部日程列表。
 *
 * 日历视图适合看「哪天有事」，但要找某条具体的行程、或者回顾自己记过什么，
 * 列表要方便得多。重复行程会把重复规则直接标在条目上，一眼能看出它会反复发生。
 *
 * 不设时间的待办也在这里，但要单列一区：它们排不进「即将到来 / 已过去」，
 * 混进日期序列里只会把时间感搅乱。
 */
@Composable
fun EventListScreen(
    repository: EventRepository,
    onBack: () -> Unit,
    onEventClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val calendarData by repository
        .observeCalendar()
        .collectAsState(initial = CalendarData(emptyList(), emptyList()))

    val now = remember { System.currentTimeMillis() }
    val rules = remember(calendarData) { calendarData.rules.associateBy { it.eventId } }
    // 待办按记下的先后排：先记的在上，免得陈年的那几件被后记的压下去。
    // 打完勾的挪到最后的「已完成」，不再占着自己那一行。
    val todos = remember(calendarData) {
        calendarData.events.filter { it.isTodo }.sortedBy { it.createdAt }
    }
    val openTodos = todos.filterNot { it.done }
    val doneTodos = todos.filter { it.done }
    val sorted = remember(calendarData) {
        calendarData.events.filterNot { it.isTodo }.sortedBy { startKey(it) }
    }

    val scope = rememberCoroutineScope()
    val onToggleDone: (EventEntity) -> Unit = { event ->
        scope.launch { repository.setDone(event.id, !event.done) }
    }
    val upcoming = sorted.filter { startKey(it) >= now }
    val past = sorted.filter { startKey(it) < now }.sortedByDescending { startKey(it) }

    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Text(
                text = "全部日程",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (calendarData.events.isEmpty()) {
            Text(
                text = "还没有记录任何行程。回到日历，说一句话或者点「＋ 新建」都可以。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        if (openTodos.isNotEmpty()) {
            SectionTitle("待办（${openTodos.size}）")
            openTodos.forEach { event ->
                EventRow(event, rules[event.id], onEventClick, onToggleDone)
            }
        }

        if (upcoming.isNotEmpty()) {
            SectionTitle("即将到来（${upcoming.size}）")
            upcoming.forEach { event ->
                EventRow(event, rules[event.id], onEventClick, onToggleDone)
            }
        }

        if (past.isNotEmpty()) {
            SectionTitle("已过去（${past.size}）")
            past.forEach { event ->
                EventRow(event, rules[event.id], onEventClick, onToggleDone)
            }
        }

        if (doneTodos.isNotEmpty()) {
            SectionTitle("已完成（${doneTodos.size}）")
            doneTodos.forEach { event ->
                EventRow(event, rules[event.id], onEventClick, onToggleDone)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun EventRow(
    event: EventEntity,
    rule: RecurrenceRuleEntity?,
    onClick: (String) -> Unit,
    onToggleDone: (EventEntity) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(event.id) },
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // 与日历用同一套颜色：紧急偏红、常规偏蓝、不急偏灰。
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(EventColors.accent(event.urgency)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 完成过的待办打个勾并划掉：一眼能看出「这行是做完了的」，不必读文字。
                    if (event.done) {
                        Text(
                            text = EventFormat.DONE_MARK,
                            modifier = Modifier.padding(end = 6.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = event.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (event.done) TextDecoration.LineThrough else null,
                        color = if (event.done) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    // 重复行程用一个显眼的标签标出重复周期，这是列表里最需要一眼看到的信息。
                    EventFormat.recurrenceDescription(rule)?.let { repeat ->
                        Text(
                            text = repeat,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    text = EventFormat.timeDescription(event),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                event.location?.takeIf { it.isNotBlank() }?.let { location ->
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 待办可以就地打勾，不必点进去。定时行程没有「完成」这回事，不给按钮。
            if (event.isTodo) {
                TextButton(
                    onClick = { onToggleDone(event) },
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(end = 4.dp),
                ) {
                    Text(if (event.done) "撤销" else "完成")
                }
            }
        }
    }
}

/** 排序用的时间键：定时行程用时间戳，全天行程用当天零点。 */
private fun startKey(event: EventEntity): Long =
    event.startAt ?: (event.startEpochDay ?: 0L) * MILLIS_PER_DAY

private const val MILLIS_PER_DAY = 86_400_000L
