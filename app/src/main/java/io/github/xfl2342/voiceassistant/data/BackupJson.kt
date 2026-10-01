package io.github.xfl2342.voiceassistant.data

import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.data.db.FeedbackEntity
import io.github.xfl2342.voiceassistant.data.db.RecurrenceRuleEntity
import io.github.xfl2342.voiceassistant.data.db.ReminderEntity
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 一份备份里装的全部数据。字段与数据库表一一对应，日后写导入时照着它读回来。 */
data class BackupSnapshot(
    /** 只含未删除的行程；软删除的那些是回收站里的东西，不进备份。 */
    val events: List<EventEntity>,

    /** 只含上面这些行程的重复规则。 */
    val rules: List<RecurrenceRuleEntity>,

    /** 只含上面这些行程的提醒。 */
    val reminders: List<ReminderEntity>,

    val feedback: List<FeedbackEntity>,
)

/** 从文件里读回来的一份备份，连同文件头上那句「什么时候导出的」。 */
data class ParsedBackup(
    val snapshot: BackupSnapshot,

    /** 备份里写的导出时间，形如 `2026-09-26 15:30:00`；老文件可能没有这一项。 */
    val exportedAtText: String?,
)

/**
 * 文件读不动（不是备份、版本太新、缺字段）时抛这个。
 *
 * [message] 是给用户看的一句话，界面直接摆出来就行，不用再翻译一遍。
 */
class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 备份文件的格式：把一份 [BackupSnapshot] 渲染成 JSON 文本。
 *
 * 格式上只做两件事：**字段名和数据库保持一致**（这样以后写导入几乎不用动脑子），
 * 以及**带上格式版本号**（以后改了字段，读的人一眼知道该按哪一版解析）。
 *
 * 生成 JSON 用的是这个文件里手写的小工具，而不是系统自带的 `org.json`：
 * 后者在电脑上的单元测试里是个空壳（一调用就抛异常），而这份文件最容易出错的地方
 * 恰恰是「用户输入的引号、换行、控制字符有没有正确转义」——这一处必须能用单元测试钉住。
 *
 * 读的方向反过来：解析交给系统的 `org.json`。写的时候怕的是自己手抖，读的时候怕的是
 * 文件被改坏，这种事让一个用了十几年的解析器去做，比自己再手写一个靠谱得多。
 */
object BackupJson {

    const val APP_NAME = "生活助理"

    /** 认文件的标记：日后用户拿错文件（比如把别人的备份塞进来）时能认出来。 */
    const val FORMAT = "voiceassistant-backup"

    /**
     * 格式版本。字段增删时加一，并在读的那一头按版本分支。
     *
     * v2：行程多了 `done`（待办的完成标记）。
     * v3：重复规则多了 `exceptionDates`（跳过的那几次）。
     * 老文件一律照读，缺哪一项就按它的默认值处理。
     */
    const val FORMAT_VERSION = 3

    private const val NOTE =
        "生活助理的数据备份：行程、重复规则、提醒与改进意见。不含 API Key 与各项设置，只含未删除的行程。"

    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun render(snapshot: BackupSnapshot, now: Long = System.currentTimeMillis()): String {
        val exportedAtText = STAMP.format(
            Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()),
        )
        val root = obj(
            "app" to str(APP_NAME),
            "format" to str(FORMAT),
            "formatVersion" to num(FORMAT_VERSION.toLong()),
            "exportedAt" to num(now),
            "exportedAtText" to str(exportedAtText),
            "note" to str(NOTE),
            "counts" to obj(
                "events" to num(snapshot.events.size.toLong()),
                "recurrenceRules" to num(snapshot.rules.size.toLong()),
                "reminders" to num(snapshot.reminders.size.toLong()),
                "feedback" to num(snapshot.feedback.size.toLong()),
            ),
            "events" to arr(snapshot.events.map(::event)),
            "recurrenceRules" to arr(snapshot.rules.map(::rule)),
            "reminders" to arr(snapshot.reminders.map(::reminder)),
            "feedback" to arr(snapshot.feedback.map(::feedbackItem)),
        )
        return StringBuilder().also { root.write(it, 0) }.append('\n').toString()
    }

    /**
     * 把一份备份文件读回 [ParsedBackup]，读不动就抛 [BackupFormatException]。
     *
     * 认得很严的地方只有两处：格式标记与格式版本——认错了会把别的文件当成备份写进数据库。
     * 其余字段一律「读得动就读，读不动按默认值」：备份是用来救数据的，为了一个字段不标准
     * 就把整份文件拒之门外，反倒违背了它存在的意义。
     */
    fun parse(text: String): ParsedBackup {
        val root = try {
            JSONObject(text)
        } catch (error: JSONException) {
            throw BackupFormatException("这个文件读不出 JSON，可能没拷完整。", error)
        }

        if (root.optString("format", "") != FORMAT) {
            throw BackupFormatException("这不是生活助理导出的备份文件。")
        }
        val version = root.optInt("formatVersion", 0)
        if (version > FORMAT_VERSION) {
            throw BackupFormatException(
                "这份备份来自更新版本的生活助理（格式 v$version），当前版本读不了，" +
                    "先把应用更新到最新再试。",
            )
        }
        if (version < 1) {
            throw BackupFormatException("备份里没写格式版本，认不准，先别用它。")
        }

        return ParsedBackup(
            snapshot = BackupSnapshot(
                events = root.items("events").map(::parseEvent),
                rules = root.items("recurrenceRules").mapNotNull(::parseRule),
                reminders = root.items("reminders").map(::parseReminder),
                feedback = root.items("feedback").map(::parseFeedback),
            ),
            exportedAtText = root.stringOrNull("exportedAtText"),
        )
    }

    private fun parseEvent(item: JSONObject) = EventEntity(
        id = item.requiredText("id", "有一条行程没写 id"),
        title = item.requiredText("title", "有一条行程没写标题"),
        allDay = item.optBoolean("allDay", false),
        startAt = item.longOrNull("startAt"),
        endAt = item.longOrNull("endAt"),
        startEpochDay = item.longOrNull("startEpochDay"),
        endEpochDay = item.longOrNull("endEpochDay"),
        timeZone = item.stringOrNull("timeZone") ?: EventEntity.DEFAULT_TIME_ZONE,
        location = item.stringOrNull("location"),
        notes = item.stringOrNull("notes"),
        urgency = EventEntity.normalizeUrgency(item.stringOrNull("urgency")),
        // v1 的备份里没有这一项，读成「没完成」。
        done = item.optBoolean("done", false),
        rawText = item.stringOrNull("rawText"),
        createdAt = item.longOrNull("createdAt") ?: 0L,
        updatedAt = item.longOrNull("updatedAt") ?: 0L,
        deleted = false,
    )

    /**
     * 认不出来的重复频率直接丢掉这条规则：展开不了规则，留着只会变成一条
     * 谁也看不懂的行程。丢掉的只是一条规则，行程本身照常恢复。
     */
    private fun parseRule(item: JSONObject): RecurrenceRuleEntity? {
        val frequency = item.stringOrNull("frequency")
        if (frequency != RecurrenceRuleEntity.FREQUENCY_DAILY &&
            frequency != RecurrenceRuleEntity.FREQUENCY_WEEKLY
        ) {
            return null
        }
        return RecurrenceRuleEntity(
            id = item.requiredText("id", "有一条重复规则没写 id"),
            eventId = item.requiredText("eventId", "有一条重复规则没写所属行程"),
            frequency = frequency,
            interval = item.optInt("interval", 1).coerceAtLeast(1),
            byDay = item.stringOrNull("byDay"),
            endType = if (item.stringOrNull("endType") == RecurrenceRuleEntity.END_TYPE_ON_DATE) {
                RecurrenceRuleEntity.END_TYPE_ON_DATE
            } else {
                RecurrenceRuleEntity.END_TYPE_NEVER
            },
            endEpochDay = item.longOrNull("endEpochDay"),
            exceptionDates = item.stringOrNull("exceptionDates"),
        )
    }

    private fun parseReminder(item: JSONObject) = ReminderEntity(
        id = item.requiredText("id", "有一条提醒没写 id"),
        eventId = item.requiredText("eventId", "有一条提醒没写所属行程"),
        triggerType = if (item.stringOrNull("triggerType") == ReminderEntity.TYPE_AT) {
            ReminderEntity.TYPE_AT
        } else {
            ReminderEntity.TYPE_BEFORE
        },
        minutesBefore = if (item.isNull("minutesBefore")) null else item.optInt("minutesBefore"),
        triggerAt = item.longOrNull("triggerAt") ?: 0L,
    )

    private fun parseFeedback(item: JSONObject) = FeedbackEntity(
        id = item.requiredText("id", "有一条改进意见没写 id"),
        content = item.requiredText("content", "有一条改进意见没写正文"),
        createdAt = item.longOrNull("createdAt") ?: 0L,
        updatedAt = item.longOrNull("updatedAt") ?: 0L,
        done = item.optBoolean("done", false),
    )

    /** 取数组里的对象，缺项、脏数据（比如数组里塞了个数字）都跳过，不因此否定整份文件。 */
    private fun JSONObject.items(name: String): List<JSONObject> {
        val array = optJSONArray(name) ?: return emptyList()
        return (0 until array.length()).mapNotNull { index -> array.optJSONObject(index) }
    }

    /**
     * 取字符串字段；写的是 JSON 里没有这一项，或者值是 null。
     *
     * 不能直接用 `optString`：它对 JSON 的 null 会返回字符串 "null"，
     * 于是「没有地点」会变成一条地点叫「null」的行程。
     */
    private fun JSONObject.stringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name)

    private fun JSONObject.longOrNull(name: String): Long? =
        if (isNull(name)) null else optLong(name)

    private fun JSONObject.requiredText(name: String, problem: String): String =
        stringOrNull(name)?.takeIf { it.isNotBlank() }
            ?: throw BackupFormatException("备份里的数据不完整：$problem。")

    private fun event(event: EventEntity): JsonValue = obj(
        "id" to str(event.id),
        "title" to str(event.title),
        "allDay" to bool(event.allDay),
        "startAt" to num(event.startAt),
        "endAt" to num(event.endAt),
        "startEpochDay" to num(event.startEpochDay),
        "endEpochDay" to num(event.endEpochDay),
        "timeZone" to str(event.timeZone),
        "location" to str(event.location),
        "notes" to str(event.notes),
        "urgency" to str(event.urgency),
        "done" to bool(event.done),
        "rawText" to str(event.rawText),
        "createdAt" to num(event.createdAt),
        "updatedAt" to num(event.updatedAt),
    )

    private fun rule(rule: RecurrenceRuleEntity): JsonValue = obj(
        "id" to str(rule.id),
        "eventId" to str(rule.eventId),
        "frequency" to str(rule.frequency),
        "interval" to num(rule.interval.toLong()),
        "byDay" to str(rule.byDay),
        "endType" to str(rule.endType),
        "endEpochDay" to num(rule.endEpochDay),
        "exceptionDates" to str(rule.exceptionDates),
    )

    private fun reminder(reminder: ReminderEntity): JsonValue = obj(
        "id" to str(reminder.id),
        "eventId" to str(reminder.eventId),
        "triggerType" to str(reminder.triggerType),
        "minutesBefore" to num(reminder.minutesBefore?.toLong()),
        "triggerAt" to num(reminder.triggerAt),
    )

    private fun feedbackItem(item: FeedbackEntity): JsonValue = obj(
        "id" to str(item.id),
        "content" to str(item.content),
        "createdAt" to num(item.createdAt),
        "updatedAt" to num(item.updatedAt),
        "done" to bool(item.done),
    )

    private fun obj(vararg members: Pair<String, JsonValue>) = JsonObject(members.toList())

    private fun arr(items: List<JsonValue>) = JsonArray(items)

    private fun str(value: String?) = if (value == null) JsonNull else JsonString(value)

    private fun num(value: Long?) = if (value == null) JsonNull else JsonNumber(value)

    private fun bool(value: Boolean) = JsonBoolean(value)
}

/**
 * 够用就好的 JSON 生成器。
 *
 * 只支持这一个文件需要的类型，不追求通用：字符串、整数、布尔、null、对象、数组。
 * 输出带缩进，是给人看的——备份文件偶尔会用记事本打开，能一眼看懂比省几个字节值。
 */
private sealed interface JsonValue {
    fun write(sb: StringBuilder, depth: Int)
}

private class JsonString(private val value: String) : JsonValue {
    override fun write(sb: StringBuilder, depth: Int) {
        sb.append('"')
        appendEscaped(sb, value)
        sb.append('"')
    }
}

private class JsonNumber(private val value: Long) : JsonValue {
    override fun write(sb: StringBuilder, depth: Int) {
        sb.append(value)
    }
}

private class JsonBoolean(private val value: Boolean) : JsonValue {
    override fun write(sb: StringBuilder, depth: Int) {
        sb.append(if (value) "true" else "false")
    }
}

private object JsonNull : JsonValue {
    override fun write(sb: StringBuilder, depth: Int) {
        sb.append("null")
    }
}

private class JsonObject(private val members: List<Pair<String, JsonValue>>) : JsonValue {
    override fun write(sb: StringBuilder, depth: Int) {
        if (members.isEmpty()) {
            sb.append("{}")
            return
        }
        sb.append("{\n")
        members.forEachIndexed { index, (name, value) ->
            sb.append(INDENT.repeat(depth + 1))
            sb.append('"')
            appendEscaped(sb, name)
            sb.append("\": ")
            value.write(sb, depth + 1)
            if (index < members.lastIndex) sb.append(',')
            sb.append('\n')
        }
        sb.append(INDENT.repeat(depth)).append('}')
    }
}

private class JsonArray(private val items: List<JsonValue>) : JsonValue {
    override fun write(sb: StringBuilder, depth: Int) {
        if (items.isEmpty()) {
            sb.append("[]")
            return
        }
        sb.append("[\n")
        items.forEachIndexed { index, item ->
            sb.append(INDENT.repeat(depth + 1))
            item.write(sb, depth + 1)
            if (index < items.lastIndex) sb.append(',')
            sb.append('\n')
        }
        sb.append(INDENT.repeat(depth)).append(']')
    }
}

private const val INDENT = "  "

/** 转义：JSON 里必须写成反斜杠形式的那些字符，一个都不能漏。 */
private fun appendEscaped(sb: StringBuilder, text: String) {
    for (ch in text) {
        when (ch) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            // 其余控制字符没有简写，只能写成 \uXXXX。不转义的话，
            // 有些解析器会直接判定文件损坏，而用户根本看不出来是哪个字符惹的祸。
            else -> if (ch < ' ') sb.append(unicodeEscape(ch)) else sb.append(ch)
        }
    }
}

private fun unicodeEscape(ch: Char): String =
    "\\u" + ch.code.toString(16).padStart(4, '0')
