package com.bilipod.app.biliapi

import android.util.Log
import com.bilipod.app.common.Constants
import com.google.gson.JsonParser
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * wbi 密钥缓存。
 * nav 接口每次请求都会带最新密钥，但没必要每个业务请求都取一次，这里缓存 12 小时；
 * 拿到 -403 / -352 时可以调用 invalidate() 强制刷新。
 *
 * 注意：这里用独立 OkHttpClient（不经过签名拦截器），避免循环依赖。
 */
class WbiKeyStore(private val cookieJar: CookieJar) {

    @Volatile
    private var imgKey: String? = null

    @Volatile
    private var subKey: String? = null

    @Volatile
    private var expireAt: Long = 0L

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(object : Interceptor {
                override fun intercept(chain: Interceptor.Chain): Response {
                    val request = chain.request().newBuilder()
                        .header("User-Agent", Constants.UA)
                        .header("Referer", Constants.REFERER)
                        .build()
                    return chain.proceed(request)
                }
            })
            .build()
    }

    /** 取密钥，取不到返回 null，调用方按未签名请求处理 */
    fun keys(force: Boolean = false): Pair<String, String>? {
        val now = System.currentTimeMillis()
        val i = imgKey
        val s = subKey
        if (!force && i != null && s != null && now < expireAt) return i to s

        synchronized(this) {
            if (!force && imgKey != null && subKey != null && System.currentTimeMillis() < expireAt) {
                return imgKey!! to subKey!!
            }
            return try {
                fetch()
            } catch (t: Throwable) {
                Log.w(TAG, "wbi 密钥获取失败，本次请求不带签名", t)
                null
            }
        }
    }

    /** 供 403 场景强制刷新 */
    fun invalidate() {
        expireAt = 0L
    }

    private fun fetch(): Pair<String, String>? {
        val request = Request.Builder()
            .url("${Constants.API_BASE}x/web-interface/nav")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val root = JsonParser.parseString(body).asJsonObject
            // 注意：未登录时 nav 返回 code=-101（账号未登录），但 data.wbi_img 仍然是有效的，
            // 只是 isLogin=false。所以这里不能以 code==0 作为前提，只要拿到 wbi_img 就够。
            val data = root.getAsJsonObject("data") ?: return null
            val wbiImg = data.getAsJsonObject("wbi_img") ?: return null
            val img = wbiImg.get("img_url")?.asString?.substringAfterLast('/')?.substringBefore('.')
                ?: return null
            val sub = wbiImg.get("sub_url")?.asString?.substringAfterLast('/')?.substringBefore('.')
                ?: return null
            imgKey = img
            subKey = sub
            expireAt = System.currentTimeMillis() + CACHE_TTL_MS
            return img to sub
        }
    }

    private companion object {
        const val TAG = "WbiKeyStore"
        const val CACHE_TTL_MS = 12 * 60 * 60 * 1000L
    }
}
