package io.github.xfl2342.voiceassistant.domain

import io.github.xfl2342.voiceassistant.ai.EventDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 「模型输出 → 数据库实体」的转换测试。
 *
 * 这一步的输入是模型生成的文本，格式随时可能变形，所以要把各种形态都钉死：
 * 带时区的时间、只有日期、缺结束时间、用户没说提醒等等。
 */
class EventDraftMapperTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun `定时行程按给定时间解析并补全默认时长`() {
        val draft = draft(
            title = "项目评审会",
            start = "2026-09-26T15:00:00+08:00",
            end = "",
        )

        val bundle = EventDraftMapper.toBundle(draft, rawText = "明天下午三点项目评审会", zone = zone)
            .getOrThrow()

        assertEquals(atEpochMillis(2026, 9, 26, 15, 0), bundle.event.startAt)
        assertEquals(atEpochMillis(2026, 9, 26, 16, 0), bundle.event.endAt)
        assertEquals("Asia/Shanghai", bundle.event.timeZone)
        assertEquals("明天下午三点项目评审会", bundle.event.rawText)
    }

    @Test
    fun `全天行程按日期存而不是时间戳`() {
        val draft = draft(
            title = "年假",
            start = "2026-10-01",
            end = "2026-10-01",
            allDay = true,
        )

        val bundle = EventDraftMapper.toBundle(draft, rawText = null, zone = zone).getOrThrow()

        assertTrue(bundle.event.allDay)
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay(), bundle.event.startEpochDay)
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay(), bundle.event.endEpochDay)
        assertEquals(null, bundle.event.startAt)
    }

    @Test
    fun `用户明确说了提醒时间时按原话设置`() {
        val draft = draft(
            title = "开会",
            start = "2026-09-26T15:00:00+08:00",
            end = "",
            reminderMinutes = listOf(15),
        )

        val bundle = EventDraftMapper.toBundle(draft, rawText = null, zone = zone).getOrThrow()

        val reminder = bundle.reminders.single()
        assertEquals(15, reminder.minutesBefore)
        assertEquals(atEpochMillis(2026, 9, 26, 14, 45), reminder.triggerAt)
    }

    @Test
    fun `用户没说提醒时按急迫程度取默认值`() {
        val urgent = draft(
            title = "临时会议",
            start = "2026-09-26T15:00:00+08:00",
            end = "",
            urgency = "high",
        )
        val farAway = urgent.copy(urgency = "low")

        val urgentBundle = EventDraftMapper.toBundle(urgent, null, zone).getOrThrow()
        val farBundle = EventDraftMapper.toBundle(farAway, null, zone).getOrThrow()

        assertEquals(ReminderPreset.URGENT.minutesBefore, urgentBundle.reminders.single().minutesBefore)
        assertEquals(ReminderPreset.FAR.minutesBefore, farBundle.reminders.single().minutesBefore)
    }

    @Test
    fun `全天行程的提醒以当天早上九点为锚点`() {
        val draft = draft(
            title = "交材料",
            start = "2026-10-01",
            end = "2026-10-01",
            allDay = true,
            reminderMinutes = listOf(15),
        )

        val bundle = EventDraftMapper.toBundle(draft, null, zone).getOrThrow()

        // 10-01 09:00 往前 15 分钟 = 08:45
        assertEquals(atEpochMillis(2026, 10, 1, 8, 45), bundle.reminders.single().triggerAt)
    }

    @Test
    fun `全天行程提前两天提醒时落在两天前的早上九点前`() {
        val draft = draft(
            title = "体检",
            start = "2026-10-10",
            end = "2026-10-10",
            allDay = true,
            reminderMinutes = listOf(2 * 24 * 60),
        )

        val bundle = EventDraftMapper.toBundle(draft, null, zone).getOrThrow()

        assertEquals(atEpochMillis(2026, 10, 8, 9, 0), bundle.reminders.single().triggerAt)
    }

    @Test
    fun `每周重复的英文星期名会被转成存储格式`() {
        val draft = draft(
            title = "晨跑",
            start = "2026-09-07T07:00:00+08:00",
            end = "2026-09-07T08:00:00+08:00",
            recurrenceFrequency = "weekly",
            recurrenceByDay = listOf("monday", "wednesday", "friday"),
        )

        val bundle = EventDraftMapper.toBundle(draft, null, zone).getOrThrow()

        val rule = bundle.rule!!
        assertEquals("weekly", rule.frequency)
        assertEquals("MO,WE,FR", rule.byDay)
        assertEquals(1, rule.interval)
    }

    @Test
    fun `每天重复且没有结束日期时结束方式为永不结束`() {
        val draft = draft(
            title = "吃药",
            start = "2026-09-07T08:00:00+08:00",
            end = "",
            recurrenceFrequency = "daily",
        )

        val rule = EventDraftMapper.toBundle(draft, null, zone).getOrThrow().rule!!

        assertEquals("daily", rule.frequency)
        assertEquals(null, rule.byDay)
        assertEquals("never", rule.endType)
    }

    @Test
    fun `不支持的重复频率不会生成规则`() {
        val draft = draft(
            title = "交房租",
            start = "2026-09-07T08:00:00+08:00",
            end = "",
            recurrenceFrequency = "monthly",
        )

        assertEquals(null, EventDraftMapper.toBundle(draft, null, zone).getOrThrow().rule)
    }

    @Test
    fun `标题为空时保存失败`() {
        val draft = draft(title = "", start = "2026-09-26T15:00:00+08:00", end = "")

        assertTrue(EventDraftMapper.toBundle(draft, null, zone).isFailure)
    }

    @Test
    fun `急迫程度会跟着行程一起存下来`() {
        val draft = draft(
            title = "临时会议",
            start = "2026-09-26T15:00:00+08:00",
            end = "",
            urgency = "high",
        )

        val bundle = EventDraftMapper.toBundle(draft, null, zone).getOrThrow()

        assertEquals("high", bundle.event.urgency)
    }

    @Test
    fun `模型给了认不出的急迫程度时按常规处理`() {
        val draft = draft(
            title = "收拾桌子",
            start = "2026-09-26T15:00:00+08:00",
            end = "",
            urgency = "非常急",
        )

        assertEquals(
            "normal",
            EventDraftMapper.toBundle(draft, null, zone).getOrThrow().event.urgency,
        )
    }

    @Test
    fun `时间看不懂时保存失败而不是写入脏数据`() {
        val draft = draft(title = "开会", start = "下周找个时间", end = "")

        val result = EventDraftMapper.toBundle(draft, null, zone)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("开始时间") == true)
    }

    private fun atEpochMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long = LocalDateTime.of(year, month, day, hour, minute)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private fun draft(
        title: String,
        start: String,
        end: String,
        allDay: Boolean = false,
        location: String = "",
        urgency: String = "normal",
        reminderMinutes: List<Int> = emptyList(),
        recurrenceFrequency: String? = null,
        recurrenceByDay: List<String> = emptyList(),
    ): EventDraft = EventDraft(
        title = title,
        start = start,
        end = end,
        allDay = allDay,
        location = location,
        urgency = urgency,
        confidence = 0.9,
        missingFields = emptyList(),
        reminderMinutes = reminderMinutes,
        recurrenceText = null,
        rawJson = "{}",
        recurrenceFrequency = recurrenceFrequency,
        recurrenceByDay = recurrenceByDay,
    )
}
