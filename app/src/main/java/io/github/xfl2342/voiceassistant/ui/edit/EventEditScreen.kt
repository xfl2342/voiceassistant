package io.github.xfl2342.voiceassistant.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.xfl2342.voiceassistant.data.EventDetail
import io.github.xfl2342.voiceassistant.data.SettingsStore
import io.github.xfl2342.voiceassistant.data.TimeInputMode
import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.domain.EventSaveBundle
import io.github.xfl2342.voiceassistant.domain.EventService
import io.github.xfl2342.voiceassistant.domain.ReminderPlanner
import io.github.xfl2342.voiceassistant.domain.ReminderPreset
import io.github.xfl2342.voiceassistant.ui.components.EventFormat
import io.github.xfl2342.voiceassistant.ui.components.WheelTimePicker
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** 编辑时要操作的时间字段。 */
private enum class TimeField { START, END }

/**
 * 编辑已有行程。
 *
 * 只改「行程本身」（标题、时间、地点、提醒）；重复规则保持原样，不在这里改。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventEditScreen(
    service: EventService,
    settingsStore: SettingsStore,
    eventId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.of(EventEntity.DEFAULT_TIME_ZONE) }
    val scope = rememberCoroutineScope()
    val timeInputMode = remember { settingsStore.timeInputMode }

    var detail by remember { mutableStateOf<EventDetail?>(null) }
    var title by remember { mutableStateOf("") }
    var allDay by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(LocalDate.now(zone)) }
    var startTime by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var endDate by remember { mutableStateOf(LocalDate.now(zone)) }
    var endTime by remember { mutableStateOf(LocalTime.of(10, 0)) }
    var location by remember { mutableStateOf("") }
    var reminderMinutes by remember { mutableStateOf<Int?>(ReminderPreset.NORMAL.minutesBefore) }
    var saving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var dateTarget by remember { mutableStateOf<TimeField?>(null) }
    var timeTarget by remember { mutableStateOf<TimeField?>(null) }

    LaunchedEffect(eventId) {
        val loaded = service.load(eventId) ?: return@LaunchedEffect
        detail = loaded
        val event = loaded.event
        title = event.title
        allDay = event.allDay
        location = event.location.orEmpty()
        if (event.allDay) {
            val start = LocalDate.ofEpochDay(event.startEpochDay ?: LocalDate.now(zone).toEpochDay())
            startDate = start
            endDate = LocalDate.ofEpochDay(event.endEpochDay ?: start.toEpochDay())
        } else {
            val start = Instant.ofEpochMilli(event.startAt ?: 0).atZone(zone)
            startDate = start.toLocalDate()
            startTime = start.toLocalTime()
            val end = event.endAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
            endDate = end?.toLocalDate() ?: startDate
            endTime = end?.toLocalTime() ?: startTime.plusHours(1)
        }
        reminderMinutes = loaded.reminders.firstOrNull { it.minutesBefore != null }?.minutesBefore
    }

    BackHandler(onBack = onBack)

    fun buildUpdatedEvent(original: EventEntity): EventEntity {
        val now = System.currentTimeMillis()
        return if (allDay) {
            original.copy(
                title = title.trim(),
                allDay = true,
                startAt = null,
                endAt = null,
                startEpochDay = startDate.toEpochDay(),
                endEpochDay = endDate.toEpochDay().coerceAtLeast(startDate.toEpochDay()),
                location = location.trim().ifBlank { null },
                updatedAt = now,
            )
        } else {
            val start = LocalDateTime.of(startDate, startTime).atZone(zone).toInstant()
            var end = LocalDateTime.of(endDate, endTime).atZone(zone).toInstant()
            // 结束时间早于开始时间时，按一小时处理，避免存进一条负数时长的行程。
            if (end.isBefore(start)) end = start.plusSeconds(3600)
            original.copy(
                title = title.trim(),
                allDay = false,
                startAt = start.toEpochMilli(),
                endAt = end.toEpochMilli(),
                startEpochDay = null,
                endEpochDay = null,
                location = location.trim().ifBlank { null },
                updatedAt = now,
            )
        }
    }

    fun save() {
        val loaded = detail ?: return
        if (title.isBlank()) {
            errorText = "标题不能为空"
            return
        }
        saving = true
        errorText = null

        scope.launch {
            val updated = buildUpdatedEvent(loaded.event)
            val reminders = reminderMinutes
                ?.let { listOf(ReminderPlanner.create(updated, it)) }
                .orEmpty()
            service.save(EventSaveBundle(updated, loaded.rule, reminders))
            saving = false
            onSaved()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Text(
                text = "编辑行程",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (detail == null) {
            Text("正在读取…", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("标题") },
                    singleLine = true,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("全天", modifier = Modifier.weight(1f))
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                }

                Text(
                    text = "开始",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { dateTarget = TimeField.START }) {
                        Text(EventFormat.fullDate(startDate))
                    }
                    if (!allDay) {
                        OutlinedButton(onClick = { timeTarget = TimeField.START }) {
                            Text(EventFormat.time(LocalDateTime.of(startDate, startTime).atZone(zone).toInstant()))
                        }
                    }
                }

                Text(
                    text = "结束",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { dateTarget = TimeField.END }) {
                        Text(EventFormat.fullDate(endDate))
                    }
                    if (!allDay) {
                        OutlinedButton(onClick = { timeTarget = TimeField.END }) {
                            Text(EventFormat.time(LocalDateTime.of(endDate, endTime).atZone(zone).toInstant()))
                        }
                    }
                }

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("地点（可留空）") },
                    singleLine = true,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "提醒",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                // 用可换行的布局：预设多的时候不至于把最后一个挤出屏幕。
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReminderPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = reminderMinutes == preset.minutesBefore,
                            onClick = { reminderMinutes = preset.minutesBefore },
                            label = { Text(ReminderPreset.describe(preset.minutesBefore)) },
                        )
                    }
                    FilterChip(
                        selected = reminderMinutes == null,
                        onClick = { reminderMinutes = null },
                        label = { Text("不提醒") },
                    )
                }
            }
        }

        errorText?.let { message ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        Button(
            onClick = { save() },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (saving) "保存中…" else "保存")
        }
    }

    dateTarget?.let { target ->
        key(target) {
            val initial = if (target == TimeField.START) startDate else endDate
            val state = rememberDatePickerState(
                initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { dateTarget = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            state.selectedDateMillis?.let { millis ->
                                val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                                if (target == TimeField.START) {
                                    // 改开始日期时，结束日期不早于开始日期。
                                    if (endDate.isBefore(picked)) endDate = picked
                                    startDate = picked
                                } else {
                                    endDate = picked
                                }
                            }
                            dateTarget = null
                        }
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dateTarget = null }) { Text("取消") }
                },
            ) {
                DatePicker(state = state)
            }
        }
    }

    timeTarget?.let { target ->
        key(target, timeInputMode) {
            val initial = if (target == TimeField.START) startTime else endTime
            var wheelHour by remember { mutableStateOf(initial.hour) }
            var wheelMinute by remember { mutableStateOf(initial.minute) }
            val state = rememberTimePickerState(
                initialHour = initial.hour,
                initialMinute = initial.minute,
                is24Hour = true,
            )
            // 这里刻意不用 AlertDialog：它的宽度约束会把 24 小时表盘的内圈裁掉
            // （13–23 点的数字在内圈上），看起来就像只能选 12 小时。
            Dialog(onDismissRequest = { timeTarget = null }) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // 用哪种方式在「设置 → 选择时间的方式」里切换，这里只按设置显示。
                        when (timeInputMode) {
                            TimeInputMode.WHEEL -> WheelTimePicker(
                                hour = wheelHour,
                                minute = wheelMinute,
                                onHourChange = { wheelHour = it },
                                onMinuteChange = { wheelMinute = it },
                            )
                            TimeInputMode.INPUT -> TimeInput(state = state)
                            TimeInputMode.DIAL -> TimePicker(state = state)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { timeTarget = null }) { Text("取消") }
                            TextButton(
                                onClick = {
                                    val picked = if (timeInputMode == TimeInputMode.WHEEL) {
                                        LocalTime.of(wheelHour, wheelMinute)
                                    } else {
                                        LocalTime.of(state.hour, state.minute)
                                    }
                                    if (target == TimeField.START) startTime = picked else endTime = picked
                                    timeTarget = null
                                }
                            ) {
                                Text("确定")
                            }
                        }
                    }
                }
            }
        }
    }
}
