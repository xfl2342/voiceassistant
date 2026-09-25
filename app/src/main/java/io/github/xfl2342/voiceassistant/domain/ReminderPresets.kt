package io.github.xfl2342.voiceassistant.domain

/**
 * 提醒预设：按事情的急迫程度给出默认的提前提醒时间。
 *
 * 模型只返回 high / normal / low 三档，这里映射到最接近的预设；
 * 用户也可以在确认界面上换成别的档。
 */
enum class ReminderPreset(
    val label: String,
    val minutesBefore: Int,
    val description: String,
) {
    URGENT("紧急", 15, "临时会议、马上要处理的事"),
    SOON("较急", 60, "当天稍后要做的事"),
    NORMAL("常规", 24 * 60, "需要提前过一遍的事"),
    FAR("远期", 2 * 24 * 60, "需要提前准备的行程"),
    ;

    companion object {

        fun fromUrgency(urgency: String): ReminderPreset = when (urgency.lowercase()) {
            "high" -> URGENT
            "low" -> FAR
            else -> NORMAL
        }

        fun fromMinutes(minutes: Int): ReminderPreset =
            entries.minByOrNull { kotlin.math.abs(it.minutesBefore - minutes) } ?: NORMAL

        /** 把分钟数说成人话：「提前 15 分钟」「提前 1 天」。 */
        fun describe(minutes: Int): String = when {
            minutes <= 0 -> "不提醒"
            minutes < 60 -> "提前 $minutes 分钟"
            minutes < 24 * 60 -> "提前 ${minutes / 60} 小时"
            else -> "提前 ${minutes / (24 * 60)} 天"
        }
    }
}
