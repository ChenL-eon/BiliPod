package com.bilipod.app.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bilipod.app.database.entity.PlayHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlayHistoryEntity)

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history WHERE episodeKey = :key LIMIT 1")
    suspend fun find(key: String): PlayHistoryEntity?

    /** 一次性读取，供 key 迁移使用 */
    @Query("SELECT * FROM play_history")
    suspend fun listAll(): List<PlayHistoryEntity>

    @Query("DELETE FROM play_history WHERE episodeKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM play_history")
    suspend fun clear()
}
