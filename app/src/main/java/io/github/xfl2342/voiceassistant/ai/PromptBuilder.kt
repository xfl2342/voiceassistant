package io.github.xfl2342.voiceassistant.ai

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 构造发给 DeepSeek 的提示词。
 *
 * 这里最关键的一点是**把当前时间喂给模型**：像「明天」「下周三」「今晚」这类说法，
 * 模型只有知道「现在是什么时候、今天星期几」才算得对日期。这一步漏掉，
 * 相对时间会大面积解析错误。
 */
object PromptBuilder {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss EEEE")

    /** 给模型的时间基准；日后支持设置时区时从这里改。 */
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    fun systemPrompt(now: ZonedDateTime = ZonedDateTime.now(zone)): String {
        val currentTime = now.format(dateTimeFormatter)
        return """
            你是日程解析助手，负责把用户说的中文口语转成 json。

            当前时间：$currentTime
            时区：Asia/Shanghai

            输出要求：
            1. 只输出一个 json 对象，不要输出解释文字，不要用 markdown 代码块包裹。
            2. 用户没有提到的信息不要编造：地点、备注留空字符串；确实无法确定的时间返回 null。
            3. 时间用 ISO 8601 并带时区偏移，例如 2026-09-26T15:00:00+08:00。
            4. 用户只说了开始时间时，默认时长 1 小时，并补全 end。
            5. 全天行程（例如「下周三请一天假」）把 all_day 设为 true，此时 start 与 end
               用日期表示，例如 2026-10-01，不带时间部分。
            6. 用户明确说了提醒时间就填进 reminders；没说则根据事情的急迫程度给出
               urgency，取值只能是 high、normal、low 之一。
            7. 无法确定的字段名放进 missing_fields；confidence 是 0 到 1 之间的小数。

            字段格式如下：
            {
              "title": "行程标题",
              "start": "开始时间或日期",
              "end": "结束时间或日期",
              "all_day": false,
              "location": "",
              "notes": "",
              "reminders": [{"type": "before", "minutes": 15}],
              "recurrence": {
                "frequency": "daily 或 weekly，没有则 null",
                "interval": 1,
                "by_day": ["monday", "wednesday"],
                "end_type": "never 或 on_date",
                "end_date": null
              },
              "urgency": "normal",
              "confidence": 0.9,
              "missing_fields": []
            }
        """.trimIndent()
    }

    fun userPrompt(text: String): String = "请把这句话解析成日程：$text"
}
