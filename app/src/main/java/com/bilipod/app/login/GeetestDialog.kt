package com.bilipod.app.login

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 人机验证面板：用 WebView 承载极验 V3，验证结果通过 JS Bridge 回传。
 *
 * 为什么不自己画滑块：极验是黑盒，只有官方 gt.js 能产出服务端认可的 validate / seccode。
 * 也不用官方登录页：这里只要极验那一小块，表单留在 Compose 里，报错和加载态都能自己控制。
 *
 * 实测（2026-11）：B 站 captcha 接口给的 gt + challenge 在本地页面初始化时不做域名校验，
 * 所以可以把 gt.js 直接放进 assets 页面跑。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GeetestOverlay(
    gt: String,
    challenge: String,
    onResult: (challenge: String, validate: String, seccode: String) -> Unit,
    onError: (String) -> Unit,
    onCancel: () -> Unit
) {
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(Unit) {
        onDispose { Log.d("Geetest", "overlay disposed") }
    }

    // 不再画外层卡片：只留一层半透明遮罩，极验自己的面板直接浮在上面。
    // 遮罩负责"背景变暗"，页面负责"点空白处关掉"，WebView 全屏但背景透明。
    BackHandler(enabled = true) { onCancel() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // 关掉缩放和滚动：页面一旦能滚，触摸坐标会和渲染位置错开（实测偏 100+ 像素）
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                    settings.useWideViewPort = false
                    settings.loadWithOverviewMode = false
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = WebView.OVER_SCROLL_NEVER
                    // 极验的 gt.js 从 static.geetest.com 拉，用移动端 UA 保证渲染尺寸正常
                    settings.userAgentString = com.bilipod.app.common.Constants.UA
                    // 透明底：让 Compose 那层遮罩透上来，视觉上就只有极验面板浮着
                    setBackgroundColor(0x00000000)
                    addJavascriptInterface(
                        GeetestBridge(
                            mainHandler = mainHandler,
                            onResultCb = onResult,
                            onErrorCb = onError,
                            onCloseCb = onCancel
                        ),
                        "AndroidBridge"
                    )
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                            Log.d("Geetest", "console[${msg.lineNumber()}]: ${msg.message()}")
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            tryInit(view, 0, gt, challenge)
                        }
                    }
                    // file:// 加载：页面只靠 gt.js 的 JSONP 拉参数，不需要同域，实测也不校验来源
                    loadUrl("file:///android_asset/geetest.html")
                }
                webView
            }
        )
    }
}

/**
 * 极验回调桥。JS 线程到这里，所有 UI 回调统一 post 到主线程。
 *
 * 注意：回调属性一律带 Cb 后缀。和 @JavascriptInterface 方法同名的话，
 * 方法体内的 onClose() 会被解析成"调用自己"，导致 post 无限递归、
 * 上层永远收不到回调（本轮踩过，日志表现为同一毫秒刷几十条 bridge onClose）。
 */
private class GeetestBridge(
    private val mainHandler: Handler,
    private val onResultCb: (String, String, String) -> Unit,
    private val onErrorCb: (String) -> Unit,
    private val onCloseCb: () -> Unit
) {

    // 极验在服务端确认后会把 onSuccess 连打好几次（同一份 validate），
    // JS 侧虽然也拦了一道，但页面重载时那个标志会重置，这里再守一次
    private val reported = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * 软关闭令牌。极验点完"确认"提交成功时，会先把自己的弹层收起来并触发 onClose，
     * 过一两秒才异步回调 onSuccess。早期这里一收到 onClose 就清 captcha，
     * 结果 onResult 到达时面板已关、参数被丢（日志 "dropped: 面板已关闭"），短信永远发不出去。
     * 所以 onClose 只挂一个延时关闭，结果一到就用 onResult 把令牌打掉。
     */
    private val pendingSoftClose = java.util.concurrent.atomic.AtomicBoolean(false)

    @JavascriptInterface
    fun onResult(challenge: String, validate: String, seccode: String) {
        pendingSoftClose.set(false)
        if (!reported.compareAndSet(false, true)) {
            Log.d("Geetest", "result dropped (already reported)")
            return
        }
        Log.d("Geetest", "result: challenge=${challenge.take(8)} validate=${validate.take(8)}")
        mainHandler.post { onResultCb(challenge, validate, seccode) }
    }

    @JavascriptInterface
    fun onError(message: String) {
        Log.d("Geetest", "error: $message")
        mainHandler.post { onErrorCb(message) }
    }

    /** 页面内的调试输出，只在 logcat 里看，不给用户 */
    @JavascriptInterface
    fun onDebug(message: String) {
        Log.d("Geetest", "js: $message")
    }

    /** 用户主动关掉（点面板外的空白、点面板上的 ×、返回键）：立刻收 */
    @JavascriptInterface
    fun onClose() {
        Log.d("Geetest", "bridge onClose")
        pendingSoftClose.set(false)
        mainHandler.post { onCloseCb() }
    }

    /** 极验自己收摊：先等验证结果，等不到再收 */
    @JavascriptInterface
    fun onSoftClose() {
        Log.d("Geetest", "bridge softClose")
        if (!pendingSoftClose.compareAndSet(false, true)) { return }
        mainHandler.postDelayed({
            if (pendingSoftClose.get()) {
                Log.d("Geetest", "softClose fired (no result)")
                onCloseCb()
            } else {
                Log.d("Geetest", "softClose skipped (result arrived)")
            }
        }, SOFT_CLOSE_MS)
    }
}

/** 极验提交后大约 2 秒才回 validate，慢网再留点余量 */
private const val SOFT_CLOSE_MS = 4000L

/** 拼 JS 字符串字面量，防止参数里的引号把脚本打断 */
private fun quote(raw: String): String = "'" + raw.replace("\\", "\\\\").replace("'", "\\'") + "'"

/** gt.js 没到之前的重试节奏：12 次 × 400ms ≈ 5 秒，够 3G 慢网络把脚本拉下来 */
private const val GT_INIT_MAX_ATTEMPTS = 12
private const val GT_INIT_RETRY_MS = 400L

/**
 * 等 gt.js 就绪再初始化。
 *
 * 页面是 file:// 加载的本地文件，gt.js 要从 static.geetest.com 拉，网络慢的时候
 * onPageFinished 时脚本可能还没到，直接调 initGeetest 会 reference error，
 * 所以这里用"检查到函数存在才调用"的重试。
 */
private fun tryInit(view: WebView, attempt: Int, gt: String, challenge: String) {
    val script = """
        (function () {
          if (typeof initGeetest !== 'function') { return 'wait'; }
          initGeetestCaptcha(${quote(gt)}, ${quote(challenge)});
          return 'ok';
        })()
    """.trimIndent()
    view.evaluateJavascript(script) { result ->
        Log.d("Geetest", "tryInit#$attempt -> $result")
        if (result != null && result.contains("wait")) {
            if (attempt < GT_INIT_MAX_ATTEMPTS) {
                view.postDelayed({ tryInit(view, attempt + 1, gt, challenge) }, GT_INIT_RETRY_MS)
            } else {
                // 别让用户对着"正在加载"干等：把失败写回页面，并让上层给个错误提示
                Log.d("Geetest", "gt.js 没加载出来")
                view.evaluateJavascript(
                    "(function(){var t=document.getElementById('tip');" +
                        "if(t){t.innerText='人机验证加载失败，请检查网络后重试';}})()",
                    null
                )
            }
        }
    }
}
