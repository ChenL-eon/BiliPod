package com.bilipod.app.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 播放历史 / 断点续播。
 * 一个 episodeKey 只会有一条记录，播到哪就更新到哪，避免历史列表被同一集刷屏。
 */
@Entity(
    tableName = "play_history",
    indices = [Index(value = ["playedAt"])]
)
data class PlayHistoryEntity(
    @PrimaryKey val episodeKey: String,
    val title: String,
    val cover: String?,
    val upName: String?,
    val bvid: String?,
    val cid: Long?,
    val sid: Long?,
    val durationMs: Long,
    /** 上次退出时的播放位置，用于断点续播 */
    val positionMs: Long,
    /** 最近一次播放时间，用于按时间倒序展示 */
    val playedAt: Long
)
