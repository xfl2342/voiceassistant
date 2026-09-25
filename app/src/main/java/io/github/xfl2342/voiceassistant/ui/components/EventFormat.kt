package io.github.xfl2342.voiceassistant.ui.components

import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.data.db.RecurrenceRuleEntity
import io.github.xfl2342.voiceassistant.domain.DayOfWeekCodes
import io.github.xfl2342.voiceassistant.domain.ReminderPreset
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 行程信息的文字格式化，详情页与编辑页共用。 */
object EventFormat {

    private val zone: ZoneId = ZoneId.of(EventEntity.DEFAULT_TIME_ZONE)
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val fullDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")
    private val shortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M 月 d 日")

    fun fullDate(date: LocalDate): String = fullDateFormatter.format(date)

    fun shortDate(date: LocalDate): String = shortDateFormatter.format(date)

    fun time(instant: Instant): String = timeFormatter.format(instant.atZone(zone))

    /** 行程时间的一句话描述。 */
    fun timeDescription(event: EventEntity): String = if (event.allDay) {
        val start = LocalDate.ofEpochDay(event.startEpochDay ?: 0)
        val end = LocalDate.ofEpochDay(event.endEpochDay ?: start.toEpochDay())
        if (start == end) "${fullDate(start)}（全天）" else "${fullDate(start)} 至 ${fullDate(end)}（全天）"
    } else {
        val start = Instant.ofEpochMilli(event.startAt ?: 0)
        val end = event.endAt?.let(Instant::ofEpochMilli)
        val date = start.atZone(zone).toLocalDate()
        if (end == null) {
            "${fullDate(date)} ${time(start)}"
        } else {
            "${fullDate(date)} ${time(start)} - ${time(end)}"
        }
    }

    /** 重复规则的中文描述，没有规则时返回 null。 */
    fun recurrenceDescription(rule: RecurrenceRuleEntity?): String? = when (rule?.frequency) {
        null -> null
        RecurrenceRuleEntity.FREQUENCY_DAILY -> "每天"
        RecurrenceRuleEntity.FREQUENCY_WEEKLY -> {
            val days = DayOfWeekCodes.parse(rule.byDay)
            if (days.isEmpty()) "每周" else "每周 " + DayOfWeekCodes.labels(days)
        }
        else -> null
    }

    /** 提醒的中文描述。 */
    fun reminderDescription(minutesBefore: Int): String = ReminderPreset.describe(minutesBefore)
}
