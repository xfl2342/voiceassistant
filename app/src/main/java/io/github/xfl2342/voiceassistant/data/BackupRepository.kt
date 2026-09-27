package io.github.xfl2342.voiceassistant.data

import android.net.Uri
import androidx.room.withTransaction
import io.github.xfl2342.voiceassistant.data.db.AppDatabase

/** 备份里各有多少条。界面先把这个数字摆出来，用户才知道自己要备份的是什么。 */
data class BackupCounts(
    val events: Int,
    val rules: Int,
    val reminders: Int,
    val feedback: Int,
) {
    /** 一条行程、一条意见都没有时，导出没有意义。 */
    val isEmpty: Boolean get() = events == 0 && feedback == 0
}

/** 导出到下载目录的结果：写出的文件名，以及这一份里各有多少条。 */
data class BackupResult(val fileName: String, val counts: BackupCounts)

/**
 * 恢复前先算好的一笔账。
 *
 * 恢复是这个应用里唯一会成批写数据的操作，所以不直接动手：先把「会新增几条、能找回几条、
 * 跳过几条」摆给用户看，用户点头之后再落库。
 */
data class ImportPlan(
    val summary: ImportSummary,

    /** 备份里写的导出时间，用来在界面说清「这份备份是哪时候的」。 */
    val exportedAtText: String?,

    /** 已经读好、校验过的那份备份，等着落库。 */
    val backup: BackupSnapshot,
)

/** 恢复实际做了多少事。 */
data class ImportSummary(
    val newEvents: Int = 0,

    /** 之前删掉的行程又被找回来了几条。 */
    val restoredEvents: Int = 0,

    val skippedEvents: Int = 0,
    val newFeedback: Int = 0,
    val skippedFeedback: Int = 0,
) {
    /** 有没有真的动到数据。一条都没动时，界面就别摆出「已恢复」的架势。 */
    val changedAny: Boolean get() = newEvents + restoredEvents + newFeedback > 0
}

/**
 * 备份的统一入口。
 *
 * 两个方向都很克制：**导出只读不写**，出错最多是少一份文件；**恢复只增不减**，
 * 手机上已有的行程一律不动，所以再怎么出岔子也不会把用户现成的数据弄丢。
 *
 * 快照的取法是「先取未删除的行程，再取与它们配套的重复规则和提醒」。按行程过滤一遍，
 * 是为了保证导出的文件自洽：孤儿规则、孤儿提醒（万一有）不进备份，导入时也不会撞上外键。
 * 改进意见没有外键，全部带走。
 */
class BackupRepository(
    private val database: AppDatabase,
    private val files: BackupFileStore,
) {

    private val eventDao = database.eventDao()
    private val ruleDao = database.recurrenceRuleDao()
    private val reminderDao = database.reminderDao()
    private val feedbackDao = database.feedbackDao()

    /** 数一遍各有多少条，只给界面显示用。 */
    suspend fun counts(): BackupCounts = snapshot().toCounts()

    /** 导出到下载目录；写不进去时返回 null，由界面提示重试。 */
    suspend fun exportToDownloads(): BackupResult? = runCatching {
        val snapshot = snapshot()
        BackupResult(files.writeToDownloads(BackupJson.render(snapshot)), snapshot.toCounts())
    }.getOrNull()

    /** 导出到用户选定的位置；失败返回 false。 */
    suspend fun exportTo(uri: Uri): Boolean = runCatching {
        files.writeTo(uri, BackupJson.render(snapshot()))
        true
    }.getOrDefault(false)

    /**
     * 读一份备份并算好恢复计划；文件读不懂时抛 [BackupFormatException]。
     *
     * 只读，不写库——用户看过这笔账、点了确认，才会走到 [import]。
     */
    suspend fun planImport(uri: Uri): ImportPlan = planImport(BackupJson.parse(files.readText(uri)))

    /** 给已经读好的备份算一笔账，供界面预览。 */
    suspend fun planImport(parsed: ParsedBackup): ImportPlan {
        val existing = currentEvents()
        val existingFeedback = feedbackDao.loadAll().mapTo(mutableSetOf()) { it.id }

        var newEvents = 0
        var restoredEvents = 0
        var skippedEvents = 0
        parsed.snapshot.events.forEach { event ->
            val current = existing[event.id]
            when {
                current == null -> newEvents++
                current.deleted -> restoredEvents++
                else -> skippedEvents++
            }
        }

        val newFeedback = parsed.snapshot.feedback.count { it.id !in existingFeedback }
        return ImportPlan(
            summary = ImportSummary(
                newEvents = newEvents,
                restoredEvents = restoredEvents,
                skippedEvents = skippedEvents,
                newFeedback = newFeedback,
                skippedFeedback = parsed.snapshot.feedback.size - newFeedback,
            ),
            exportedAtText = parsed.exportedAtText,
            backup = parsed.snapshot,
        )
    }

    /**
     * 落库。整个恢复在一个事务里，要么全成、要么什么都不留。
     *
     * 三条规则：
     * - 手机上**没有**这条 id：加进来；
     * - 手机上**有但已删除**（删除是软删除，行还在库里）：把备份里的内容写回去，等于找回；
     * - 手机上**有且在用**：跳过 —— 用户在手机上改过的东西，不该被一份旧备份盖回去。
     *
     * 提醒照备份里的样子先写进去，随后由上层重算未来时刻并注册闹钟（见 domain/BackupService）。
     * 之所以照抄一份而不是直接交给重算，是为了「重算那一步万一没跑成」时，提前量还留在库里，
     * 下次应用启动刷新提醒时能自己补上。
     */
    suspend fun import(backup: BackupSnapshot): ImportSummary = database.withTransaction {
        val existing = currentEvents()
        val existingFeedback = feedbackDao.loadAll().mapTo(mutableSetOf()) { it.id }
        val rulesByEvent = backup.rules.associateBy { it.eventId }
        val remindersByEvent = backup.reminders.groupBy { it.eventId }

        var newEvents = 0
        var restoredEvents = 0
        var skippedEvents = 0

        backup.events.forEach { event ->
            val current = existing[event.id]
            if (current != null && !current.deleted) {
                skippedEvents++
                return@forEach
            }

            // 软删除的行还在库里，把它连同规则、提醒一起写回去，就等于找回来了。
            eventDao.upsert(event.copy(deleted = false))

            ruleDao.deleteByEventId(event.id)
            rulesByEvent[event.id]?.let { ruleDao.upsert(it) }

            reminderDao.deleteByEventId(event.id)
            remindersByEvent[event.id]?.let { reminderDao.upsertAll(it) }

            if (current == null) newEvents++ else restoredEvents++
        }

        var newFeedback = 0
        var skippedFeedback = 0
        backup.feedback.forEach { item ->
            if (existingFeedback.add(item.id)) {
                feedbackDao.upsert(item)
                newFeedback++
            } else {
                skippedFeedback++
            }
        }

        ImportSummary(
            newEvents = newEvents,
            restoredEvents = restoredEvents,
            skippedEvents = skippedEvents,
            newFeedback = newFeedback,
            skippedFeedback = skippedFeedback,
        )
    }

    /** 建议的文件名，「另存为」对话框里用。 */
    fun suggestedFileName(): String = BackupFileStore.fileName()

    private suspend fun snapshot(): BackupSnapshot {
        val events = eventDao.loadAll()
        val exportedIds = events.mapTo(mutableSetOf()) { it.id }
        return BackupSnapshot(
            events = events,
            rules = ruleDao.loadAll().filter { it.eventId in exportedIds },
            reminders = reminderDao.loadAll().filter { it.eventId in exportedIds },
            feedback = feedbackDao.loadAll(),
        )
    }

    /**
     * 库里全部的行程，**含已删除的**。
     *
     * 恢复必须看得见软删除的那些行：判断「这是新的一条」还是「这条能找回」，全指望它。
     */
    private suspend fun currentEvents() =
        eventDao.loadAllIncludingDeleted().associateBy { it.id }

    private fun BackupSnapshot.toCounts() = BackupCounts(
        events = events.size,
        rules = rules.size,
        reminders = reminders.size,
        feedback = feedback.size,
    )
}
