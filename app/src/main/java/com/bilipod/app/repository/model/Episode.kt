package com.bilipod.app.repository.model

/**
 * 统一的单集模型，播放/缓存/历史三处共用。
 *
 * key 规则（2026-11 修正）：
 *  - 视频稿件：v_{bvid}_{cid}，cid 未知（搜索/收藏接口不返回 cid）时退化成 v_{bvid}
 * 之前只用 v_{bvid} 是错的：多分 P 稿件（韩顺平那套 910 集共用一个 bvid）会让
 * 缓存、播放历史、断点位置全部串成一条记录，下载也只会落一个文件。
 *
 * 音频区（`a_{sid}`）已于 2026-11-15 停运并清掉相关接口/构造器，`sid` 字段只作为
 * 实体表的历史列保留，避免动 Room schema。
 */
data class Episode(
    val key: String,
    val title: String,
    val bvid: String? = null,
    val cid: Long? = null,
    val sid: Long? = null,
    val cover: String? = null,
    val upName: String? = null,
    val durationSec: Long = 0L
) {
    companion object {
        fun video(
            bvid: String,
            cid: Long,
            title: String,
            cover: String?,
            upName: String?,
            durationSec: Long
        ): Episode = Episode(
            key = videoKey(bvid, cid),
            title = title,
            bvid = bvid,
            cid = cid,
            cover = cover,
            upName = upName,
            durationSec = durationSec
        )

        /** cid 有效才带上，否则保持旧的 bvid 级 key（搜索/收藏拿不到 cid 时用） */
        fun videoKey(bvid: String, cid: Long?): String =
            if ((cid ?: 0L) > 0L) "v_${bvid}_$cid" else "v_$bvid"

        /**
         * 从 key 里还原 bvid，兼容 v_BVxxx 与 v_BVxxx_123 两种写法。
         * 搜索/收藏这类"只认 bvid 不认 cid"的卡片靠它回落到稿件级缓存状态。
         */
        fun bvidOfKey(key: String): String? {
            if (!key.startsWith("v_")) return null
            val body = key.removePrefix("v_")
            val cut = body.indexOf('_')
            return if (cut > 0) body.substring(0, cut) else body
        }

        /** 稿件级别名 key（用于 UI 侧兼容） */
        fun bvidLevelKey(bvid: String): String = "v_$bvid"
    }
}

/** 稿件/合集详情，episodes 是选集列表 */
data class PodcastDetail(
    val title: String,
    val cover: String?,
    val upName: String?,
    val ownerMid: Long,
    val desc: String?,
    val episodes: List<Episode>,
    /** 选集来自 ugc_season（UP 主合集）而不是分 P，用来区分"合集"和"多分 P 稿件" */
    val isSeason: Boolean = false
)

/** 收藏夹条目 */
data class FavFolder(
    val id: Long,
    val title: String,
    val count: Int
)

/** 登录用户信息 */
data class UserProfile(
    val mid: Long,
    val name: String,
    val face: String?
)
