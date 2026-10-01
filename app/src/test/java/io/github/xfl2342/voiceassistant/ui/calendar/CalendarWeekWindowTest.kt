package io.github.xfl2342.voiceassistant.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 月历该画哪几周。
 *
 * 收起成一周之后，屏幕上能看到什么完全由这个函数决定：算错一天，露出来的就是
 * 别的周。基准取 2026 年 10 月（1 日是周四，按周一起始，网格从 9 月 28 日那周开始）。
 */
class CalendarWeekWindowTest {

    private val octoberStart: LocalDate = LocalDate.of(2026, 10, 1)

    @Test
    fun `展开时固定六行，从当月第一天所在的周首开始`() {
        val weeks = monthGridWeeks(
            monthStart = octoberStart,
            selectedDay = LocalDate.of(2026, 10, 15),
            weekStartDay = DayOfWeek.MONDAY,
            collapsed = false,
        )

        assertEquals(6, weeks.size)
        assertEquals(LocalDate.of(2026, 9, 28), weeks.first())
        assertEquals(LocalDate.of(2026, 11, 2), weeks.last())
        weeks.zipWithNext { first, second ->
            assertEquals("相邻两周应当正好差七天", 7L, second.toEpochDay() - first.toEpochDay())
        }
    }

    @Test
    fun `收起时只有选中日所在的那一周`() {
        val weeks = monthGridWeeks(
            monthStart = octoberStart,
            selectedDay = LocalDate.of(2026, 10, 15),
            weekStartDay = DayOfWeek.MONDAY,
            collapsed = true,
        )

        assertEquals(listOf(LocalDate.of(2026, 10, 12)), weeks)
    }

    @Test
    fun `跨月的那一周也要算对`() {
        // 10 月 1 日是周四，它那一周从 9 月 28 日（周一）开始。
        val weeks = monthGridWeeks(
            monthStart = octoberStart,
            selectedDay = LocalDate.of(2026, 10, 1),
            weekStartDay = DayOfWeek.MONDAY,
            collapsed = true,
        )

        assertEquals(listOf(LocalDate.of(2026, 9, 28)), weeks)
    }

    @Test
    fun `每周起始日换成周日时按周日算`() {
        val weeks = monthGridWeeks(
            monthStart = octoberStart,
            selectedDay = LocalDate.of(2026, 10, 1),
            weekStartDay = DayOfWeek.SUNDAY,
            collapsed = true,
        )

        assertEquals(listOf(LocalDate.of(2026, 9, 27)), weeks)
    }

    @Test
    fun `展开的六行要盖得住这个月的最后一天`() {
        val weeks = monthGridWeeks(
            monthStart = octoberStart,
            selectedDay = LocalDate.of(2026, 10, 31),
            weekStartDay = DayOfWeek.MONDAY,
            collapsed = false,
        )
        val lastCoveredDay = weeks.last().plusDays(6)

        assertTrue(
            "网格最后一行只到 $lastCoveredDay，盖不住 10 月 31 日",
            !lastCoveredDay.isBefore(LocalDate.of(2026, 10, 31)),
        )
    }

    @Test
    fun `标题在跨月的周里写两边的日期`() {
        assertEquals("9/28 – 10/4", weekRangeLabel(LocalDate.of(2026, 9, 28)))
        assertEquals("10/5 – 10/11", weekRangeLabel(LocalDate.of(2026, 10, 5)))
    }
}
