package io.github.xfl2342.voiceassistant.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一条行程。
 *
 * 定时行程与全天行程用两套字段表示，不要混用：
 * - 定时行程用 [startAt] / [endAt]，存 UTC 毫秒；
 * - 全天行程用 [startEpochDay] / [endEpochDay]，存本地日期的 epochDay。
 * - 待办（[isTodo]）两组都留空：没有时间，也不该出现在日历上。
 *
 * 之所以把全天行程单独存成日期，是因为「10 月 1 日全天」一旦换算成时间点，
 * 在不同时区下可能变成 9 月 30 日或 10 月 2 日。用日期存就没有这个问题。
 */
@Entity(
    tableName = "events",
    indices = [
        Index(value = ["startAt"]),
        Index(value = ["startEpochDay"]),
        Index(value = ["deleted"]),
    ],
)
data class EventEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val allDay: Boolean,

    /** 定时行程的开始时间（UTC 毫秒）；全天行程为 null。 */
    val startAt: Long? = null,

    /** 定时行程的结束时间（UTC 毫秒）；全天行程为 null。 */
    val endAt: Long? = null,

    /** 全天行程的开始日期（本地日期 epochDay）；定时行程为 null。 */
    val startEpochDay: Long? = null,

    /** 全天行程的结束日期；单日全天行程与开始日期相同。 */
    val endEpochDay: Long? = null,

    val timeZone: String = DEFAULT_TIME_ZONE,
    val location: String? = null,
    val notes: String? = null,

    /**
     * 急迫程度：high / normal / low。
     *
     * 与提醒的提前量是两件事：这一项说的是「这件事多要紧」（决定日历上的颜色），
     * 提醒说的是「提前多久通知你」。AI 解析时会给出这一项，手动录入时自己选。
     */
    // 这里的默认值必须与 v2 → v3 迁移里那句 ALTER TABLE 完全一致，
    // 否则全新安装与升级安装会得到两份不同的表结构，Room 会在打开数据库时报错。
    @ColumnInfo(defaultValue = "normal")
    val urgency: String = URGENCY_NORMAL,

    /**
     * 是不是已经完成。
     *
     * 目前只有待办用得上：定时行程做完就过去了，全天行程也一样，都不需要打勾。
     * 待办没有时间，不打个勾就分不出「还没做」和「早就做完了」。
     */
    // 同上：默认值要与 v3 → v4 迁移里那句 ALTER TABLE 完全一致。
    @ColumnInfo(defaultValue = "0")
    val done: Boolean = false,

    /** 用户的原话，保留下来便于日后回溯解析是否出错。 */
    val rawText: String? = null,

    val createdAt: Long,
    val updatedAt: Long,

    /** 软删除标记：删除后仍保留数据，便于日后做撤销或回收站。 */
    val deleted: Boolean = false,
) {
    /**
     * 是不是一条不设时间的待办。
     *
     * 「没有时间」就写成两组时间字段都空：定时看 [startAt]，全天看 [startEpochDay]，
     * 两组都空自然就是「还没定时间，只是要记住有这么件事」。
     *
     * 不另加一个「类型」列，是免得同一个意思存两处、早晚打架。下游也不必特意照顾：
     * 重复展开那一层算不出锚点日期就不会产生任何一次发生，于是日历、桌面小组件、
     * 冲突检测都自动不认它。
     */
    val isTodo: Boolean
        get() = !allDay && startAt == null && startEpochDay == null

    companion object {
        const val DEFAULT_TIME_ZONE = "Asia/Shanghai"

        const val URGENCY_HIGH = "high"
        const val URGENCY_NORMAL = "normal"
        const val URGENCY_LOW = "low"

        /** 把模型或界面传来的写法规整到三档，认不出来的一律按「常规」。 */
        fun normalizeUrgency(value: String?): String = when (value?.trim()?.lowercase()) {
            URGENCY_HIGH -> URGENCY_HIGH
            URGENCY_LOW -> URGENCY_LOW
            else -> URGENCY_NORMAL
        }
    }
}
