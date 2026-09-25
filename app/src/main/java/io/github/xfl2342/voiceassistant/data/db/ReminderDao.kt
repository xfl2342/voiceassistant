package io.github.xfl2342.voiceassistant.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders WHERE eventId = :eventId ORDER BY triggerAt")
    suspend fun findByEventId(eventId: String): List<ReminderEntity>

    /** 取出待触发且尚未处理的提醒，用于开机或应用启动后重新注册闹钟。 */
    @Query("SELECT * FROM reminders WHERE triggerAt > :from ORDER BY triggerAt")
    suspend fun findUpcoming(from: Long): List<ReminderEntity>

    /** 待触发的提醒连同行程标题一起取出，注册闹钟时要用标题组装通知。 */
    @Query(
        """
        SELECT r.id AS reminderId, r.eventId AS eventId, r.triggerAt AS triggerAt, e.title AS title
        FROM reminders r
        INNER JOIN events e ON e.id = r.eventId
        WHERE r.triggerAt > :from AND e.deleted = 0
        ORDER BY r.triggerAt
        """
    )
    suspend fun findUpcomingWithTitle(from: Long): List<UpcomingReminder>

    @Upsert
    suspend fun upsertAll(reminders: List<ReminderEntity>)

    @Query("DELETE FROM reminders WHERE eventId = :eventId")
    suspend fun deleteByEventId(eventId: String)
}

/** 提醒与行程标题的组合，供开机后重新注册闹钟使用。 */
data class UpcomingReminder(
    val reminderId: String,
    val eventId: String,
    val triggerAt: Long,
    val title: String,
)
