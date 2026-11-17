package com.bilipod.app.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 音频缓存记录。
 *
 * 注意：这里只存本地文件信息，绝对不存 B 站直链（直链带时效 token，存下来必然失效）。
 * status 取值见 CacheState 枚举的 name。
 */
@Entity(tableName = "audio_cache")
data class CacheEntity(
    @PrimaryKey val episodeKey: String,
    val title: String,
    val cover: String?,
    val upName: String?,
    val bvid: String?,
    val cid: Long?,
    val sid: Long?,
    /** 文件名，相对于 filesDir/audio_cache */
    val fileName: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    /** WAITING / DOWNLOADING / SUCCESS / FAILED / CANCELED */
    val status: String,
    val createdAt: Long,
    /** 最近访问时间，容量淘汰按它升序删 */
    val lastAccessAt: Long
)
