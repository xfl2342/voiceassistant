package io.github.xfl2342.voiceassistant.domain

import android.util.Log
import io.github.xfl2342.voiceassistant.data.EventRepository
import io.github.xfl2342.voiceassistant.data.SettingsStore
import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.data.db.RecurrenceRuleEntity
import io.github.xfl2342.voiceassistant.data.db.ReminderEntity
import io.github.xfl2342.voiceassistant.reminder.ReminderScheduler
import java.time.Instant
import java.time.ZoneId

/**
 * 让「数据库里的提醒」和「系统里已注册的闹钟」保持一致。
 *
 * 重复行程不能只注册第一次：说「每周一三五早上八点跑步」，如果只注册一条提醒，
 * 之后就不会再响了。这里的做法是**预注册未来一段时间的每一次发生**，
 * 并在应用启动、开机重启时重新算一遍（滚动续期）。
 */
class ReminderSync(
    private val repository: EventRepository,
    private val scheduler: ReminderScheduler,
    private val settingsStore: SettingsStore,
) {

    private val zone: ZoneId = ZoneId.of(EventEntity.DEFAULT_TIME_ZONE)

    /** 重算所有行程的提醒并重新注册闹钟。应用启动与开机后都调用它。 */
    suspend fun refreshAll(now: Instant = Instant.now()) {
        val rules = repository.loadRules().associateBy { it.eventId }
        val events = repository.loadEvents()
        events.forEach { event ->
            val minutes = repository.remindersOf(event.id).mapNotNull { it.minutesBefore }.distinct()
            apply(event, rules[event.id], minutes, now)
        }
        Log.i(TAG, "已刷新 ${events.size} 条行程的提醒")
    }

    /**
     * 针对单条行程重算提醒。
     *
     * [minutesList] 为空表示用户选择了「不提醒」，此时只清理、不新建。
     */
    suspend fun refresh(
        event: EventEntity,
        rule: RecurrenceRuleEntity?,
        minutesList: List<Int>,
        now: Instant = Instant.now(),
    ) {
        apply(event, rule, minutesList, now)
    }

    private suspend fun apply(
        event: EventEntity,
        rule: RecurrenceRuleEntity?,
        minutesList: List<Int>,
        now: Instant,
    ) {
        // 先撤掉旧的，再按最新规则重建。
        repository.remindersOf(event.id).forEach { scheduler.cancel(it.id) }

        val planned = plan(event, rule, minutesList, now)
        repository.replaceReminders(event.id, planned)
        planned.forEach { scheduler.schedule(it, event.title) }
        Log.i(TAG, "「${event.title}」注册 ${planned.size} 条提醒")
    }

    private fun plan(
        event: EventEntity,
        rule: RecurrenceRuleEntity?,
        minutesList: List<Int>,
        now: Instant,
    ): List<ReminderEntity> {
        if (minutesList.isEmpty()) return emptyList()
        // 待办没有时间，排不出提醒；数据要是从别处带进来一条，也在这里挡掉。
        if (event.isTodo) return emptyList()

        val anchors: List<Instant> = if (rule == null) {
            listOf(ReminderPlanner.anchorOf(event, zone))
        } else {
            val today = now.atZone(zone).toLocalDate()
            RecurrenceExpander
                .expand(
                    events = listOf(event),
                    rules = listOf(rule),
                    from = today,
                    to = today.plusDays(HORIZON_DAYS),
                    zone = zone,
                    weekStartDay = settingsStore.weekStartDay,
                )
                .map { occurrence ->
                    occurrence.startAt
                        ?: ReminderPlanner.anchorOfDay(occurrence.date, zone)
                }
        }

        return anchors
            .flatMap { anchor ->
                minutesList.map { minutes ->
                    ReminderPlanner.createAt(event.id, anchor, minutes)
                }
            }
            // 已经过去的时刻不用注册，否则会立刻弹通知。
            .filter { it.triggerAt > now.toEpochMilli() }
            .sortedBy { it.triggerAt }
            .take(MAX_REMINDERS)
    }

    private companion object {
        const val TAG = "ReminderSync"

        /** 重复行程预注册多久之内的提醒；每次启动都会顺延。 */
        const val HORIZON_DAYS = 30L

        /** 单条行程的提醒上限，避免一条「每天」的行程塞进几百个闹钟。 */
        const val MAX_REMINDERS = 60
    }
}
