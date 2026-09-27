package io.github.xfl2342.voiceassistant.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一条改进意见。
 *
 * 用途是「想到什么随手记一句」，攒到下次连电脑时一起改。所以这里刻意不做分类、
 * 不做优先级、不做标签，只留两件事：写了什么、处理了没有。字段一多，记的人就会
 * 开始犹豫，犹豫就不会记了。
 */
@Entity(tableName = "feedback")
data class FeedbackEntity(
    @PrimaryKey
    val id: String,

    /** 意见正文，允许换行。 */
    val content: String,

    /** 记录时间（UTC 毫秒）。 */
    val createdAt: Long,

    val updatedAt: Long,

    /** 是否已处理。处理完的挪到「已完成」，默认不占视线。 */
    val done: Boolean = false,
)
