package io.github.xfl2342.voiceassistant.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一条行程。
 *
 * 定时行程与全天行程用两套字段表示，不要混用：
 * - 定时行程用 [startAt] / [endAt]，存 UTC 毫秒；
 * - 全天行程用 [startEpochDay] / [endEpochDay]，存本地日期的 epochDay。
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

    /** 用户的原话，保留下来便于日后回溯解析是否出错。 */
    val rawText: String? = null,

    val createdAt: Long,
    val updatedAt: Long,

    /** 软删除标记：删除后仍保留数据，便于日后做撤销或回收站。 */
    val deleted: Boolean = false,
) {
    companion object {
        const val DEFAULT_TIME_ZONE = "Asia/Shanghai"
    }
}
