package io.github.xfl2342.voiceassistant.domain

import java.time.Instant
import java.time.LocalDate
import io.github.xfl2342.voiceassistant.data.db.EventEntity

/**
 * 展开之后的一次「发生」。
 *
 * 重复行程在数据库里只存一条记录加一条规则，界面上要看的是「这个月哪几天有事」，
 * 所以在内存里展开成这种形态再交给日历渲染。
 *
 * [date] 表示这条行程占用的某一天：跨天的全天行程会在它覆盖的每一天各产生一条。
 */
data class EventOccurrence(
    val eventId: String,
    val title: String,
    val date: LocalDate,
    val allDay: Boolean,
    val startAt: Instant?,
    val endAt: Instant?,
    val location: String?,
    /** 急迫程度（high / normal / low），日历按它给行程上色。 */
    val urgency: String = EventEntity.URGENCY_NORMAL,
    /** 来自重复规则时为规则 id，单次行程为 null。 */
    val recurrenceRuleId: String? = null,
) {
    /** 界面列表用的稳定标识。 */
    val key: String get() = "$eventId@$date"
}
