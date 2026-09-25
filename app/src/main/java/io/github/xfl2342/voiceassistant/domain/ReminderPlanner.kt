package io.github.xfl2342.voiceassistant.domain

import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.data.db.ReminderEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * 根据行程与「提前多少分钟」算出提醒实体。
 *
 * 新建行程和编辑行程都要用同一套规则，所以单独抽出来，
 * 免得两处各写一遍、日后又不一致。
 */
object ReminderPlanner {

    /** 全天行程没有具体时刻，以当天这个时间点为锚点往前推算。 */
    private val allDayAnchor: LocalTime = LocalTime.of(9, 0)

    /** 计算的基准时刻：定时行程用开始时间，全天行程用当天早上九点。 */
    fun anchorOf(event: EventEntity, zone: ZoneId): Instant = when {
        event.allDay -> LocalDate.ofEpochDay(event.startEpochDay ?: 0)
            .atTime(allDayAnchor)
            .atZone(zone)
            .toInstant()
        else -> Instant.ofEpochMilli(event.startAt ?: 0)
    }

    /** 生成一条「提前 minutesBefore 分钟」的提醒。 */
    fun create(event: EventEntity, minutesBefore: Int): ReminderEntity {
        val zone = runCatching { ZoneId.of(event.timeZone) }.getOrDefault(ZoneId.of(EventEntity.DEFAULT_TIME_ZONE))
        return ReminderEntity(
            id = UUID.randomUUID().toString(),
            eventId = event.id,
            triggerType = ReminderEntity.TYPE_BEFORE,
            minutesBefore = minutesBefore,
            triggerAt = anchorOf(event, zone).minusSeconds(minutesBefore * 60L).toEpochMilli(),
        )
    }
}
