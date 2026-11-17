package com.bilipod.app.biliapi

import com.bilipod.app.biliapi.interceptor.HeaderInterceptor
import com.bilipod.app.biliapi.interceptor.WbiSignInterceptor
import com.bilipod.app.common.Constants
import com.bilipod.app.common.SecureCookieStore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 网络层组装。
 * - Cookie 由 SecureCookieStore 统一注入（OkHttp 的 CookieJar 在 BridgeInterceptor 里生效）
 * - UA / Referer 由 HeaderInterceptor 统一注入
 * - wbi 签名由 WbiSignInterceptor 统一注入
 * 业务代码只看到干净的 suspend 函数。
 */
class BiliApiClient(val cookieStore: SecureCookieStore) {

    private val gson: Gson = GsonBuilder().setLenient().create()

    private val wbiKeyStore = WbiKeyStore(cookieStore)

    /** 对外暴露给下载器等非 Retrofit 场景复用同一套 Cookie/请求头 */
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieStore)
        .addInterceptor(HeaderInterceptor())
        .addInterceptor(WbiSignInterceptor(wbiKeyStore))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val api: BiliApiService = buildRetrofit(Constants.API_BASE).create(BiliApiService::class.java)

    val authApi: AuthApiService =
        buildRetrofit(Constants.PASSPORT_BASE).create(AuthApiService::class.java)

    /** 403 / 风控后可用于刷新 wbi 密钥 */
    fun refreshWbiKeys() = wbiKeyStore.invalidate()

    /**
     * 预热：访问一次网页首页，让服务端通过 Set-Cookie 下发真正的 buvid3 / buvid4 / b_nut。
     *
     * 风险点（实测结论，2026-11 于模拟器验证）：
     * 自己把 spi 的 b_3 拼成 buvid3 不算"已激活的设备指纹"，
     * 这样拿到的 playurl 地址里 buvid 参数为空，CDN 直接 403；
     * 必须是首页 Set-Cookie 下发的那一份，直链才带 buvid，CDN 才放行。
     */
    suspend fun warmupHome() = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://www.bilibili.com/").get().build()
        runCatching {
            httpClient.newCall(request).execute().use { it.body?.string() }
        }
        Unit
    }

    private fun buildRetrofit(baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
}
