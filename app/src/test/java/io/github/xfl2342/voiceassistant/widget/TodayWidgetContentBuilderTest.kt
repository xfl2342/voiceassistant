package io.github.xfl2342.voiceassistant.widget

import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.data.db.RecurrenceRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 桌面小组件的排版测试。
 *
 * 这一层是给「今天」那一小块算字的：今天有哪些行程、放不下几条、今天空着时
 * 说一句什么。日期边界与省略条数最容易出错，所以单独覆盖。
 * 基准日期是 2026-09-26（周六）。
 */
class TodayWidgetContentBuilderTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 9, 26)

    @Test
    fun `今天的行程按时间先后列出`() {
        val afternoon = timedEvent(at(today, 15), at(today, 16), title = "项目评审会")
        val morning = timedEvent(at(today, 9), at(today, 10), title = "晨会", id = "morning")

        val content = build(listOf(afternoon, morning))

        assertEquals(listOf("09:00", "15:00"), content.rows.map { it.timeLabel })
        assertEquals(listOf("晨会", "项目评审会"), content.rows.map { it.title })
        assertEquals(0, content.overflow)
    }

    @Test
    fun `全天行程排在定时行程前面并显示全天`() {
        val allDay = allDayEvent(today, title = "请一天假")
        val timed = timedEvent(at(today, 9), at(today, 10), title = "晨会")

        val content = build(listOf(timed, allDay))

        assertEquals(listOf(TodayWidgetContentBuilder.ALL_DAY, "09:00"), content.rows.map { it.timeLabel })
        assertEquals("请一天假", content.rows.first().title)
    }

    @Test
    fun `日期标题带上星期`() {
        val content = build(emptyList())

        assertEquals("9 月 26 日 周六", content.dateLabel)
    }

    @Test
    fun `放不下时只显示前几条并说明还差几条`() {
        val events = listOf(
            timedEvent(at(today, 9), at(today, 10), title = "晨会", id = "a"),
            timedEvent(at(today, 11), at(today, 12), title = "面谈", id = "b"),
            timedEvent(at(today, 15), at(today, 16), title = "评审", id = "c"),
        )

        val content = build(events, maxRows = 2)

        assertEquals(listOf("晨会", "面谈"), content.rows.map { it.title })
        assertEquals(1, content.overflow)
    }

    @Test
    fun `今天空着时提示下一次是什么时候`() {
        val tomorrow = timedEvent(at(today.plusDays(1), 10), at(today.plusDays(1), 11), title = "体检")

        val content = build(listOf(tomorrow))

        assertTrue(content.rows.isEmpty())
        assertEquals(TodayWidgetContentBuilder.EMPTY_TODAY, content.emptyLabel)
        assertEquals("下一条：明天 10:00 体检", content.nextLabel)
    }

    @Test
    fun `更远的下一条带上日期与星期`() {
        val monday = today.plusDays(2)
        val event = timedEvent(at(monday, 19), at(monday, 20), title = "游泳")

        val content = build(listOf(event))

        assertEquals("下一条：9 月 28 日 周一 19:00 游泳", content.nextLabel)
    }

    @Test
    fun `一周之内都没有安排时就不再往后提示`() {
        val far = timedEvent(at(today.plusDays(14), 10), at(today.plusDays(14), 11), title = "出差")

        val content = build(listOf(far))

        assertEquals(TodayWidgetContentBuilder.EMPTY_TODAY, content.emptyLabel)
        assertNull(content.nextLabel)
    }

    @Test
    fun `重复行程今天该出现就出现`() {
        val event = timedEvent(at(LocalDate.of(2026, 9, 1), 8), at(LocalDate.of(2026, 9, 1), 9), id = "daily")
        val rule = dailyRule(event.id)

        val content = build(listOf(event), rules = listOf(rule))

        assertEquals(listOf("08:00"), content.rows.map { it.timeLabel })
    }

    @Test
    fun `急迫程度会带到小组件上`() {
        val event = timedEvent(at(today, 9), at(today, 10)).copy(urgency = EventEntity.URGENCY_HIGH)

        val content = build(listOf(event))

        assertEquals(EventEntity.URGENCY_HIGH, content.rows.single().urgency)
    }

    @Test
    fun `已删除的行程不上小组件`() {
        val event = timedEvent(at(today, 9), at(today, 10)).copy(deleted = true)

        val content = build(listOf(event))

        assertTrue(content.rows.isEmpty())
        assertEquals(TodayWidgetContentBuilder.EMPTY_TODAY, content.emptyLabel)
    }

    @Test
    fun `一天太多条时按上限截断`() {
        val events = (0..11).map { hour ->
            timedEvent(
                at(today, hour),
                at(today, hour.plus(1)),
                id = "event-$hour",
            )
        }

        val content = build(events)

        assertEquals(TodayWidgetContentBuilder.MAX_ROWS, content.rows.size)
        assertEquals(4, content.overflow)
    }

    @Test
    fun `拿不到桌面高度时按默认行数`() {
        assertEquals(3, widgetRowBudget(0))
    }

    @Test
    fun `高度只够一行时至少留一行`() {
        assertEquals(1, widgetRowBudget(110))
    }

    @Test
    fun `高度够时按行高算出能放几行`() {
        // 4×3 的小组件大约 203dp 高：刨掉标题和「还有几条」，剩下四行。
        assertEquals(4, widgetRowBudget(203))
    }

    @Test
    fun `拉得很高也不会超过上限`() {
        assertEquals(TodayWidgetContentBuilder.MAX_ROWS, widgetRowBudget(2000))
    }

    private fun build(
        events: List<EventEntity>,
        rules: List<RecurrenceRuleEntity> = emptyList(),
        maxRows: Int = TodayWidgetContentBuilder.MAX_ROWS,
    ): TodayWidgetContent = TodayWidgetContentBuilder.build(
        events = events,
        rules = rules,
        today = today,
        zone = zone,
        maxRows = maxRows,
    )

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant =
        LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant()

    private fun timedEvent(
        start: Instant,
        end: Instant,
        title: String = "测试行程",
        id: String = "event",
    ): EventEntity = EventEntity(
        id = id,
        title = title,
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
        title: String = "全天行程",
        id: String = "allday",
    ): EventEntity = EventEntity(
        id = id,
        title = title,
        allDay = true,
        startEpochDay = start.toEpochDay(),
        endEpochDay = end.toEpochDay(),
        timeZone = zone.id,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun dailyRule(eventId: String): RecurrenceRuleEntity = RecurrenceRuleEntity(
        id = "rule-$eventId",
        eventId = eventId,
        frequency = RecurrenceRuleEntity.FREQUENCY_DAILY,
        interval = 1,
        endType = RecurrenceRuleEntity.END_TYPE_NEVER,
    )
}
