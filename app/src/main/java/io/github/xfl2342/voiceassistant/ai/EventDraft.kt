package io.github.xfl2342.voiceassistant.ai

import org.json.JSONObject

/**
 * 模型解析出来的行程草稿。
 *
 * 这是「AI 的产出」与「真正入库的行程」之间的中间形态：先在这里做结构与取值校验，
 * 用户确认之后再转换成数据库实体。这样即使模型返回脏数据，也不会污染数据库。
 */
data class EventDraft(
    val title: String,
    val start: String,
    val end: String,
    val allDay: Boolean,
    val location: String,
    val urgency: String,
    val confidence: Double,
    val missingFields: List<String>,
    val reminderMinutes: List<Int>,
    val recurrenceText: String?,
    val rawJson: String,
    /** 重复规则的原始字段，保存时用来生成规则实体。 */
    val recurrenceFrequency: String? = null,
    val recurrenceByDay: List<String> = emptyList(),
    val recurrenceEndDate: String? = null,
) {

    /** 需要提醒用户留意的地方。 */
    val warnings: List<String>
        get() = buildList {
            if (title.isBlank()) add("标题缺失")
            if (start.isBlank() || start == "null") add("开始时间缺失")
            if (missingFields.isNotEmpty()) {
                add("模型标注缺失：" + missingFields.joinToString("、"))
            }
            if (confidence < LOW_CONFIDENCE) add("置信度较低（$confidence）")
        }

    companion object {

        private const val LOW_CONFIDENCE = 0.6

        /**
         * 从模型返回的文本解析出行程草稿。
         *
         * 模型偶尔会用 markdown 代码块把 json 包起来，这里做一次容错剥离。
         */
        fun parse(content: String): Result<EventDraft> = runCatching {
            val json = JSONObject(stripCodeFence(content))
            val recurrence = json.optJSONObject("recurrence")

            EventDraft(
                title = json.optString("title").orEmpty(),
                start = json.optString("start").orEmpty(),
                end = json.optString("end").orEmpty(),
                allDay = json.optBoolean("all_day", false),
                location = json.optString("location").orEmpty(),
                urgency = json.optString("urgency", "normal").orEmpty(),
                confidence = json.optDouble("confidence", 0.0),
                missingFields = json.stringList("missing_fields"),
                reminderMinutes = json.optJSONArray("reminders")
                    ?.let { array ->
                        (0 until array.length()).mapNotNull { index ->
                            array.optJSONObject(index)
                                ?.takeIf { it.optString("type") == "before" }
                                ?.optInt("minutes")
                        }
                    }
                    .orEmpty(),
                recurrenceText = recurrence
                    ?.takeIf { it.optString("frequency").let { f -> f.isNotBlank() && f != "null" } }
                    ?.let { describeRecurrence(it) },
                rawJson = json.toString(2),
                recurrenceFrequency = recurrence
                    ?.optString("frequency")
                    ?.takeIf { it.isNotBlank() && it != "null" },
                recurrenceByDay = recurrence?.stringList("by_day").orEmpty(),
                recurrenceEndDate = recurrence
                    ?.optString("end_date")
                    ?.takeIf { it.isNotBlank() && it != "null" },
            )
        }

        private fun JSONObject.stringList(key: String): List<String> =
            optJSONArray(key)?.let { array -> (0 until array.length()).map { array.optString(it) } }
                .orEmpty()

        private fun describeRecurrence(rule: JSONObject): String {
            val frequency = rule.optString("frequency")
            val days = rule.stringList("by_day")
            return when {
                days.isNotEmpty() -> "每周 " + days.joinToString("、") { dayOfWeekCn(it) }
                frequency == "daily" -> "每天"
                frequency == "weekly" -> "每周"
                else -> frequency
            }
        }

        private fun stripCodeFence(text: String): String {
            val trimmed = text.trim()
            if (!trimmed.startsWith("```")) return trimmed
            return trimmed
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        }

        private fun dayOfWeekCn(value: String): String = when (value.lowercase()) {
            "monday" -> "周一"
            "tuesday" -> "周二"
            "wednesday" -> "周三"
            "thursday" -> "周四"
            "friday" -> "周五"
            "saturday" -> "周六"
            "sunday" -> "周日"
            else -> value
        }
    }
}
