package com.bilipod.app.repository.model

import com.bilipod.app.database.entity.CacheEntity
import com.bilipod.app.database.entity.FavoriteEntity
import com.bilipod.app.database.entity.PlayHistoryEntity

/**
 * Room 实体 -> 领域模型。
 * 放 repository 层，数据库包不反向依赖 UI。
 */
fun PlayHistoryEntity.toEpisode(): Episode = Episode(
    key = episodeKey,
    title = title,
    bvid = bvid,
    cid = cid,
    sid = sid,
    cover = cover,
    upName = upName,
    durationSec = durationMs / 1000L
)

fun FavoriteEntity.toEpisode(): Episode = Episode(
    key = episodeKey,
    title = title,
    bvid = bvid,
    cid = cid,
    sid = sid,
    cover = cover,
    upName = upName,
    durationSec = durationSec
)

fun CacheEntity.toEpisode(): Episode = Episode(
    key = episodeKey,
    title = title,
    bvid = bvid,
    cid = cid,
    sid = sid,
    cover = cover,
    upName = upName,
    // 缓存实体不记录时长，播放时以真实媒体时长为准
    durationSec = 0L
)
