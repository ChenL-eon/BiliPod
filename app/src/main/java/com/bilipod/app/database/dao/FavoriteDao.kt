package com.bilipod.app.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bilipod.app.database.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteEntity)

    @Query("DELETE FROM favorite_episode WHERE episodeKey = :key")
    suspend fun delete(key: String)

    @Query("SELECT * FROM favorite_episode ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    /**
     * 精确 key 命中就返回它。
     * 取消收藏时需要知道"实际存的是哪个 key"，否则删不到那一条。
     */
    @Query("SELECT episodeKey FROM favorite_episode WHERE episodeKey = :key LIMIT 1")
    suspend fun findKey(key: String): String?

    /**
     * 稿件级回退：搜索/收藏夹卡片拿到的 key 是 v_{bvid}，播放页红心写的是 v_{bvid}_{cid}，
     * 两边 key 精度不同。按 bvid 查一次，才能让"从收藏夹点进来"也显示实心。
     */
    @Query("SELECT episodeKey FROM favorite_episode WHERE bvid = :bvid ORDER BY addedAt DESC LIMIT 1")
    suspend fun findKeyByBvid(bvid: String): String?
}
