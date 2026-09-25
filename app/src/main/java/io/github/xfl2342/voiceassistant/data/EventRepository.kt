package io.github.xfl2342.voiceassistant.data

import androidx.room.withTransaction
import io.github.xfl2342.voiceassistant.data.db.AppDatabase
import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.data.db.RecurrenceRuleEntity
import io.github.xfl2342.voiceassistant.data.db.ReminderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** 日历界面渲染一次所需要的全部数据。 */
data class CalendarData(
    val events: List<EventEntity>,
    val rules: List<RecurrenceRuleEntity>,
)

/**
 * 行程数据的统一入口。
 *
 * 界面只跟这里打交道，不直接碰数据库，日后要加缓存或换存储方式都不用动界面。
 */
class EventRepository(private val database: AppDatabase) {

    private val eventDao = database.eventDao()
    private val ruleDao = database.recurrenceRuleDao()
    private val reminderDao = database.reminderDao()

    fun observeCalendar(): Flow<CalendarData> =
        combine(eventDao.observeAll(), ruleDao.observeAll()) { events, rules ->
            CalendarData(events, rules)
        }

    /** 保存行程、重复规则与提醒。三者要么一起成功，要么一起失败。 */
    suspend fun save(
        event: EventEntity,
        rule: RecurrenceRuleEntity? = null,
        reminders: List<ReminderEntity> = emptyList(),
    ) {
        database.withTransaction {
            eventDao.upsert(event)

            ruleDao.deleteByEventId(event.id)
            rule?.let { ruleDao.upsert(it) }

            reminderDao.deleteByEventId(event.id)
            if (reminders.isNotEmpty()) reminderDao.upsertAll(reminders)
        }
    }

    suspend fun findById(id: String): EventEntity? = eventDao.findById(id)

    suspend fun loadEvents(): List<EventEntity> = eventDao.loadAll()

    suspend fun loadRules(): List<RecurrenceRuleEntity> = ruleDao.loadAll()

    suspend fun remindersOf(eventId: String): List<ReminderEntity> =
        reminderDao.findByEventId(eventId)

    suspend fun readyRules(): Map<String, RecurrenceRuleEntity> =
        ruleDao.loadAll().associateBy { it.eventId }

    suspend fun delete(eventId: String, now: Long = System.currentTimeMillis()) {
        database.withTransaction {
            eventDao.softDelete(eventId, now)
            ruleDao.deleteByEventId(eventId)
            reminderDao.deleteByEventId(eventId)
        }
    }
}
