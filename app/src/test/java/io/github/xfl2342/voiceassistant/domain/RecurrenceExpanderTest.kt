package io.github.xfl2342.voiceassistant.domain

import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.data.db.RecurrenceRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 重复规则展开的测试。
 *
 * 这是整个项目里最容易算错的一块（跨周、间隔、结束日期、全天与定时混用），
 * 所以把它单独拎出来用单元测试覆盖。测试日期都以 2026-09-07（周一）为基准。
 */
class RecurrenceExpanderTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val monday: LocalDate = LocalDate.of(2026, 9, 7)

    @Test
    fun `单次行程在窗口内时产生一条`() {
        val event = timedEvent(at(LocalDate.of(2026, 9, 26), 17), at(LocalDate.of(2026, 9, 26), 18))

        val result = expand(listOf(event), emptyList(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))

        assertEquals(1, result.size)
        assertEquals(LocalDate.of(2026, 9, 26), result.first().date)
        assertEquals(at(LocalDate.of(2026, 9, 26), 17), result.first().startAt)
    }

    @Test
    fun `单次行程在窗口外时不出现`() {
        val event = timedEvent(at(LocalDate.of(2026, 8, 20), 17), at(LocalDate.of(2026, 8, 20), 18))

        val result = expand(listOf(event), emptyList(), monday, monday.plusDays(6))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `每天重复在一周内出现七次`() {
        val event = timedEvent(at(monday, 9), at(monday, 10), id = "daily")
        val rule = rule(event.id, RecurrenceRuleEntity.FREQUENCY_DAILY)

        val result = expand(listOf(event), listOf(rule), monday, monday.plusDays(6))

        assertEquals(7, result.size)
        assertEquals(monday, result.first().date)
        assertEquals(monday.plusDays(6), result.last().date)
    }

    @Test
    fun `每天重复在窗口中间开始时按当天起算`() {
        val event = timedEvent(at(LocalDate.of(2026, 9, 1), 9), at(LocalDate.of(2026, 9, 1), 10), id = "daily")
        val rule = rule(event.id, RecurrenceRuleEntity.FREQUENCY_DAILY)
        val from = LocalDate.of(2026, 9, 5)

        val result = expand(listOf(event), listOf(rule), from, LocalDate.of(2026, 9, 7))

        assertEquals(listOf(from, from.plusDays(1), from.plusDays(2)), result.map { it.date })
    }

    @Test
    fun `每周指定周一三五时一周出现三次`() {
        val event = timedEvent(at(monday, 19), at(monday, 20), id = "weekly")
        val rule = rule(event.id, RecurrenceRuleEntity.FREQUENCY_WEEKLY, byDay = "MO,WE,FR")

        val result = expand(listOf(event), listOf(rule), monday, monday.plusDays(6))

        assertEquals(
            listOf(monday, monday.plusDays(2), monday.plusDays(4)),
            result.map { it.date },
        )
    }

    @Test
    fun `模型返回英文星期名时同样能识别`() {
        val event = timedEvent(at(monday, 19), at(monday, 20), id = "weekly")
        val rule = rule(event.id, RecurrenceRuleEntity.FREQUENCY_WEEKLY, byDay = "monday,wednesday")

        val result = expand(listOf(event), listOf(rule), monday, monday.plusDays(6))

        assertEquals(listOf(monday, monday.plusDays(2)), result.map { it.date })
    }

    @Test
    fun `间隔两周时跳过中间一周`() {
        val event = timedEvent(at(monday, 19), at(monday, 20), id = "biweekly")
        val rule = rule(
            event.id,
            RecurrenceRuleEntity.FREQUENCY_WEEKLY,
            byDay = "MO,WE,FR",
            interval = 2,
        )

        val result = expand(listOf(event), listOf(rule), monday, monday.plusDays(20))

        assertEquals(
            listOf(
                monday,
                monday.plusDays(2),
                monday.plusDays(4),
                monday.plusDays(14),
                monday.plusDays(16),
                monday.plusDays(18),
            ),
            result.map { it.date },
        )
    }

    @Test
    fun `超过结束日期后不再出现`() {
        val event = timedEvent(at(monday, 19), at(monday, 20), id = "weekly")
        val rule = rule(
            event.id,
            RecurrenceRuleEntity.FREQUENCY_WEEKLY,
            byDay = "MO,WE,FR",
            endDate = monday.plusDays(4),
        )

        val result = expand(listOf(event), listOf(rule), monday, monday.plusDays(30))

        assertEquals(listOf(monday, monday.plusDays(2), monday.plusDays(4)), result.map { it.date })
    }

    @Test
    fun `重复实例保留原来的时间点与时长`() {
        val event = timedEvent(at(monday, 17), at(monday, 18, 30), id = "weekly")
        val rule = rule(event.id, RecurrenceRuleEntity.FREQUENCY_WEEKLY, byDay = "MO")

        val result = expand(listOf(event), listOf(rule), monday, monday.plusDays(14))

        assertEquals(3, result.size)
        val second = result[1]
        assertEquals(monday.plusDays(7), second.date)
        assertEquals(at(monday.plusDays(7), 17), second.startAt)
        assertEquals(at(monday.plusDays(7), 18, 30), second.endAt)
    }

    @Test
    fun `每周规则不会回溯到行程开始之前`() {
        // 行程从周二开始，规则却是每周一：开始日期之前的那个周一不应出现。
        val tuesday = monday.plusDays(1)
        val event = timedEvent(at(tuesday, 9), at(tuesday, 10), id = "weekly")
        val rule = rule(event.id, RecurrenceRuleEntity.FREQUENCY_WEEKLY, byDay = "MO")

        val result = expand(
            listOf(event),
            listOf(rule),
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 30),
        )

        assertEquals(
            listOf(monday.plusDays(7), monday.plusDays(14), monday.plusDays(21)),
            result.map { it.date },
        )
    }

    @Test
    fun `单日全天行程产生一条`() {
        val event = allDayEvent(monday)

        val result = expand(listOf(event), emptyList(), monday, monday.plusDays(6))

        assertEquals(1, result.size)
        assertTrue(result.first().allDay)
        assertEquals(monday, result.first().date)
    }

    @Test
    fun `跨天全天行程每天各占一条`() {
        val event = allDayEvent(monday, monday.plusDays(2))

        val result = expand(listOf(event), emptyList(), monday, monday.plusDays(6))

        assertEquals(
            listOf(monday, monday.plusDays(1), monday.plusDays(2)),
            result.map { it.date },
        )
        assertTrue(result.all { it.allDay })
    }

    @Test
    fun `全天行程与定时行程同一天时全天排在前面`() {
        val allDay = allDayEvent(monday, id = "allday")
        val timed = timedEvent(at(monday, 9), at(monday, 10), id = "timed")

        val result = expand(listOf(timed, allDay), emptyList(), monday, monday)

        assertEquals(2, result.size)
        assertEquals("allday", result.first().eventId)
        assertTrue(result.first().allDay)
    }

    @Test
    fun `已删除的行程不参与展开`() {
        val event = allDayEvent(monday).copy(deleted = true)

        val result = expand(listOf(event), emptyList(), monday, monday.plusDays(6))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `每周的起始日是周一`() {
        // 2026-09-07 是周一，因此 09-06（周日）属于上一周。
        val event = timedEvent(at(monday, 9), at(monday, 10), id = "weekly")
        val rule = rule(
            event.id,
            RecurrenceRuleEntity.FREQUENCY_WEEKLY,
            byDay = "SU",
        )

        val result = expand(listOf(event), listOf(rule), monday, monday.plusDays(7))

        // 只有 09-13 这个周日落在从 09-07 开始的那一周里。
        assertEquals(listOf(monday.plusDays(6)), result.map { it.date })
    }

    @Test
    fun `开始日一定会出现一次_哪怕它属于上一周`() {
        // 2026-09-06 是周日。若一周从周一开始，它落在 08-31 那一周；
        // 「隔周」规则按周划分时不能把行程自己的第一天排除掉。
        val sunday = LocalDate.of(2026, 9, 6)
        val event = timedEvent(at(sunday, 9), at(sunday, 10), id = "biweekly")
        val rule = rule(
            event.id,
            RecurrenceRuleEntity.FREQUENCY_WEEKLY,
            byDay = "SU",
            interval = 2,
        )

        val result = expand(listOf(event), listOf(rule), sunday, sunday.plusDays(28))

        assertTrue(result.any { it.date == sunday })
    }

    private fun expand(
        events: List<EventEntity>,
        rules: List<RecurrenceRuleEntity>,
        from: LocalDate,
        to: LocalDate,
    ): List<EventOccurrence> = RecurrenceExpander.expand(events, rules, from, to, zone)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant =
        LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant()

    private fun timedEvent(
        start: Instant,
        end: Instant,
        id: String = "event",
    ): EventEntity = EventEntity(
        id = id,
        title = "测试行程",
        allDay = false,
        startAt = start.toEpochMilli(),
        endAt = end.toEpochMilli(),
        timeZone = zone.id,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun allDayEvent(
        start: LocalDate,
        end: LocalDate = start,
        id: String = "event",
    ): EventEntity = EventEntity(
        id = id,
        title = "全天行程",
        allDay = true,
        startEpochDay = start.toEpochDay(),
        endEpochDay = end.toEpochDay(),
        timeZone = zone.id,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun rule(
        eventId: String,
        frequency: String,
        interval: Int = 1,
        byDay: String? = null,
        endDate: LocalDate? = null,
    ): RecurrenceRuleEntity = RecurrenceRuleEntity(
        id = "rule-$eventId",
        eventId = eventId,
        frequency = frequency,
        interval = interval,
        byDay = byDay,
        endType = if (endDate != null) {
            RecurrenceRuleEntity.END_TYPE_ON_DATE
        } else {
            RecurrenceRuleEntity.END_TYPE_NEVER
        },
        endEpochDay = endDate?.toEpochDay(),
    )
}
