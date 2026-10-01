package io.github.xfl2342.voiceassistant.domain

import io.github.xfl2342.voiceassistant.ai.EventDraft
import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.data.db.RecurrenceRuleEntity
import io.github.xfl2342.voiceassistant.data.db.ReminderEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

/** 一次保存所需要的全部数据。 */
data class EventSaveBundle(
    val event: EventEntity,
    val rule: RecurrenceRuleEntity?,
    val reminders: List<ReminderEntity>,
)

/**
 * 把模型解析出来的草稿转成可以入库的实体。
 *
 * 这里是最需要小心的一步：模型返回的是文本，任何格式意外都会变成脏数据，
 * 所以全程用 [Result] 包住，解析不出来就明确失败，让用户去手工改，而不是存进去一条坏数据。
 */
object EventDraftMapper {

    /** 用户只说了开始时间时，默认安排一小时。 */
    private const val DEFAULT_DURATION_MINUTES = 60L

    fun toBundle(
        draft: EventDraft,
        rawText: String?,
        zone: ZoneId = ZoneId.of(EventEntity.DEFAULT_TIME_ZONE),
        now: Instant = Instant.now(),
        eventId: String = UUID.randomUUID().toString(),
    ): Result<EventSaveBundle> = runCatching {
        require(draft.title.isNotBlank()) { "标题为空，无法保存" }

        val timestamp = now.toEpochMilli()
        val event = when {
            draft.isTodo -> buildTodoEvent(draft, eventId, zone, timestamp, rawText)
            draft.allDay -> buildAllDayEvent(draft, eventId, zone, timestamp, rawText)
            else -> buildTimedEvent(draft, eventId, zone, timestamp, rawText)
        }

        EventSaveBundle(
            event = event,
            // 待办没有时间：既没有「下一次」可以重复，也没有可以提醒的时刻。
            rule = if (draft.isTodo) null else buildRule(draft, eventId),
            reminders = if (draft.isTodo) emptyList() else buildReminders(draft, event),
        )
    }

    /**
     * 不设时间的待办：两组时间字段都留空。
     *
     * 模型可能没听出时间（missing_fields 里有 time），也可能是用户本来就没打算定时间——
     * 判断权在模型给出的 kind 上，这里只负责照做：存成待办，不编造一个时间出来。
     */
    private fun buildTodoEvent(
        draft: EventDraft,
        eventId: String,
        zone: ZoneId,
        timestamp: Long,
        rawText: String?,
    ): EventEntity = EventEntity(
        id = eventId,
        title = draft.title,
        allDay = false,
        timeZone = zone.id,
        location = draft.location.ifBlank { null },
        urgency = EventEntity.normalizeUrgency(draft.urgency),
        rawText = rawText,
        createdAt = timestamp,
        updatedAt = timestamp,
    )

    private fun buildTimedEvent(
        draft: EventDraft,
        eventId: String,
        zone: ZoneId,
        timestamp: Long,
        rawText: String?,
    ): EventEntity {
        val start = parseInstant(draft.start, zone)
            ?: error("没能看懂开始时间：${draft.start}")
        val end = parseInstant(draft.end, zone)
            ?: start.plusSeconds(DEFAULT_DURATION_MINUTES * 60)

        return EventEntity(
            id = eventId,
            title = draft.title,
            allDay = false,
            startAt = start.toEpochMilli(),
            endAt = end.toEpochMilli(),
            timeZone = zone.id,
            location = draft.location.ifBlank { null },
            urgency = EventEntity.normalizeUrgency(draft.urgency),
            rawText = rawText,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
    }

    private fun buildAllDayEvent(
        draft: EventDraft,
        eventId: String,
        zone: ZoneId,
        timestamp: Long,
        rawText: String?,
    ): EventEntity {
        val startDate = parseDate(draft.start, zone)
            ?: error("没能看懂日期：${draft.start}")
        val endDate = parseDate(draft.end, zone) ?: startDate

        return EventEntity(
            id = eventId,
            title = draft.title,
            allDay = true,
            startEpochDay = startDate.toEpochDay(),
            endEpochDay = endDate.toEpochDay().coerceAtLeast(startDate.toEpochDay()),
            timeZone = zone.id,
            location = draft.location.ifBlank { null },
            urgency = EventEntity.normalizeUrgency(draft.urgency),
            rawText = rawText,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
    }

    private fun buildRule(draft: EventDraft, eventId: String): RecurrenceRuleEntity? {
        val frequency = when (draft.recurrenceFrequency?.lowercase()) {
            RecurrenceRuleEntity.FREQUENCY_DAILY -> RecurrenceRuleEntity.FREQUENCY_DAILY
            RecurrenceRuleEntity.FREQUENCY_WEEKLY -> RecurrenceRuleEntity.FREQUENCY_WEEKLY
            else -> return null
        }

        val days = DayOfWeekCodes.parse(draft.recurrenceByDay.joinToString(","))
        val endDate = draft.recurrenceEndDate?.let { parseDate(it, ZoneId.of("Asia/Shanghai")) }

        return RecurrenceRuleEntity(
            id = UUID.randomUUID().toString(),
            eventId = eventId,
            frequency = frequency,
            interval = 1,
            byDay = if (days.isEmpty()) null else DayOfWeekCodes.format(days),
            endType = if (endDate != null) {
                RecurrenceRuleEntity.END_TYPE_ON_DATE
            } else {
                RecurrenceRuleEntity.END_TYPE_NEVER
            },
            endEpochDay = endDate?.toEpochDay(),
        )
    }

    private fun buildReminders(draft: EventDraft, event: EventEntity): List<ReminderEntity> {
        val minutesList = draft.reminderMinutes
            .ifEmpty { listOf(ReminderPreset.fromUrgency(draft.urgency).minutesBefore) }
            .filter { it > 0 }
            .distinct()

        return minutesList.map { minutes -> ReminderPlanner.create(event, minutes) }
    }

    private fun parseInstant(text: String, zone: ZoneId): Instant? {
        val value = text.trim().takeIf { it.isNotEmpty() && it != "null" } ?: return null
        return runCatching { OffsetDateTime.parse(value).toInstant() }
            .recoverCatching { Instant.parse(value) }
            .recoverCatching { LocalDateTime.parse(value).atZone(zone).toInstant() }
            .getOrNull()
    }

    private fun parseDate(text: String, zone: ZoneId): LocalDate? {
        val value = text.trim().takeIf { it.isNotEmpty() && it != "null" } ?: return null
        return runCatching { LocalDate.parse(value) }
            .recoverCatching { parseInstant(value, zone)?.atZone(zone)?.toLocalDate() }
            .getOrNull()
    }
}
