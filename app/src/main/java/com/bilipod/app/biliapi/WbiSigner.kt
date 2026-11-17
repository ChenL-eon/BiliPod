package com.bilipod.app.biliapi

import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale

/**
 * B 站 wbi 签名工具，完全与业务解耦。
 *
 * 算法来源是 Web 端公开实现：
 *  1. 取 nav 接口返回的 wbi_img.img_url / sub_url，各自去掉路径和后缀得到 imgKey / subKey；
 *  2. 拼接后按固定 64 位索引表重排，取前 32 位得到 mixinKey；
 *  3. 业务参数 + wts 按 key 升序拼接，值里去掉 !'()* 字符后 URL 编码；
 *  4. md5(拼接串 + mixinKey) 得到 w_rid。
 *
 * 风险点：索引表和密钥来源都可能随 B 站前端更新变化，所以这里单独抽成一个无状态工具，
 * 后续只需要改这个文件；密钥获取/刷新放在 WbiKeyStore，避免业务频繁请求 nav。
 */
object WbiSigner {

    /** 官方前端写死的混排索引表，长度 64 */
    private val MIXIN_KEY_ENC_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
        61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
        36, 20, 34, 44, 52
    )

    /** 值里需要剔除的特殊字符，与前端正则 /[!'()*]/g 一致 */
    private val ILLEGAL_CHARS = Regex("[!'()*]")

    /** 由 imgKey + subKey 生成 32 位 mixinKey */
    fun mixinKey(imgKey: String, subKey: String): String {
        val raw = imgKey + subKey
        val sb = StringBuilder(64)
        MIXIN_KEY_ENC_TAB.forEach { index ->
            if (index < raw.length) sb.append(raw[index])
        }
        return sb.toString().take(32)
    }

    /**
     * 对参数签名，返回包含 wts 与 w_rid 的完整参数表。
     * @param params 业务参数，value 为 null 的会被丢弃
     */
    fun sign(
        params: Map<String, String?>,
        imgKey: String,
        subKey: String,
        wts: Long = System.currentTimeMillis() / 1000
    ): Map<String, String> {
        val key = mixinKey(imgKey, subKey)
        val filtered = LinkedHashMap<String, String>()
        params.forEach { (k, v) ->
            if (v != null) filtered[k] = v.replace(ILLEGAL_CHARS, "")
        }
        filtered["wts"] = wts.toString()

        val query = filtered.entries
            .sortedBy { it.key }
            .joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }

        filtered["w_rid"] = md5(query + key)
        return filtered
    }

    /** 等价于 JS encodeURIComponent（空格编码成 %20 而不是 +） */
    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /** 小写 md5 */
    fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { String.format(Locale.US, "%02x", it) }
    }
}
