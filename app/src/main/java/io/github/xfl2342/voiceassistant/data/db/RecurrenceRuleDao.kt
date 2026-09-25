package io.github.xfl2342.voiceassistant.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurrenceRuleDao {

    @Query("SELECT * FROM recurrence_rules")
    fun observeAll(): Flow<List<RecurrenceRuleEntity>>

    @Query("SELECT * FROM recurrence_rules")
    suspend fun loadAll(): List<RecurrenceRuleEntity>

    @Query("SELECT * FROM recurrence_rules WHERE eventId = :eventId LIMIT 1")
    suspend fun findByEventId(eventId: String): RecurrenceRuleEntity?

    @Upsert
    suspend fun upsert(rule: RecurrenceRuleEntity)

    @Query("DELETE FROM recurrence_rules WHERE eventId = :eventId")
    suspend fun deleteByEventId(eventId: String)
}
