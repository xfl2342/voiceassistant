package io.github.xfl2342.voiceassistant.domain

import io.github.xfl2342.voiceassistant.data.EventDetail
import io.github.xfl2342.voiceassistant.data.EventRepository
import io.github.xfl2342.voiceassistant.data.SettingsStore
import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.data.db.RecurrenceRuleEntity
import io.github.xfl2342.voiceassistant.reminder.ReminderScheduler
import java.time.LocalDate
import java.time.ZoneId

/**
 * 行程的写操作。
 *
 * 之所以要有这么一层，是因为「改数据库」和「改系统闹钟」必须一起做：
 * 只改数据不取消旧闹钟，用户会收到一条指向已删除行程的提醒；
 * 只取消闹钟不更新数据，提醒就不会再响。放在这里统一处理。
 */
class EventService(
    private val repository: EventRepository,
    private val reminderSync: ReminderSync,
    private val scheduler: ReminderScheduler,
    private val settingsStore: SettingsStore,
    /**
     * 行程变化之后的额外动作（目前是重画桌面小组件）。
     *
     * 默认什么都不做：这一层不该知道桌面上有没有小组件，单独测业务时也不必准备它。
     */
    private val onScheduleChanged: () -> Unit = {},
) {

    suspend fun load(eventId: String): EventDetail? = repository.loadDetail(eventId)

    /**
     * 保存（新建或修改）行程。
     *
     * 注意顺序：先取消这条行程原有的闹钟，再写库，最后注册新的。
     * 编辑时提醒记录会被重新生成、编号改变，不先取消就会留下再也不会被清理的幽灵闹钟。
     */
    suspend fun save(bundle: EventSaveBundle) {
        // 行程与规则先入库，提醒交给 ReminderSync 统一重算：
        // 重复行程需要按规则预注册未来多次，而不是只注册第一条。
        repository.save(bundle.event, bundle.rule, emptyList())
        reminderSync.refresh(
            event = bundle.event,
            rule = bundle.rule,
            minutesList = bundle.reminders.mapNotNull { it.minutesBefore }.distinct(),
        )
        onScheduleChanged()
    }

    /** 删除行程，并清掉它所有的提醒闹钟。 */
    suspend fun delete(eventId: String) {
        repository.remindersOf(eventId).forEach { scheduler.cancel(it.id) }
        repository.delete(eventId)
        onScheduleChanged()
    }

    /**
     * 给一条待办打勾 / 取消打勾。
     *
     * 待办既没有提醒也没有重复规则，所以只改这一个标记；行程数据变了，
     * 照例让桌面小组件重画一次。
     */
    suspend fun setTodoDone(eventId: String, done: Boolean) {
        repository.setDone(eventId, done)
        onScheduleChanged()
    }

    /**
     * 跳过重复行程的某一次（iCalendar 里的 EXDATE）。
     *
     * 只往规则上记一天，不删行程、也不动规则本身：那一天不再产生发生，
     * 它的提醒随重算一起撤掉，其余各次照旧。
     */
    suspend fun skipOccurrence(eventId: String, date: LocalDate) {
        val detail = repository.loadDetail(eventId) ?: return
        val rule = detail.rule ?: return
        val updated = rule.copy(exceptionDates = ExceptionDates.add(rule.exceptionDates, date))
        if (updated.exceptionDates == rule.exceptionDates) return

        // 与 save() 同一套路：先落库，再让提醒按新规则重算一遍。
        val minutes = detail.reminders.mapNotNull { it.minutesBefore }.distinct()
        repository.save(detail.event, updated, emptyList())
        reminderSync.refresh(detail.event, updated, minutes)
        onScheduleChanged()
    }

    /** 重算全部行程的提醒（应用启动与开机后调用）。 */
    suspend fun refreshAllReminders() {
        reminderSync.refreshAll()
    }

    /**
     * 检查这条行程是否与日历里已有的行程撞时间。
     *
     * 只做检查、不落库：界面拿到结果后先问用户一句，
     * 用户选择「仍然保存」再照常调用 [save]。
     */
    suspend fun findConflicts(
        event: EventEntity,
        rule: RecurrenceRuleEntity?,
    ): ConflictReport = ConflictDetector.detect(
        candidate = event,
        candidateRule = rule,
        existing = repository.loadEvents(),
        existingRules = repository.loadRules(),
        zone = ZoneId.of(EventEntity.DEFAULT_TIME_ZONE),
        weekStartDay = settingsStore.weekStartDay,
    )
}
