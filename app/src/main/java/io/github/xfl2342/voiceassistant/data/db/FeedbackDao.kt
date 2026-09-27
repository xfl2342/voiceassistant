package io.github.xfl2342.voiceassistant.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedbackDao {

    /** 观察全部意见，最近记的排在最前面。 */
    @Query("SELECT * FROM feedback ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FeedbackEntity>>

    @Query("SELECT * FROM feedback ORDER BY createdAt DESC")
    suspend fun loadAll(): List<FeedbackEntity>

    @Upsert
    suspend fun upsert(item: FeedbackEntity)

    @Query("UPDATE feedback SET done = :done, updatedAt = :now WHERE id = :id")
    suspend fun setDone(id: String, done: Boolean, now: Long)

    @Query("DELETE FROM feedback WHERE id = :id")
    suspend fun delete(id: String)
}
