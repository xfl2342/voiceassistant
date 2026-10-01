package io.github.xfl2342.voiceassistant.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 重复规则的例外日期：存进数据库是逗号分隔的 epochDay，用的时候是 LocalDate。
 *
 * 这一列坏了顶多是某几天没跳过，不该让整条规则展开不了 —— 所以「认不出来就跳过」
 * 是刻意的，这里一并钉住。
 */
class ExceptionDatesTest {

    @Test
    fun `没写过例外时读出来是空的`() {
        assertEquals(emptySet<LocalDate>(), ExceptionDates.parse(null))
        assertEquals(emptySet<LocalDate>(), ExceptionDates.parse(""))
        assertEquals(emptySet<LocalDate>(), ExceptionDates.parse("   "))
    }

    @Test
    fun `写出去再读回来还是那几天`() {
        val days = setOf(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 7))

        val text = ExceptionDates.format(days)

        assertEquals(days, ExceptionDates.parse(text))
    }

    @Test
    fun `写的时候按日期排好序`() {
        val text = ExceptionDates.format(
            setOf(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 9, 30)),
        )

        val expected = "${LocalDate.of(2026, 9, 30).toEpochDay()}," +
            "${LocalDate.of(2026, 10, 7).toEpochDay()}"
        assertEquals(expected, text)
    }

    @Test
    fun `一天都没有时写 null 而不是空串`() {
        assertEquals(null, ExceptionDates.format(emptySet()))
    }

    @Test
    fun `同一天加两次只留一条`() {
        val day = LocalDate.of(2026, 9, 30)

        val once = ExceptionDates.add(null, day)
        val twice = ExceptionDates.add(once, day)

        assertEquals(once, twice)
        assertEquals(setOf(day), ExceptionDates.parse(twice))
    }

    @Test
    fun `认不出来的片段直接跳过`() {
        val day = LocalDate.of(2026, 9, 30)

        val parsed = ExceptionDates.parse("${day.toEpochDay()},坏数据,, 20697")

        assertEquals(setOf(day, LocalDate.ofEpochDay(20_697L)), parsed)
    }
}
