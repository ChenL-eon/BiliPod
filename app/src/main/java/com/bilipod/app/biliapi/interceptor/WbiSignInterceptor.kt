package com.bilipod.app.biliapi.interceptor

import com.bilipod.app.biliapi.WbiKeyStore
import com.bilipod.app.biliapi.WbiSigner
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * wbi 签名拦截器：把签名逻辑从业务层彻底剥离。
 * 只对需要签名的路径生效，其他接口保持原样。
 */
class WbiSignInterceptor(private val keyStore: WbiKeyStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (!needSign(url)) return chain.proceed(request)
        if (url.queryParameter("w_rid") != null) return chain.proceed(request)

        // 取不到密钥时按普通请求发出去，上层的错误处理会给出提示
        val keys = keyStore.keys() ?: return chain.proceed(request)

        val params = LinkedHashMap<String, String>()
        url.queryParameterNames.forEach { name ->
            url.queryParameter(name)?.let { params[name] = it }
        }
        val signed = WbiSigner.sign(params, keys.first, keys.second)

        val newUrl = url.newBuilder().apply {
            signed.forEach { (k, v) -> setQueryParameter(k, v) }
        }.build()

        return chain.proceed(request.newBuilder().url(newUrl).build())
    }

    private fun needSign(url: HttpUrl): Boolean {
        if (url.host != "api.bilibili.com") return false
        val path = url.encodedPath
        // 路径自带 /wbi/ 的接口一定需要签名；另外少数老接口也需要，显式列出
        return path.contains("/wbi/") || path in EXTRA_SIGN_PATHS
    }

    private companion object {
        val EXTRA_SIGN_PATHS = setOf(
            "/x/polymer/web-space/seasons_archives_list"
        )
    }
}
