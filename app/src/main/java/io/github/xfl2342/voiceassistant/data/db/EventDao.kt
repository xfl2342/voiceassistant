package io.github.xfl2342.voiceassistant.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    /**
     * 观察全部未删除的行程。
     *
     * 个人自用场景下行数很少（几年也就几千条），一次读进内存再展开重复规则，
     * 比按时间窗口写复杂 SQL 更简单，也不会有性能问题。
     */
    @Query("SELECT * FROM events WHERE deleted = 0")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE deleted = 0")
    suspend fun loadAll(): List<EventEntity>

    /** 全部行程，含已删除的。恢复时靠它分辨「新的一条」与「删过的一条」。 */
    @Query("SELECT * FROM events")
    suspend fun loadAllIncludingDeleted(): List<EventEntity>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun findById(id: String): EventEntity?

    @Upsert
    suspend fun upsert(event: EventEntity)

    @Query("UPDATE events SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
