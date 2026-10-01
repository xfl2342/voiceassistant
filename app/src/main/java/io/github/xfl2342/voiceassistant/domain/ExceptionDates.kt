package io.github.xfl2342.voiceassistant.domain

import java.time.LocalDate

/**
 * 重复规则的例外日期（iCalendar 里的 EXDATE）。
 *
 * 存在数据库里是一串逗号分隔的 epochDay（跟 `byDay` 存 `MO,WE,FR` 一个路数），
 * 这里负责跟 [LocalDate] 之间来回转。
 *
 * 认不出来的片段直接跳过：这一列坏了顶多是某几天没跳过，不该让整条规则展开不了。
 */
object ExceptionDates {

    fun parse(text: String?): Set<LocalDate> {
        if (text.isNullOrBlank()) return emptySet()
        return text.split(',')
            .mapNotNull { it.trim().takeIf(String::isNotEmpty)?.toLongOrNull() }
            .map(LocalDate::ofEpochDay)
            .toSet()
    }

    /** 写成存库用的文本；一天都没有时给 null，免得留个空串。 */
    fun format(dates: Set<LocalDate>): String? =
        if (dates.isEmpty()) null else dates.sorted().joinToString(",") { it.toEpochDay().toString() }

    /** 在已有的例外里再加一天；已经有了就原样返回。 */
    fun add(text: String?, date: LocalDate): String? =
        format(parse(text) + date)
}
