package io.github.xfl2342.voiceassistant.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.xfl2342.voiceassistant.data.CalendarData
import io.github.xfl2342.voiceassistant.data.EventRepository
import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.data.db.RecurrenceRuleEntity
import io.github.xfl2342.voiceassistant.ui.components.EventFormat

/**
 * 全部日程列表。
 *
 * 日历视图适合看「哪天有事」，但要找某条具体的行程、或者回顾自己记过什么，
 * 列表要方便得多。重复行程会把重复规则直接标在条目上，一眼能看出它会反复发生。
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
    val sorted = remember(calendarData) {
        calendarData.events.sortedBy { startKey(it) }
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

        if (upcoming.isNotEmpty()) {
            SectionTitle("即将到来（${upcoming.size}）")
            upcoming.forEach { event ->
                EventRow(event, rules[event.id], onEventClick)
            }
        }

        if (past.isNotEmpty()) {
            SectionTitle("已过去（${past.size}）")
            past.forEach { event ->
                EventRow(event, rules[event.id], onEventClick)
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
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(event.id) },
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
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
    }
}

/** 排序用的时间键：定时行程用时间戳，全天行程用当天零点。 */
private fun startKey(event: EventEntity): Long =
    event.startAt ?: (event.startEpochDay ?: 0L) * MILLIS_PER_DAY

private const val MILLIS_PER_DAY = 86_400_000L
