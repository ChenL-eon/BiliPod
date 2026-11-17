package com.bilipod.app.common

/**
 * 全局常量。
 * UA 使用标准移动端浏览器 UA，不要伪装成 App 端 UA，减少被风控标记的概率。
 */
object Constants {
    const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"

    /**
     * 媒体流（音频 CDN）专用 UA。
     *
     * 风险点（实测结论，2026-11 于模拟器 + 宿主机 curl 双向验证）：
     * playurl 是按 platform=pc 签发的，CDN 会校验 UA 与 platform 是否一致。
     * 同一条带 buvid 的直链：桌面 UA 返回 206，移动端 UA 返回 403。
     * 所以接口请求继续用移动端 UA，只有取音频流时切换成桌面 UA。
     */
    const val MEDIA_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"

    const val REFERER = "https://www.bilibili.com/"
    const val ORIGIN = "https://www.bilibili.com"

    const val API_BASE = "https://api.bilibili.com/"
    const val PASSPORT_BASE = "https://passport.bilibili.com/"

    /** 扫码轮询间隔。风险点：间隔过短会被 passport 风控，2s 是官方 Web 端近似值 */
    const val QR_POLL_INTERVAL_MS = 2_000L
    /** 二维码有效期约 180s，超过就停止轮询，不做无限重试 */
    const val QR_POLL_MAX_MS = 180_000L

    /** 缓存容量上限默认 1GB，可在设置里调整 */
    const val CACHE_MAX_BYTES_DEFAULT = 1L * 1024 * 1024 * 1024
    const val CACHE_DIR_NAME = "audio_cache"

    /** 播放器内部使用的伪协议，真正地址在播放前实时解析 */
    const val PLAY_SCHEME = "bili"
    const val PLAY_HOST = "play"
}
