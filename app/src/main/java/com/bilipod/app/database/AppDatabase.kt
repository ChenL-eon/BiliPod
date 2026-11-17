package com.bilipod.app.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.bilipod.app.database.dao.CacheDao
import com.bilipod.app.database.dao.FavoriteDao
import com.bilipod.app.database.dao.PlayHistoryDao
import com.bilipod.app.database.entity.CacheEntity
import com.bilipod.app.database.entity.FavoriteEntity
import com.bilipod.app.database.entity.PlayHistoryEntity

/**
 * 单库三表：历史、收藏、缓存。
 * 实体类全部放在 database/entity，禁止把 UI 模型塞进来。
 */
@Database(
    entities = [
        PlayHistoryEntity::class,
        FavoriteEntity::class,
        CacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): PlayHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun cacheDao(): CacheDao
}
