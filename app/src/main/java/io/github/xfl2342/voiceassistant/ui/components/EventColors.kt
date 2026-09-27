package io.github.xfl2342.voiceassistant.ui.components

import androidx.compose.ui.graphics.Color
import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.ui.theme.UrgencyHigh
import io.github.xfl2342.voiceassistant.ui.theme.UrgencyLow
import io.github.xfl2342.voiceassistant.ui.theme.UrgencyNormal

/**
 * 行程颜色：按「急迫程度」分三档，日历、列表、详情页共用同一套含义。
 *
 * 三档而不是四档，是因为急迫程度本来就只分了这么细：
 * AI 解析只返回 high / normal / low，手动录入也只需要「要不要标红」。
 * 提醒的提前量是另一件事，不参与上色。
 */
object EventColors {

    /** 行程的主色：紧急偏红、常规偏蓝、不急偏灰。 */
    fun accent(urgency: String): Color = when (urgency) {
        EventEntity.URGENCY_HIGH -> UrgencyHigh
        EventEntity.URGENCY_LOW -> UrgencyLow
        else -> UrgencyNormal
    }

    /** 中文说法，用于详情页与编辑页。 */
    fun label(urgency: String): String = when (urgency) {
        EventEntity.URGENCY_HIGH -> "紧急"
        EventEntity.URGENCY_LOW -> "不急"
        else -> "常规"
    }
}
