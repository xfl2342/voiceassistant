package io.github.xfl2342.voiceassistant.domain

import java.time.DayOfWeek

/**
 * 星期与 iCalendar 代码（MO、TU…）之间的转换。
 *
 * 解析时同时接受 `MO` 与 `monday` 两种写法：前一种是我们自己存储的格式，
 * 后一种是模型直接返回的格式，两边都能吃下，省得来回转换出错。
 */
object DayOfWeekCodes {

    private val codeToDay: Map<String, DayOfWeek> = mapOf(
        "MO" to DayOfWeek.MONDAY,
        "TU" to DayOfWeek.TUESDAY,
        "WE" to DayOfWeek.WEDNESDAY,
        "TH" to DayOfWeek.THURSDAY,
        "FR" to DayOfWeek.FRIDAY,
        "SA" to DayOfWeek.SATURDAY,
        "SU" to DayOfWeek.SUNDAY,
    )

    private val dayToCode: Map<DayOfWeek, String> = codeToDay.entries.associate { (k, v) -> v to k }

    private val chineseLabels: Map<DayOfWeek, String> = mapOf(
        DayOfWeek.MONDAY to "周一",
        DayOfWeek.TUESDAY to "周二",
        DayOfWeek.WEDNESDAY to "周三",
        DayOfWeek.THURSDAY to "周四",
        DayOfWeek.FRIDAY to "周五",
        DayOfWeek.SATURDAY to "周六",
        DayOfWeek.SUNDAY to "周日",
    )

    /** 解析形如 `MO,WE,FR` 或 `monday,wednesday` 的字符串；无法识别的片段忽略。 */
    fun parse(csv: String?): Set<DayOfWeek> {
        if (csv.isNullOrBlank()) return emptySet()
        return csv.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { token -> codeToDay[token.uppercase()] ?: dayFromEnglishName(token) }
            .toSet()
    }

    /** 转成存储用格式，按周一到周日排序。 */
    fun format(days: Collection<DayOfWeek>): String =
        days.sortedBy { it.value }.joinToString(",") { dayToCode.getValue(it) }

    fun label(day: DayOfWeek): String = chineseLabels.getValue(day)

    /** 用于「每周一、三、五」这类描述。 */
    fun labels(days: Collection<DayOfWeek>): String =
        days.sortedBy { it.value }.joinToString("、") { label(it) }

    private fun dayFromEnglishName(token: String): DayOfWeek? = when (token.lowercase()) {
        "monday", "mon" -> DayOfWeek.MONDAY
        "tuesday", "tue" -> DayOfWeek.TUESDAY
        "wednesday", "wed" -> DayOfWeek.WEDNESDAY
        "thursday", "thu" -> DayOfWeek.THURSDAY
        "friday", "fri" -> DayOfWeek.FRIDAY
        "saturday", "sat" -> DayOfWeek.SATURDAY
        "sunday", "sun" -> DayOfWeek.SUNDAY
        else -> null
    }
}
