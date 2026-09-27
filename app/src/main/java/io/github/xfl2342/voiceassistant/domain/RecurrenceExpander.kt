package io.github.xfl2342.voiceassistant.domain

import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.data.db.RecurrenceRuleEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * 把「行程 + 重复规则」展开成指定日期区间内的每一次发生。
 *
 * 几条约定：
 * - 一周以**周一**为第一天，与国内日历习惯一致；
 * - 重复实例不能早于行程自身的开始日期（「从明天起每周一三五」不会把之前的周一算进来）；
 * - 定时行程保留原来的时间点与时长，只把日期挪过去；
 * - 全天行程按本地日期处理，不做时区换算。
 */
object RecurrenceExpander {

    /** 单条行程在窗口内的展开上限，防止规则写错导致死循环。 */
    private const val MAX_OCCURRENCES = 500

    /** 原数据缺少结束时间时的兜底时长：1 小时。 */
    private const val DEFAULT_DURATION_MILLIS = 60 * 60 * 1000L

    fun expand(
        events: List<EventEntity>,
        rules: List<RecurrenceRuleEntity>,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId,
        weekStartDay: DayOfWeek = DayOfWeek.MONDAY,
    ): List<EventOccurrence> {
        if (to.isBefore(from)) return emptyList()

        val rulesByEvent = rules.groupBy { it.eventId }
        return events
            .asSequence()
            .filter { !it.deleted }
            .flatMap {
                expandOne(it, rulesByEvent[it.id].orEmpty().firstOrNull(), from, to, zone, weekStartDay)
            }
            .sortedWith(
                compareBy(
                    { it.date },
                    { if (it.allDay) 0 else 1 },
                    { it.startAt?.toEpochMilli() ?: 0L },
                )
            )
            .toList()
    }

    private fun expandOne(
        event: EventEntity,
        rule: RecurrenceRuleEntity?,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId,
        weekStartDay: DayOfWeek,
    ): List<EventOccurrence> {
        val anchor = anchorDate(event, zone) ?: return emptyList()
        val startDates = occurrenceStartDates(anchor, rule, from, to, weekStartDay)
        if (startDates.isEmpty()) return emptyList()

        return if (event.allDay) {
            expandAllDay(event, rule, startDates, from, to)
        } else {
            expandTimed(event, rule, startDates, zone)
        }
    }

    private fun expandAllDay(
        event: EventEntity,
        rule: RecurrenceRuleEntity?,
        startDates: List<LocalDate>,
        from: LocalDate,
        to: LocalDate,
    ): List<EventOccurrence> {
        val firstDay = event.startEpochDay ?: return emptyList()
        val lastDay = event.endEpochDay ?: firstDay
        val span = (lastDay - firstDay).coerceAtLeast(0)

        return startDates
            .flatMap { start -> (0..span).map { start.plusDays(it) } }
            .filter { !it.isBefore(from) && !it.isAfter(to) }
            .distinct()
            .map { day ->
                EventOccurrence(
                    eventId = event.id,
                    title = event.title,
                    date = day,
                    allDay = true,
                    startAt = null,
                    endAt = null,
                    location = event.location,
                    urgency = event.urgency,
                    recurrenceRuleId = rule?.id,
                )
            }
    }

    private fun expandTimed(
        event: EventEntity,
        rule: RecurrenceRuleEntity?,
        startDates: List<LocalDate>,
        zone: ZoneId,
    ): List<EventOccurrence> {
        val startMillis = event.startAt ?: return emptyList()
        val eventZone = runCatching { ZoneId.of(event.timeZone) }.getOrDefault(zone)
        val originalStart = Instant.ofEpochMilli(startMillis).atZone(eventZone)
        val originalTime: LocalTime = originalStart.toLocalTime()
        val duration = event.endAt?.let { it - startMillis } ?: DEFAULT_DURATION_MILLIS

        return startDates.map { date ->
            val begin = LocalDateTime.of(date, originalTime).atZone(eventZone).toInstant()
            EventOccurrence(
                eventId = event.id,
                title = event.title,
                date = date,
                allDay = false,
                startAt = begin,
                endAt = begin.plusMillis(duration),
                location = event.location,
                urgency = event.urgency,
                recurrenceRuleId = rule?.id,
            )
        }
    }

    /** 行程自身的第一天：全天看日期字段，定时看时间戳换算出的本地日期。 */
    private fun anchorDate(event: EventEntity, zone: ZoneId): LocalDate? = when {
        event.allDay -> event.startEpochDay?.let(LocalDate::ofEpochDay)
        event.startAt != null -> {
            val eventZone = runCatching { ZoneId.of(event.timeZone) }.getOrDefault(zone)
            Instant.ofEpochMilli(event.startAt).atZone(eventZone).toLocalDate()
        }
        else -> null
    }

    private fun occurrenceStartDates(
        anchor: LocalDate,
        rule: RecurrenceRuleEntity?,
        from: LocalDate,
        to: LocalDate,
        weekStartDay: DayOfWeek,
    ): List<LocalDate> {
        if (rule == null) {
            return if (anchor in from..to) listOf(anchor) else emptyList()
        }

        val ruleEnd = ruleEndDate(rule)
        val windowStart = maxOf(anchor, from)
        val windowEnd = if (ruleEnd != null) minOf(to, ruleEnd) else to
        if (windowEnd.isBefore(windowStart)) return emptyList()

        val step = rule.interval.coerceAtLeast(1)
        return when (rule.frequency) {
            RecurrenceRuleEntity.FREQUENCY_DAILY -> dailyDates(anchor, step, windowStart, windowEnd)
            RecurrenceRuleEntity.FREQUENCY_WEEKLY ->
                weeklyDates(anchor, step, rule.byDay, windowStart, windowEnd, weekStartDay)
            else -> emptyList()
        }
    }

    private fun dailyDates(
        anchor: LocalDate,
        step: Int,
        windowStart: LocalDate,
        windowEnd: LocalDate,
    ): List<LocalDate> {
        val daysSinceAnchor = ChronoUnit.DAYS.between(anchor, windowStart)
        val skippedPeriods = if (daysSinceAnchor <= 0) 0 else (daysSinceAnchor + step - 1) / step
        var current = anchor.plusDays(skippedPeriods.toLong() * step)

        val dates = mutableListOf<LocalDate>()
        while (!current.isAfter(windowEnd) && dates.size < MAX_OCCURRENCES) {
            if (!current.isBefore(windowStart)) dates += current
            current = current.plusDays(step.toLong())
        }
        return dates
    }

    private fun weeklyDates(
        anchor: LocalDate,
        step: Int,
        byDay: String?,
        windowStart: LocalDate,
        windowEnd: LocalDate,
        weekStartDay: DayOfWeek,
    ): List<LocalDate> {
        val days: Set<DayOfWeek> = DayOfWeekCodes.parse(byDay).ifEmpty { setOf(anchor.dayOfWeek) }
        val anchorWeekStart = weekStart(anchor, weekStartDay)

        val dates = mutableListOf<LocalDate>()
        var current = windowStart
        while (!current.isAfter(windowEnd) && dates.size < MAX_OCCURRENCES) {
            if (days.contains(current.dayOfWeek)) {
                val weeksApart =
                    ChronoUnit.WEEKS.between(anchorWeekStart, weekStart(current, weekStartDay))
                if (weeksApart % step == 0L) dates += current
            }
            current = current.plusDays(1)
        }
        return dates
    }

    private fun ruleEndDate(rule: RecurrenceRuleEntity): LocalDate? =
        if (rule.endType == RecurrenceRuleEntity.END_TYPE_ON_DATE) {
            rule.endEpochDay?.let(LocalDate::ofEpochDay)
        } else {
            null
        }

    /** 按设置取出一周的第一天（默认周一）。 */
    private fun weekStart(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
}
