package io.github.xfl2342.voiceassistant.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 重复规则。一条行程最多一条规则。
 *
 * 字段命名刻意对齐 iCalendar 的 RRULE 子集，日后若要导出 .ics
 * 或与系统日历同步，不用再改数据模型。
 *
 * 本版只支持：每天（daily）、每周（weekly，可指定周几）。
 */
@Entity(
    tableName = "recurrence_rules",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["eventId"])],
)
data class RecurrenceRuleEntity(
    @PrimaryKey
    val id: String,
    val eventId: String,

    /** daily 或 weekly。 */
    val frequency: String,

    /** 间隔，本版固定为 1，字段保留以便日后支持「每两周」。 */
    val interval: Int = 1,

    /** 每周的哪几天，形如 `MO,WE,FR`；frequency 为 daily 时为 null。 */
    val byDay: String? = null,

    /** never 或 on_date。 */
    val endType: String = END_TYPE_NEVER,

    /** endType 为 on_date 时的结束日期（本地日期 epochDay）。 */
    val endEpochDay: Long? = null,

    /**
     * 例外日期（iCalendar 里的 EXDATE）：这几天跳过，不产生发生。
     *
     * 「每周三例会，但下周三那次不开了」只用记这一天，不必动规则本身。
     * 存法与 [byDay] 一样是逗号分隔的文本，只不过装的是本地日期的 epochDay。
     */
    val exceptionDates: String? = null,
) {
    companion object {
        const val FREQUENCY_DAILY = "daily"
        const val FREQUENCY_WEEKLY = "weekly"

        const val END_TYPE_NEVER = "never"
        const val END_TYPE_ON_DATE = "on_date"
    }
}
