package com.bilipod.app.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bilipod.app.database.entity.CacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CacheEntity)

    @Query("SELECT * FROM audio_cache WHERE episodeKey = :key LIMIT 1")
    suspend fun find(key: String): CacheEntity?

    @Query("SELECT * FROM audio_cache ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CacheEntity>>

    /** 一次性读取，供 key 迁移使用 */
    @Query("SELECT * FROM audio_cache")
    suspend fun listAll(): List<CacheEntity>

    @Query("SELECT * FROM audio_cache WHERE status = 'SUCCESS' ORDER BY lastAccessAt ASC")
    suspend fun listForEviction(): List<CacheEntity>

    @Query("UPDATE audio_cache SET lastAccessAt = :time WHERE episodeKey = :key")
    suspend fun touch(key: String, time: Long)

    @Query("DELETE FROM audio_cache WHERE episodeKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM audio_cache")
    suspend fun clear()
}
