package com.bilipod.app.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.bilipod.app.common.Constants
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * 通知栏 / 锁屏封面的加载器。
 *
 * 为什么要自定义：Media3 默认用 DefaultBitmapLoader，请求封面时不带 Referer / UA，
 * B 站的 i0.hdslb.com 有防盗链，直接 403，通知里就回落成一个占位图标（真机实测 2026-11-15）。
 * 这里复用 App 自己的 OkHttp（带 Referer + UA + Cookie），拉到图再交给 Media3。
 */
@OptIn(UnstableApi::class)
class BiliBitmapLoader(private val client: OkHttpClient) : BitmapLoader {

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        val bitmap = decodeSampled(data)
        if (bitmap != null) future.set(bitmap) else future.setException(IOException("decode failed"))
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = load(uri.toString())

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap> =
        load(metadata.artworkUri?.toString())

    private fun load(rawUrl: String?): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        val raw = rawUrl?.trim().orEmpty()
        if (raw.isEmpty()) {
            future.setException(IOException("empty artwork uri"))
            return future
        }
        // 接口给的多是 //i0.hdslb.com/...，没有 scheme；http 也要换成 https
        val url = when {
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("http://") -> raw.replaceFirst("http://", "https://")
            else -> raw
        }
        val request = Request.Builder()
            .url(url)
            .header("Referer", Constants.REFERER)
            .header("User-Agent", Constants.UA)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // 回调里任何异常都不能逃出这个线程：OkHttp 的 AsyncCall 对非 IOException 的 Throwable
                // 会直接 rethrow，落到线程未捕获处理器上就是整个进程闪退。
                // 无网时 DNS 解析失败正好走这条路径（实测 2026-11-17 于模拟器）。
                runCatching { future.setException(e) }
            }

            override fun onResponse(call: Call, response: Response) {
                runCatching {
                    response.use {
                        if (!it.isSuccessful) {
                            // 403 之类的防盗链回包不是图片，别把错误页当图去解码
                            future.setException(IOException("HTTP ${it.code}"))
                            return@use
                        }
                        val bytes = it.body?.bytes()
                        val bitmap = bytes?.let { data -> decodeSampled(data) }
                        if (bitmap != null) future.set(bitmap)
                        else future.setException(IOException("decode failed"))
                    }
                }.onFailure { runCatching { future.setException(it) } }
            }
        })
        return future
    }

    /**
     * 通知/锁屏只需要一百多 dp 的缩略图，B 站封面常见 1920×1080，
     * 原尺寸解码一张就是 8MB 的 ARGB_8888，连续切集容易把堆顶爆。
     * 先量尺寸再按 2 的幂采样，最长边压到 MAX_SIDE 以内。
     */
    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longSide = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longSide / (sample * 2) >= MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private companion object {
        /** 缩略图最长边上限（像素）；再大在通知栏里也看不出差别 */
        const val MAX_SIDE = 512
    }
}
