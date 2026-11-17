package com.bilipod.app.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 仓库地址与发布页，改仓库名或换账号时只改这三行 */
const val PROJECT_URL = "https://github.com/ChenL-eon/BiliPod"
private const val LATEST_RELEASE_API = "https://api.github.com/repos/ChenL-eon/BiliPod/releases/latest"
private const val RELEASES_PAGE = "https://github.com/ChenL-eon/BiliPod/releases"

/** 检测更新只有这六种结果，UI 按状态直接分支，不做字符串判断 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object NoRelease : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Available(val version: String, val url: String) : UpdateState
    data class Failed(val reason: String) : UpdateState
}

/**
 * 查 GitHub 的 latest release。
 *
 * 单独用一份没有 B 站 HeaderInterceptor 的 client：复用 BiliApiClient 的话会带上
 * B 站 UA 和 Referer，对第三方接口既没必要也不礼貌。未认证访问限 60 次/小时，
 * 手动点一下完全够用，所以不带 token。
 */
internal object UpdateChecker {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun check(currentVersion: String): UpdateState = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    // 还没打过 tag 时就是这个结果，属于正常状态而不是错误
                    response.code == 404 -> UpdateState.NoRelease
                    !response.isSuccessful -> UpdateState.Failed("HTTP ${response.code}")
                    else -> {
                        val json = JSONObject(response.body?.string().orEmpty())
                        val tag = json.optString("tag_name").trimStart('v', 'V')
                        val url = json.optString("html_url").ifBlank { RELEASES_PAGE }
                        when {
                            tag.isBlank() -> UpdateState.Failed("返回里没有版本号")
                            compareVersion(tag, currentVersion) > 0 ->
                                UpdateState.Available(tag, url)
                            else -> UpdateState.UpToDate(currentVersion)
                        }
                    }
                }
            }
        }.getOrElse { e ->
            UpdateState.Failed(e.message?.take(48) ?: "网络异常")
        }
    }

    /** 只比数字段，0.10.0 要大于 0.9.0；tag 里带后缀（如 1.0.0-beta）时后缀不参与比较 */
    private fun compareVersion(a: String, b: String): Int {
        val pa = a.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }
}

/**
 * 关于浮层：版本、项目地址、检测更新。
 * 视觉跟设置页的清空确认弹层一致（圆角 20、无阴影、无描边）。
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember { versionNameOf(context) }
    val versionText = remember { "$versionName（${versionCodeOf(context)}）" }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "关于 BiliPod",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "版本 $versionText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))

                AboutRow(
                    title = "项目地址",
                    value = "github.com/ChenL-eon/BiliPod",
                    valueColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    onClick = { openUrl(context, PROJECT_URL) }
                )

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "关闭",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutRow(
    title: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 检测更新浮层：和"关于"分开成两个入口，一进来就自己查，不用再点一下。
 * 结果只有一句话 + 一两个动作，别做成又一层菜单。
 */
@Composable
fun UpdateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember { versionNameOf(context) }
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Checking) }

    LaunchedEffect(Unit) {
        state = UpdateChecker.check(versionName)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "检测更新",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "当前版本 $versionName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                Spacer(Modifier.height(14.dp))

                val headline = when (val s = state) {
                    is UpdateState.Idle, is UpdateState.Checking -> "正在检查 GitHub 上的最新版本…"
                    is UpdateState.NoRelease -> "还没有发布版本"
                    is UpdateState.UpToDate -> "已经是最新版本"
                    is UpdateState.Available -> "发现新版本 v${s.version}"
                    is UpdateState.Failed -> "检测失败"
                }
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleSmall,
                    color = when (state) {
                        is UpdateState.Available -> MaterialTheme.colorScheme.primary
                        is UpdateState.UpToDate -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    }
                )

                // 详情一行就够：失败给原因，没发版给说明，其余不啰嗦
                val detail = when (val s = state) {
                    is UpdateState.Failed -> s.reason
                    is UpdateState.NoRelease -> "作者还没打过 release tag，仓库里也没有可下载的安装包"
                    is UpdateState.Checking -> null
                    else -> null
                }
                if (detail != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                }

                val available = state as? UpdateState.Available
                if (available != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "打开发布页",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { openUrl(context, available.url) }
                            .padding(vertical = 4.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "关闭",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/** 应用内打开外部链接，没有浏览器时静默失败，不让面板崩掉 */
private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun versionNameOf(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.1.0"
    }.getOrDefault("0.1.0")

private fun versionCodeOf(context: Context): Long =
    runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        PackageInfoCompat.getLongVersionCode(info)
    }.getOrDefault(1L)
