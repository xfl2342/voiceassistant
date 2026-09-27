package io.github.xfl2342.voiceassistant.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ReminderDao {

    /** 全部提醒。备份要把它们整份带走，所以这里不能只按行程查。 */
    @Query("SELECT * FROM reminders")
    suspend fun loadAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE eventId = :eventId ORDER BY triggerAt")
    suspend fun findByEventId(eventId: String): List<ReminderEntity>

    @Upsert
    suspend fun upsertAll(reminders: List<ReminderEntity>)

    @Query("DELETE FROM reminders WHERE eventId = :eventId")
    suspend fun deleteByEventId(eventId: String)
}
