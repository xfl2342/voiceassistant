package io.github.xfl2342.voiceassistant.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一条提醒。
 *
 * [triggerAt] 是已经算好的绝对触发时间：注册系统闹钟时直接用这个值，
 * 界面上要改提前量时也只需要重算这一个字段。
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["eventId"]),
        Index(value = ["triggerAt"]),
    ],
)
data class ReminderEntity(
    @PrimaryKey
    val id: String,
    val eventId: String,

    /** before（提前）或 at（准点）。 */
    val triggerType: String,

    /** 提前分钟数；triggerType 为 at 时为 null。 */
    val minutesBefore: Int? = null,

    /** 实际触发时间（UTC 毫秒）。 */
    val triggerAt: Long,
) {
    companion object {
        const val TYPE_BEFORE = "before"
        const val TYPE_AT = "at"
    }
}
