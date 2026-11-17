package com.bilipod.app.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 收藏的单集，和 B 站账号收藏夹无关，纯本地 */
@Entity(tableName = "favorite_episode")
data class FavoriteEntity(
    @PrimaryKey val episodeKey: String,
    val title: String,
    val cover: String?,
    val upName: String?,
    val bvid: String?,
    val cid: Long?,
    val sid: Long?,
    val durationSec: Long,
    val addedAt: Long
)
