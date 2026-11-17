package com.bilipod.app.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bilipod.app.common.Constants

/**
 * B 站图片统一入口。
 *
 * 两个坑都要在这里统一处理：
 * 1. 接口返回的 pic 经常是协议相对地址（`//i1.hdslb.com/...`），
 *    Coil 不认没有 scheme 的 url，必须先补成 https；
 * 2. i0.hdslb.com 有防盗链，不带 Referer / UA 会拿到 403 占位图。
 */
@Composable
fun BiliImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val data = url?.trim()?.let { raw ->
        when {
            raw.isEmpty() -> null
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("http://") -> raw.replaceFirst("http://", "https://")
            else -> raw
        }
    }

    // 统一底色：加载中/加载失败都不会出现刺眼的色块，和卡片底色同一层次
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (data == null) {
            Text(
                text = "封面",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            )
            return@Box
        }
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(data)
                .setHeader("Referer", Constants.REFERER)
                .setHeader("User-Agent", Constants.UA)
                // 安静风格：不做淡入动画，避免列表滑动时闪烁
                .crossfade(false)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale
        )
    }
}
