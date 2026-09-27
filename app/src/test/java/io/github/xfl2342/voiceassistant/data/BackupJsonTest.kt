package io.github.xfl2342.voiceassistant.data

import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.data.db.FeedbackEntity
import io.github.xfl2342.voiceassistant.data.db.RecurrenceRuleEntity
import io.github.xfl2342.voiceassistant.data.db.ReminderEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份文件格式的测试。
 *
 * 这份文件有两个读者：拿记事本翻一眼的人，和日后写导入时按字段名取值的代码。
 * 所以这里钉两件事：**字段名与数据库一致、条数对得上**；以及**用户输入里的引号、
 * 换行、控制字符有没有被正确转义**——转义错了，文件会变成打不开的坏文件，
 * 而用户根本看不出是哪个字惹的祸。
 */
class BackupJsonTest {

    @Test
    fun `开头写清格式版本与各有多少条`() {
        val text = BackupJson.render(snapshot(), now = 1_774_000_000_000L)

        assertTrue(text.contains(""""format": "voiceassistant-backup","""))
        assertTrue(text.contains(""""formatVersion": 1,"""))
        assertTrue(text.contains(""""exportedAt": 1774000000000,"""))
        assertTrue(text.contains(""""exportedAtText": """"))
        assertTrue(text.contains(""""events": 1,"""))
        assertTrue(text.contains(""""recurrenceRules": 1,"""))
        assertTrue(text.contains(""""reminders": 1,"""))
        assertTrue(text.contains(""""feedback": 1"""))
        assertTrue("文件末尾留一个换行，用命令行看的时候不至于粘住提示符", text.endsWith("}\n"))
    }

    @Test
    fun `各表的字段照数据库原样写出来`() {
        val text = BackupJson.render(snapshot())

        assertTrue(text.contains(""""title": "项目评审会","""))
        assertTrue(text.contains(""""allDay": false,"""))
        assertTrue(text.contains(""""startAt": 1774000000000,"""))
        assertTrue(text.contains(""""urgency": "high","""))
        assertTrue(text.contains(""""byDay": "MO,WE","""))
        assertTrue(text.contains(""""minutesBefore": 15,"""))
        assertTrue(text.contains(""""content": "日历首页想看到天气","""))
        assertTrue(text.contains(""""done": false"""))
    }

    @Test
    fun `全天行程没有的时刻写成 null`() {
        val text = BackupJson.render(
            BackupSnapshot(listOf(allDayEvent()), emptyList(), emptyList(), emptyList()),
        )

        assertTrue(text.contains(""""startAt": null,"""))
        assertTrue(text.contains(""""endAt": null,"""))
        assertTrue(text.contains(""""startEpochDay": 20687,"""))
    }

    @Test
    fun `准点提醒没有提前分钟数`() {
        val text = BackupJson.render(
            BackupSnapshot(
                events = emptyList(),
                rules = emptyList(),
                reminders = listOf(
                    ReminderEntity(
                        id = "m1",
                        eventId = "e1",
                        triggerType = ReminderEntity.TYPE_AT,
                        minutesBefore = null,
                        triggerAt = 1_774_000_000_000L,
                    ),
                ),
                feedback = emptyList(),
            ),
        )

        assertTrue(text.contains(""""triggerType": "at","""))
        assertTrue(text.contains(""""minutesBefore": null,"""))
    }

    @Test
    fun `标题里的引号换行与控制字符都会被转义`() {
        val title = "会\"议\\记录\n第二行\t带制表符" + '\u0001'
        val text = BackupJson.render(
            BackupSnapshot(listOf(timedEvent(title)), emptyList(), emptyList(), emptyList()),
        )

        // 期望的样子（原始字符串里反斜杠就是反斜杠本身）：
        assertTrue(text.contains(""""title": "会\"议\\记录\n第二行\t带制表符\u0001","""))
        assertFalse("换行不能原样进文件，否则一份备份会多出好几行", text.contains("会\"议"))
        assertFalse("控制字符不能原样进文件", text.contains('\u0001'))
    }

    @Test
    fun `没有数据时数组写成空的方括号`() {
        val text = BackupJson.render(BackupSnapshot(emptyList(), emptyList(), emptyList(), emptyList()))

        assertTrue(text.contains(""""events": [],"""))
        assertTrue(text.contains(""""recurrenceRules": [],"""))
        assertTrue(text.contains(""""reminders": [],"""))
        assertTrue(text.contains(""""feedback": []"""))
        assertTrue(text.contains(""""events": 0,"""))
    }

    @Test
    fun `文件名带导出时间且以 json 结尾`() {
        val name = BackupFileStore.fileName(now = 1_774_000_000_000L)

        // 时间部分跟着导出时刻走，时区因机器而异，所以只钉格式。
        assertTrue(
            "实际是：$name",
            Regex("""^生活助理-备份-\d{4}-\d{2}-\d{2}-\d{6}\.json$""").matches(name),
        )
    }

    private fun snapshot() = BackupSnapshot(
        events = listOf(timedEvent()),
        rules = listOf(
            RecurrenceRuleEntity(
                id = "r1",
                eventId = "e1",
                frequency = RecurrenceRuleEntity.FREQUENCY_WEEKLY,
                byDay = "MO,WE",
            ),
        ),
        reminders = listOf(
            ReminderEntity(
                id = "m1",
                eventId = "e1",
                triggerType = ReminderEntity.TYPE_BEFORE,
                minutesBefore = 15,
                triggerAt = 1_773_999_100_000L,
            ),
        ),
        feedback = listOf(
            FeedbackEntity(
                id = "f1",
                content = "日历首页想看到天气",
                createdAt = 1_773_000_000_000L,
                updatedAt = 1_773_000_000_000L,
            ),
        ),
    )

    private fun timedEvent(title: String = "项目评审会") = EventEntity(
        id = "e1",
        title = title,
        allDay = false,
        startAt = 1_774_000_000_000L,
        endAt = 1_774_003_600_000L,
        urgency = EventEntity.URGENCY_HIGH,
        rawText = "明天下午三点开项目评审会",
        createdAt = 1_773_000_000_000L,
        updatedAt = 1_773_000_000_000L,
    )

    private fun allDayEvent() = EventEntity(
        id = "e2",
        title = "休一天",
        allDay = true,
        startEpochDay = 20_687L,
        endEpochDay = 20_687L,
        createdAt = 1_773_000_000_000L,
        updatedAt = 1_773_000_000_000L,
    )
}
