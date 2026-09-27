package io.github.xfl2342.voiceassistant.domain

import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.data.db.RecurrenceRuleEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * 一条冲突：候选行程的某一次发生，与已有行程的某一次发生撞在同一段时间上。
 *
 * 带上「是哪一天」「对方几点到几点」，是为了在提示框里能直接说清楚，
 * 而不是只告诉用户「有冲突」却让人自己去翻。
 */
data class EventConflict(
    val otherEventId: String,
    val otherTitle: String,
    /** 冲突发生在哪一天（本地日期），重复行程的每一次发生各算一处。 */
    val date: LocalDate,
    val otherAllDay: Boolean,
    val otherStartAt: Instant?,
    val otherEndAt: Instant?,
)

/** 冲突检测的结果。 */
data class ConflictReport(
    /** 用于展示的冲突，按时间排序，最多 [ConflictDetector.MAX_REPORTED] 条。 */
    val conflicts: List<EventConflict>,
    /** 实际检出的冲突总数，可能多于 [conflicts] 的长度。 */
    val totalCount: Int,
) {
    val hasConflict: Boolean get() = totalCount > 0

    companion object {
        val EMPTY = ConflictReport(emptyList(), 0)
    }
}

/**
 * 检查一条行程是否与日历里已有的行程撞时间。
 *
 * 几条刻意的取舍：
 * - **只提示，不阻止**。自用场景里「两个会就是都排在同一天」是常态，
 *   这里只负责把话说清楚，保存与否仍然由用户决定；
 * - **全天不与定时互相算冲突**。「下周三请一天假」和「下周三下午三点开会」
 *   同时存在再正常不过，真把它们算成冲突，提示会变得没法看；
 * - **重复行程只看未来一段时间的发生**。一条「每天」的行程展开到几年后没有意义，
 *   而且会把提示淹掉，所以只往前看 [HORIZON_DAYS] 天，这也是提醒注册采用的思路。
 *
 * 判断本身不碰数据库与系统闹钟，纯函数，便于单元测试。
 */
object ConflictDetector {

    /** 重复行程向未来看多少天。 */
    const val HORIZON_DAYS = 90L

    /** 最多展示几处冲突；其余的只报总数。 */
    const val MAX_REPORTED = 5

    /** 缺少结束时间时的兜底时长，与重复展开、编辑页保持一致。 */
    private const val DEFAULT_DURATION_MILLIS = 60 * 60 * 1000L

    fun detect(
        candidate: EventEntity,
        candidateRule: RecurrenceRuleEntity?,
        existing: List<EventEntity>,
        existingRules: List<RecurrenceRuleEntity>,
        zone: ZoneId,
        weekStartDay: DayOfWeek = DayOfWeek.MONDAY,
        today: LocalDate = LocalDate.now(zone),
    ): ConflictReport {
        val window = windowOf(candidate, candidateRule, zone, today)
            ?: return ConflictReport.EMPTY
        val (from, to) = window

        val mine = RecurrenceExpander.expand(
            events = listOf(candidate),
            rules = listOfNotNull(candidateRule),
            from = from,
            to = to,
            zone = zone,
            weekStartDay = weekStartDay,
        )
        if (mine.isEmpty()) return ConflictReport.EMPTY

        // 编辑已有行程时，它自己当然会和自己重叠，先排掉。
        val others = existing.filter { it.id != candidate.id && !it.deleted }
        if (others.isEmpty()) return ConflictReport.EMPTY

        val theirs = RecurrenceExpander.expand(
            events = others,
            rules = existingRules,
            from = from,
            to = to,
            zone = zone,
            weekStartDay = weekStartDay,
        )
        if (theirs.isEmpty()) return ConflictReport.EMPTY

        // 同一条行程的同一天只报一次：一天里重叠几次说法上也是一处冲突。
        val seen = HashSet<String>()
        val found = mutableListOf<EventConflict>()
        for (occurrence in mine) {
            for (other in theirs) {
                if (!overlaps(occurrence, other)) continue
                if (!seen.add("${other.eventId}@${other.date}")) continue
                found += EventConflict(
                    otherEventId = other.eventId,
                    otherTitle = other.title,
                    date = other.date,
                    otherAllDay = other.allDay,
                    otherStartAt = other.startAt,
                    otherEndAt = other.endAt,
                )
            }
        }
        if (found.isEmpty()) return ConflictReport.EMPTY

        val sorted = found.sortedWith(
            compareBy({ it.date }, { it.otherStartAt?.toEpochMilli() ?: 0L }),
        )
        return ConflictReport(sorted.take(MAX_REPORTED), sorted.size)
    }

    /** 候选行程要检查的时间窗口：单次的看它自己，重复的从今天起往后看一段。 */
    private fun windowOf(
        candidate: EventEntity,
        rule: RecurrenceRuleEntity?,
        zone: ZoneId,
        today: LocalDate,
    ): Pair<LocalDate, LocalDate>? {
        val anchor = anchorDate(candidate, zone) ?: return null
        if (rule == null) {
            // 单次行程（含跨天的全天行程）：只看它自己占用的那些天，
            // 过去与将来一视同仁，这样补录一条旧行程也能看到当时的冲突。
            return anchor to maxOf(anchor, lastDate(candidate, zone) ?: anchor)
        }
        val start = maxOf(anchor, today)
        return start to start.plusDays(HORIZON_DAYS)
    }

    /** 一次发生是否与另一次发生重叠。 */
    private fun overlaps(a: EventOccurrence, b: EventOccurrence): Boolean {
        // 全天与定时各占各的：全天行程没有时刻，无法判断具体撞不撞。
        if (a.allDay != b.allDay) return false
        // 两边都是全天：展开时已按覆盖的每一天各产生一条，同一天就是重叠。
        if (a.allDay) return a.date == b.date

        val aStart = a.startAt ?: return false
        val bStart = b.startAt ?: return false
        // 相隔一天以上不可能重叠（定时行程极少跨天），先做一次便宜的排除。
        if (abs(ChronoUnit.DAYS.between(a.date, b.date)) > 1) return false

        val aEnd = a.endAt ?: aStart.plusMillis(DEFAULT_DURATION_MILLIS)
        val bEnd = b.endAt ?: bStart.plusMillis(DEFAULT_DURATION_MILLIS)
        // 半开区间：15:00-16:00 与 16:00-17:00 首尾相接，不算冲突。
        return aStart < bEnd && bStart < aEnd
    }

    /** 行程自身的第一天：全天看日期字段，定时看时间戳换算出的当地日期。 */
    private fun anchorDate(event: EventEntity, zone: ZoneId): LocalDate? = when {
        event.allDay -> event.startEpochDay?.let(LocalDate::ofEpochDay)
        event.startAt != null -> Instant.ofEpochMilli(event.startAt)
            .atZone(zoneOf(event, zone))
            .toLocalDate()
        else -> null
    }

    /** 行程的最后一天，用于跨天的单次行程。 */
    private fun lastDate(event: EventEntity, zone: ZoneId): LocalDate? = when {
        event.allDay -> (event.endEpochDay ?: event.startEpochDay)?.let(LocalDate::ofEpochDay)
        event.endAt != null -> Instant.ofEpochMilli(event.endAt)
            .atZone(zoneOf(event, zone))
            .toLocalDate()
        else -> null
    }

    /** 行程自带时区，拿不准时退回默认时区。 */
    private fun zoneOf(event: EventEntity, fallback: ZoneId): ZoneId =
        runCatching { ZoneId.of(event.timeZone) }.getOrDefault(fallback)
}
