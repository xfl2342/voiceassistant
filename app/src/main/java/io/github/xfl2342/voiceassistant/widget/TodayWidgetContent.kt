package io.github.xfl2342.voiceassistant.widget

import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.data.db.RecurrenceRuleEntity
import io.github.xfl2342.voiceassistant.domain.DayOfWeekCodes
import io.github.xfl2342.voiceassistant.domain.EventOccurrence
import io.github.xfl2342.voiceassistant.domain.RecurrenceExpander
import io.github.xfl2342.voiceassistant.ui.components.EventFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 小组件上的一行：一条行程。 */
data class TodayWidgetRow(
    val eventId: String,
    /** 左列的时间：「09:00」；全天行程是「全天」。 */
    val timeLabel: String,
    val title: String,
    /** 急迫程度（high / normal / low），小组件按它给左边的小竖条上色。 */
    val urgency: String,
)

/** 小组件这一屏要显示的全部内容：文字都排好了，画的时候不需要再算。 */
data class TodayWidgetContent(
    /** 日期标题，例：`9 月 26 日 周六`。 */
    val dateLabel: String,
    val rows: List<TodayWidgetRow>,
    /** 一屏放不下、被省略掉的条数；0 表示都放下了。 */
    val overflow: Int,
    /** 今天没有安排时显示的那句话；有安排时为 null。 */
    val emptyLabel: String?,
    /** 今天没有安排时，顺带说一句下一次是什么时候；往后也找不到时为 null。 */
    val nextLabel: String?,
)

/**
 * 把「行程 + 重复规则」排成小组件要显示的一屏。
 *
 * 这一层刻意不碰安卓的任何东西（没有 Context、没有 RemoteViews），
 * 好让「今天到底显示哪几条」这件事能直接被单元测试盯住——跨天、重复行程、
 * 放不下时省略几条，都是容易算错的地方。
 */
object TodayWidgetContentBuilder {

    /** 一屏最多放多少行。再多只是把小组件拉长，不如让用户点进应用看。 */
    const val MAX_ROWS = 8

    /** 今天没有安排时，往后找几天看看下一次是什么时候。 */
    private const val LOOKAHEAD_DAYS = 7L

    /** 全天行程在左列显示的字。 */
    const val ALL_DAY = "全天"

    /** 今天没有安排时的那句话。写成常量是为了让测试和界面说的是同一句话。 */
    const val EMPTY_TODAY = "今天没有安排"

    fun build(
        events: List<EventEntity>,
        rules: List<RecurrenceRuleEntity>,
        today: LocalDate,
        zone: ZoneId,
        weekStartDay: DayOfWeek = DayOfWeek.MONDAY,
        maxRows: Int = MAX_ROWS,
    ): TodayWidgetContent {
        val dateLabel = "${EventFormat.shortDate(today)} ${DayOfWeekCodes.label(today.dayOfWeek)}"
        val occurrences =
            RecurrenceExpander.expand(events, rules, today, today, zone, weekStartDay)

        if (occurrences.isEmpty()) {
            // 今天空着时，顺手说一句下一次是什么时候——这是「今天没安排」之外
            // 用户最想知道的一件事。只看未来一周：更远的事情提前摆在桌面上反而是噪音。
            val next = RecurrenceExpander
                .expand(
                    events = events,
                    rules = rules,
                    from = today.plusDays(1),
                    to = today.plusDays(LOOKAHEAD_DAYS),
                    zone = zone,
                    weekStartDay = weekStartDay,
                )
                .firstOrNull()

            return TodayWidgetContent(
                dateLabel = dateLabel,
                rows = emptyList(),
                overflow = 0,
                emptyLabel = EMPTY_TODAY,
                nextLabel = next?.let { "下一条：${describe(it, today)}" },
            )
        }

        val shown = occurrences.take(maxRows.coerceAtLeast(1))
        return TodayWidgetContent(
            dateLabel = dateLabel,
            rows = shown.map(::row),
            overflow = occurrences.size - shown.size,
            emptyLabel = null,
            nextLabel = null,
        )
    }

    private fun row(occurrence: EventOccurrence): TodayWidgetRow = TodayWidgetRow(
        eventId = occurrence.eventId,
        timeLabel = if (occurrence.allDay) ALL_DAY else timeLabel(occurrence.startAt),
        title = occurrence.title,
        urgency = occurrence.urgency,
    )

    /** 「明天 10:00 项目评审会」，今天之外的日期补上日期与星期。 */
    private fun describe(occurrence: EventOccurrence, today: LocalDate): String {
        val day = if (occurrence.date == today.plusDays(1)) {
            "明天"
        } else {
            "${EventFormat.shortDate(occurrence.date)} ${DayOfWeekCodes.label(occurrence.date.dayOfWeek)}"
        }
        val time = if (occurrence.allDay) ALL_DAY else timeLabel(occurrence.startAt)
        return "$day $time ${occurrence.title}"
    }

    /**
     * 时间文字与详情页共用 [EventFormat]，时区都是 `Asia/Shanghai`，两边不会对不上。
     *
     * 定时行程展开后一定有开始时间；万一没有，就按全天显示，总比空一格强。
     */
    private fun timeLabel(startAt: Instant?): String =
        startAt?.let(EventFormat::time) ?: ALL_DAY
}

/**
 * 小组件的行高账。
 *
 * 桌面上的格子数是用户定的，能放几行就得按当前高度算：算多了会被裁掉半行，
 * 难看且看不出少了什么；算少了则白白空着。这里把「除行程行之外的固定高度」
 * 和「一行多高」写死，跟 `widget_today.xml`、`widget_row.xml` 里的尺寸对应。
 */
private const val WIDGET_CHROME_DP = 99
private const val WIDGET_ROW_DP = 26
private const val WIDGET_DEFAULT_ROWS = 3

/**
 * 按小组件当前高度能放几行行程。
 *
 * [minHeightDp] 是系统给的可用高度；拿不到时（返回 0 或负数）按默认行数处理。
 */
internal fun widgetRowBudget(minHeightDp: Int): Int {
    if (minHeightDp <= 0) return WIDGET_DEFAULT_ROWS
    return ((minHeightDp - WIDGET_CHROME_DP) / WIDGET_ROW_DP)
        .coerceIn(1, TodayWidgetContentBuilder.MAX_ROWS)
}
