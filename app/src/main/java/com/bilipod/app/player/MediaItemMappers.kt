package com.bilipod.app.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.bilipod.app.common.Constants
import com.bilipod.app.repository.model.Episode

/**
 * Episode -> MediaItem。
 *
 * 关键设计：MediaItem 里放的是伪协议 bili://play?...，不是真实音频地址。
 * 真实地址由 PlayerService 里的 ResolvingDataSource 在真正开始读取数据前解析，
 * 这样过期 token 永远不会被缓存下来，也天然支持"链接过期自动重解析"。
 */
fun Episode.toMediaItem(): MediaItem {
    val uri = Uri.Builder()
        .scheme(Constants.PLAY_SCHEME)
        .authority(Constants.PLAY_HOST)
        .appendQueryParameter("key", key)
        .apply {
            bvid?.let { appendQueryParameter("bvid", it) }
            cid?.takeIf { it > 0L }?.let { appendQueryParameter("cid", it.toString()) }
            sid?.let { appendQueryParameter("sid", it.toString()) }
        }
        .build()

    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(upName ?: "B站播客")
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .apply { cover?.let { setArtworkUri(Uri.parse(it)) } }
        .build()

    return MediaItem.Builder()
        .setMediaId(key)
        .setUri(uri)
        .setMediaMetadata(metadata)
        .build()
}

/** 反向解析，供 PlayerService 的 resolver 使用 */
fun Uri.toEpisodeOrNull(): Episode? {
    if (scheme != Constants.PLAY_SCHEME) return null
    val key = getQueryParameter("key") ?: return null
    val bvid = getQueryParameter("bvid")
    val cid = getQueryParameter("cid")?.toLongOrNull()
    val sid = getQueryParameter("sid")?.toLongOrNull()
    return Episode(
        key = key,
        title = "",
        bvid = bvid,
        cid = cid,
        sid = sid
    )
}
