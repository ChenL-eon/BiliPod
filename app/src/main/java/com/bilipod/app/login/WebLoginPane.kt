package com.bilipod.app.login

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bilipod.app.common.Constants

/**
 * 官方登录页地址。移动端 H5 版。
 * 注意 type=password 参数官方会忽略，实际打开的是短信表单；密码表单靠面板顶部的"账号密码"切换过去。
 */
private const val LOGIN_PAGE = "https://passport.bilibili.com/login?type=password"

/**
 * 账号 / 短信登录面板：内嵌 B 站官方登录页。
 *
 * 为什么不自己画表单：
 * 密码登录的 `token / challenge / validate / seccode` 四个参数必须由极验人机验证产出，
 * 短信登录的 `captcha_key` 同样要先过极验。想自绘表单就得把极验 SDK 塞进 WebView 再回传结果，
 * 这一步的失败模式（挑战拿不到、域名白名单、参数格式随版本变）没有真实账号没法自测，
 * 所以这里让官方页面承载极验和表单，App 只负责把登录完成后的 Cookie 收回来。
 *
 * Cookie 落地靠两处：页面跳回普通 bilibili 域名时自动读一次，以及底部"已登录，继续"手动兜底。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLoginPane(
    onBack: () -> Unit,
    onCookies: (String) -> Unit
) {
    var progress by remember { mutableIntStateOf(0) }
    // 保存 WebView 引用，切换登录方式要用它在页面内触发点击
    var webView by remember { mutableStateOf<WebView?>(null) }
    // 面板内的即时提示：点"继续"但网页还没登录时，要就地告诉用户，不能静默失败
    var inlineHint by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .size(22.dp)
            )
            Spacer(Modifier.width(2.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "账号 / 短信登录",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "官方页面，人机验证在页内完成",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                )
            }
        }

        // 自绘进度条，避开 Material3 LinearProgressIndicator 各版本签名不一致的问题
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            if (progress in 1..99) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress / 100f)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                )
            }
        }

        // 页面里"账号密码登录"那个入口用坐标点不动，这里按文案在页面内触发 click() 兜底
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "短信验证码",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .clickable { switchLoginMode(webView, "短信验证码登录") }
                    .padding(horizontal = 9.dp, vertical = 7.dp)
            )
            Text(
                text = "账号密码",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier
                    .clickable { switchLoginMode(webView, "账号密码登录") }
                    .padding(horizontal = 9.dp, vertical = 7.dp)
            )
        }

        // 头部与页面之间来一条淡分隔线，不让两层白底糊在一起
        HorizontalDivider(
            modifier = Modifier.padding(top = 2.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )

        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            factory = { context ->
                val contentView = WebView(context)
                with(contentView) {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // 移动端 UA：H5 登录页在手机上排版正常，密码和短信都在同一页里切
                    settings.userAgentString = Constants.UA
                    // WebView 默认不接受第三方域 Cookie，passport 登录会跨 passport / bilibili 两个域
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(contentView, true)
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            // 登录成功后页面会被带回普通 bilibili 域名，这时 Cookie 已经写完
                            if (url.contains("bilibili.com") && !url.contains("passport.bilibili.com")) {
                                val cookie = CookieManager.getInstance().getCookie(url)
                                if (!cookie.isNullOrBlank()) onCookies(cookie)
                            }
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress
                        }
                    }
                    loadUrl(LOGIN_PAGE)
                }
                webView = contentView
                contentView
            }
        )

        // 底栏也拉一条分隔线，页面白底和底栏之间才有层次
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = inlineHint ?: "打不开？回上一页扫码登录",
                style = MaterialTheme.typography.labelSmall,
                color = if (inlineHint != null) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                },
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "我已登录，继续",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                modifier = Modifier
                    .clickable {
                        val cookie = CookieManager.getInstance().getCookie("https://www.bilibili.com")
                            ?: CookieManager.getInstance().getCookie("https://passport.bilibili.com")
                        // 只认 SESSDATA：访客 Cookie 也有 buvid3 之类，不能当成登录成功
                        if (!cookie.isNullOrBlank() && cookie.contains("SESSDATA=")) {
                            inlineHint = null
                            onCookies(cookie)
                        } else {
                            inlineHint = "网页里还没登录成功，先在上面完成登录"
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * 在页面里点一个指定文案的登录方式入口。
 *
 * 为什么需要这个：B 站 H5 登录页上的"账号密码登录"是 JS 渲染出来的切换入口，
 * 用坐标点它会落空（页面重写过点击逻辑），所以改成按文案找到叶子节点，再往上最多四层
 * 找到可点击的祖先（a / 带 href / 带 onclick）触发 click()。找到就返回 clicked，找不到返回 notfound。
 */
private fun switchLoginMode(webView: WebView?, label: String) {
    val view = webView ?: return
    val script = """
        (function () {
          var want = '$label';
          var links = document.querySelectorAll('a');
          var hrefs = [];
          for (var i = 0; i < links.length; i++) {
            var href = links[i].getAttribute('href') || '';
            hrefs.push(href + '#' + (links[i].textContent || '').trim().slice(0, 10));
            if (want.indexOf('密码') >= 0 && /password|pwd/.test(href)) {
              links[i].click();
              return 'clicked-href:' + href;
            }
          }
          var nodes = document.querySelectorAll('*');
          for (var i = 0; i < nodes.length; i++) {
            var el = nodes[i];
            if (el.textContent && el.textContent.trim() === want && el.children.length <= 1) {
              el.click();
              return 'clicked-text';
            }
          }
          return 'notfound|links=' + hrefs.slice(0, 8).join(' ; ');
        })()
    """.trimIndent()
    view.evaluateJavascript(script) { result ->
        Log.d("WebLogin", "switch[$label] -> $result")
    }
}
