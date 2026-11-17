package com.bilipod.app.repository

import com.bilipod.app.biliapi.BiliApiClient
import com.bilipod.app.common.AppResult
import com.bilipod.app.common.Constants
import com.bilipod.app.common.SecureCookieStore
import com.bilipod.app.repository.model.Episode

/**
 * 播放地址仓库。
 *
 * 核心原则：B 站的音频直链带时效 token，绝不长期保存，
 * 每次播放 / 每次缓存下载前都实时解析一次；过期或 403 时重新解析即可。
 *
 * 风险点：
 *  - playurl 接口需要登录 Cookie 才能拿到高码率音频，未登录可能拿不到 dash.audio；
 *  - CDN 会校验 Referer，播放时必须带上，否则 403；
 *  - 短时间大量调用 playurl 会触发风控（-352 / -412），不要为了"预加载所有集"而循环请求。
 */
class PlayUrlRepository(
    private val client: BiliApiClient,
    private val cookieStore: SecureCookieStore
) {

    /** 解析结果：真实地址 + 播放/下载需要的请求头 */
    data class ResolvedAudio(
        val url: String,
        val headers: Map<String, String>
    )

    suspend fun resolveAudioUrl(episode: Episode): AppResult<ResolvedAudio> = try {
        // 音频区接口已于 2026-11-15 停运，这里只剩稿件链路
        val url = episode.bvid?.let { resolveVideo(it, episode.cid) }
        if (url.isNullOrBlank()) {
            AppResult.Failure(-404, "未取到可播放的音频地址")
        } else {
            AppResult.Success(ResolvedAudio(url, playbackHeaders()))
        }
    } catch (t: Throwable) {
        AppResult.Error(t)
    }

    /**
     * 播放与下载共用的请求头。
     *
     * 注意：这里用 MEDIA_UA（桌面 UA）而不是接口用的移动端 UA。
     * playurl 按 platform=pc 签发，CDN 会校验 UA 与 platform 一致，
     * 用移动端 UA 取音频流会稳定 403（实测）。
     */
    fun playbackHeaders(): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        headers["User-Agent"] = Constants.MEDIA_UA
        headers["Referer"] = Constants.REFERER
        headers["Origin"] = Constants.ORIGIN
        cookieStore.value("SESSDATA")?.let { headers["Cookie"] = "SESSDATA=$it" }
        return headers
    }

    /** 播放地址失效（403 / 410）时刷新 wbi 密钥，下一次解析会用新密钥 */
    fun onForbidden() = client.refreshWbiKeys()

    private suspend fun resolveVideo(bvid: String, cid: Long?): String? {
        val realCid = cid?.takeIf { it > 0L } ?: fetchFirstCid(bvid) ?: return null
        val resp = client.api.playUrl(bvid = bvid, cid = realCid)
        if (!resp.isSuccess) return null
        val dash = resp.data?.dash
        val audio = dash?.audio.orEmpty().maxByOrNull { it.bandwidth }
        audio?.baseUrl?.takeIf { it.isNotBlank() }?.let { return it }
        // 少数老稿件只有 durl（FLV/MP4），直接取音频轨也能播
        return resp.data?.durl?.firstOrNull()?.url
    }

    /** 搜索/收藏接口不给 cid，这里按需补齐，并且只补一次 */
    private suspend fun fetchFirstCid(bvid: String): Long? {
        val resp = client.api.videoDetail(bvid)
        if (!resp.isSuccess) return null
        return resp.data?.pages?.firstOrNull()?.cid?.takeIf { it > 0L }
    }

    /**
     * 供缓存层使用：条目 cid 未知时补一个 cid，才能算出"每一集唯一"的 key。
     * 失败返回 null，调用方退回 bvid 级 key，不阻塞下载。
     */
    suspend fun firstCidOf(bvid: String): Long? = try {
        fetchFirstCid(bvid)
    } catch (t: Throwable) {
        null
    }
}
