package io.github.xfl2342.voiceassistant.domain

import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.data.db.RecurrenceRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 保存前冲突检测的测试。
 *
 * 最容易出错的是「什么才算撞上」：首尾相接的两场会、全天与定时行程、
 * 重复行程展开后的每一次发生，都要钉死在这里，免得日后改动提醒逻辑时把判断带歪。
 */
class ConflictDetectorTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 9, 26)

    @Test
    fun `时间重叠的定时行程会被检出`() {
        val existing = timed("a", "项目评审会", at(15, 0), at(16, 0))
        val candidate = timed("b", "电话沟通", at(15, 30), at(16, 30))

        val report = detect(candidate, existing = listOf(existing))

        assertTrue(report.hasConflict)
        assertEquals(1, report.totalCount)
        val conflict = report.conflicts.single()
        assertEquals("项目评审会", conflict.otherTitle)
        assertEquals(today, conflict.date)
        assertFalse(conflict.otherAllDay)
        assertEquals(at(15, 0).atZone(zone).toInstant(), conflict.otherStartAt)
    }

    @Test
    fun `首尾相接的两场会不算冲突`() {
        val existing = timed("a", "第一场", at(15, 0), at(16, 0))
        val candidate = timed("b", "第二场", at(16, 0), at(17, 0))

        assertFalse(detect(candidate, existing = listOf(existing)).hasConflict)
    }

    @Test
    fun `不同日期的同一时间不算冲突`() {
        val existing = timed("a", "昨天的会", at(15, 0), at(16, 0))
        val candidate = timed(
            "b",
            "明天的会",
            at(15, 0, today.plusDays(1)),
            at(16, 0, today.plusDays(1)),
        )

        assertFalse(detect(candidate, existing = listOf(existing)).hasConflict)
    }

    @Test
    fun `全天行程与定时行程互不算冲突`() {
        val existing = timed("a", "下午的会", at(15, 0), at(16, 0))
        val candidate = allDay("b", "请一天假", today)

        assertFalse(detect(candidate, existing = listOf(existing)).hasConflict)
    }

    @Test
    fun `同一天的两条全天行程算冲突`() {
        val existing = allDay("a", "年假", today)
        val candidate = allDay("b", "出差", today)

        val report = detect(candidate, existing = listOf(existing))

        assertEquals(1, report.totalCount)
        assertTrue(report.conflicts.single().otherAllDay)
    }

    @Test
    fun `跨天的全天行程按覆盖的每一天比对`() {
        val existing = allDay("a", "出差", today.plusDays(2))
        val candidate = allDay("b", "培训", today, today.plusDays(3))

        val report = detect(candidate, existing = listOf(existing))

        assertEquals(1, report.totalCount)
        assertEquals(today.plusDays(2), report.conflicts.single().date)
    }

    @Test
    fun `编辑一条行程时不会和自己冲突`() {
        val same = timed("a", "项目评审会", at(15, 0), at(16, 0))

        assertFalse(detect(same, existing = listOf(same)).hasConflict)
    }

    @Test
    fun `已删除的行程不参与检测`() {
        val removed = timed("a", "删掉的会", at(15, 0), at(16, 0)).copy(deleted = true)
        val candidate = timed("b", "新会", at(15, 30), at(16, 30))

        assertFalse(detect(candidate, existing = listOf(removed)).hasConflict)
    }

    @Test
    fun `缺少结束时间的定时行程按一小时处理`() {
        val existing = timed("a", "短的会", at(15, 30), at(16, 30))
        val candidate = timed("b", "没说结束时长的会", at(15, 0), null)

        assertEquals(1, detect(candidate, existing = listOf(existing)).totalCount)
    }

    @Test
    fun `每周重复的行程会逐次与已有行程比对`() {
        // 每周一三五早上八点跑步，从 9 月 7 日（周一）开始。
        val candidate = timed(
            "a",
            "晨跑",
            at(8, 0, LocalDate.of(2026, 9, 7)),
            at(9, 0, LocalDate.of(2026, 9, 7)),
        )
        val rule = weeklyRule("a", DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

        // 9 月 9 日是周三，与早晨的跑步撞上；9 月 10 日是周四，不重复，不该报。
        val wednesday = timed(
            "b",
            "周三的早会",
            at(8, 30, LocalDate.of(2026, 9, 9)),
            at(9, 30, LocalDate.of(2026, 9, 9)),
        )
        val thursday = timed(
            "c",
            "周四的早会",
            at(8, 30, LocalDate.of(2026, 9, 10)),
            at(9, 30, LocalDate.of(2026, 9, 10)),
        )

        val report = detect(
            candidate = candidate,
            rule = rule,
            existing = listOf(wednesday, thursday),
            day = LocalDate.of(2026, 9, 1),
        )

        assertEquals(1, report.totalCount)
        assertEquals("周三的早会", report.conflicts.single().otherTitle)
        assertEquals(LocalDate.of(2026, 9, 9), report.conflicts.single().date)
    }

    @Test
    fun `重复行程只检查未来一段时间内的冲突`() {
        // 每天早上八点的会，从去年就开始了。
        val candidate = timed(
            "a",
            "早会",
            at(8, 0, today.minusMonths(8)),
            at(9, 0, today.minusMonths(8)),
        )
        val rule = dailyRule("a")

        val soon = timed("b", "十天后的一次体检", at(8, 30, today.plusDays(10)), at(9, 30, today.plusDays(10)))
        val faraway = timed("c", "四个月后的一次体检", at(8, 30, today.plusDays(120)), at(9, 30, today.plusDays(120)))

        assertEquals(1, detect(candidate, rule, listOf(soon)).totalCount)
        assertFalse(detect(candidate, rule, listOf(faraway)).hasConflict)
    }

    @Test
    fun `每天都撞时只展示最早几处但仍报出总数`() {
        val candidate = timed(
            "a",
            "早会",
            at(9, 0, today.minusDays(30)),
            at(10, 0, today.minusDays(30)),
        )
        val existing = timed(
            "b",
            "另一个早会",
            at(9, 30, today.minusDays(30)),
            at(10, 30, today.minusDays(30)),
        )

        val report = detect(candidate, dailyRule("a"), listOf(existing), listOf(dailyRule("b")))

        // 从今天起共 91 天（含今天），每天一处。
        assertEquals(91, report.totalCount)
        assertEquals(ConflictDetector.MAX_REPORTED, report.conflicts.size)
        assertEquals(today, report.conflicts.first().date)
        assertTrue(report.conflicts.first().date < report.conflicts.last().date)
    }

    @Test
    fun `同一条行程同一天只报一处冲突`() {
        // 两条都跨三天的全天行程，重叠的是三个连续的日期。
        val candidate = allDay("a", "培训", today, today.plusDays(2))
        val existing = allDay("b", "出差", today, today.plusDays(2))

        val report = detect(candidate, existing = listOf(existing))

        assertEquals(3, report.totalCount)
        assertEquals(3, report.conflicts.size)
    }

    private fun detect(
        candidate: EventEntity,
        rule: RecurrenceRuleEntity? = null,
        existing: List<EventEntity>,
        existingRules: List<RecurrenceRuleEntity> = emptyList(),
        day: LocalDate = today,
    ): ConflictReport = ConflictDetector.detect(
        candidate = candidate,
        candidateRule = rule,
        existing = existing,
        existingRules = existingRules,
        zone = zone,
        weekStartDay = DayOfWeek.MONDAY,
        today = day,
    )

    private fun at(hour: Int, minute: Int = 0, day: LocalDate = today): LocalDateTime =
        LocalDateTime.of(day, LocalTime.of(hour, minute))

    private fun timed(
        id: String,
        title: String,
        start: LocalDateTime,
        end: LocalDateTime? = start.plusHours(1),
    ): EventEntity = EventEntity(
        id = id,
        title = title,
        allDay = false,
        startAt = start.atZone(zone).toInstant().toEpochMilli(),
        endAt = end?.atZone(zone)?.toInstant()?.toEpochMilli(),
        timeZone = zone.id,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun allDay(
        id: String,
        title: String,
        from: LocalDate,
        to: LocalDate = from,
    ): EventEntity = EventEntity(
        id = id,
        title = title,
        allDay = true,
        startEpochDay = from.toEpochDay(),
        endEpochDay = to.toEpochDay(),
        timeZone = zone.id,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun dailyRule(eventId: String): RecurrenceRuleEntity = RecurrenceRuleEntity(
        id = "rule-$eventId",
        eventId = eventId,
        frequency = RecurrenceRuleEntity.FREQUENCY_DAILY,
    )

    private fun weeklyRule(eventId: String, vararg days: DayOfWeek): RecurrenceRuleEntity =
        RecurrenceRuleEntity(
            id = "rule-$eventId",
            eventId = eventId,
            frequency = RecurrenceRuleEntity.FREQUENCY_WEEKLY,
            byDay = DayOfWeekCodes.format(days.toList()),
        )
}
