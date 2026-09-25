package io.github.xfl2342.voiceassistant.domain

import io.github.xfl2342.voiceassistant.data.EventDetail
import io.github.xfl2342.voiceassistant.data.EventRepository
import io.github.xfl2342.voiceassistant.reminder.ReminderScheduler

/**
 * 行程的写操作。
 *
 * 之所以要有这么一层，是因为「改数据库」和「改系统闹钟」必须一起做：
 * 只改数据不取消旧闹钟，用户会收到一条指向已删除行程的提醒；
 * 只取消闹钟不更新数据，提醒就不会再响。放在这里统一处理。
 */
class EventService(
    private val repository: EventRepository,
    private val scheduler: ReminderScheduler,
) {

    suspend fun load(eventId: String): EventDetail? = repository.loadDetail(eventId)

    /**
     * 保存（新建或修改）行程。
     *
     * 注意顺序：先取消这条行程原有的闹钟，再写库，最后注册新的。
     * 编辑时提醒记录会被重新生成、编号改变，不先取消就会留下再也不会被清理的幽灵闹钟。
     */
    suspend fun save(bundle: EventSaveBundle) {
        repository.remindersOf(bundle.event.id).forEach { scheduler.cancel(it.id) }
        repository.save(bundle.event, bundle.rule, bundle.reminders)
        bundle.reminders.forEach { scheduler.schedule(it, bundle.event.title) }
    }

    /** 删除行程，并清掉它所有的提醒闹钟。 */
    suspend fun delete(eventId: String) {
        repository.remindersOf(eventId).forEach { scheduler.cancel(it.id) }
        repository.delete(eventId)
    }
}
