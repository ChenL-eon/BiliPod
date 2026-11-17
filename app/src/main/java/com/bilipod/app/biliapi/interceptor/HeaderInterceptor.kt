package com.bilipod.app.biliapi.interceptor

import com.bilipod.app.common.Constants
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 统一请求头（默认值）。
 * 风险点：B 站对缺少 Referer / 异常 UA 的请求会直接返回 412 或 -352，
 * 所以这里集中管理，不要在业务代码里零散地加 header。
 *
 * 注意：这里是"兜底默认值"，不是强制覆盖。
 * 媒体下载（playurl 直链）必须用桌面 UA 才能通过 CDN 校验，
 * 业务层已在请求上显式设置 MEDIA_UA，这里不能再改回移动端 UA，否则必然 403。
 */
class HeaderInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()

        if (request.header("User-Agent") == null) {
            builder.header("User-Agent", Constants.UA)
        }
        if (request.header("Referer") == null) {
            builder.header("Referer", Constants.REFERER)
        }
        if (request.header("Accept") == null) {
            builder.header("Accept", "application/json, text/plain, */*")
        }
        builder.header("Accept-Language", "zh-CN,zh;q=0.9")

        // 只有主站接口带 Origin，passport 接口带 Origin 反而会被判定为异常来源
        if (request.url.host == "api.bilibili.com" && request.header("Origin") == null) {
            builder.header("Origin", Constants.ORIGIN)
        }
        return chain.proceed(builder.build())
    }
}
