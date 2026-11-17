package com.bilipod.app.repository

import com.bilipod.app.biliapi.ApiResponse
import com.bilipod.app.biliapi.BiliApiClient
import com.bilipod.app.biliapi.model.CaptchaData
import com.bilipod.app.biliapi.model.LoginData
import com.bilipod.app.biliapi.model.NavData
import com.bilipod.app.biliapi.model.QrGenerateData
import com.bilipod.app.biliapi.model.QrPollData
import com.bilipod.app.biliapi.model.SmsSendData
import com.bilipod.app.biliapi.model.WebKeyData
import com.bilipod.app.common.AppResult
import com.bilipod.app.common.BiliRsa
import com.bilipod.app.common.SecureCookieStore
import com.bilipod.app.repository.model.UserProfile

/**
 * 登录态仓库：二维码生成 / 轮询、buvid 初始化、用户信息。
 *
 * 关于 Cookie：
 * passport 扫码确认后服务端通过 Set-Cookie 下发 SESSDATA / bili_jct / DedeUserID 等，
 * OkHttp 的 CookieJar（SecureCookieStore）会自动接收并加密存储，业务层不需要解析 query。
 *
 * 风险点：轮询必须串行且带间隔（见 Constants.QR_POLL_INTERVAL_MS），并发轮询会触发风控。
 */
class AuthRepository(
    private val client: BiliApiClient,
    private val cookieStore: SecureCookieStore
) {

    fun isLoggedIn(): Boolean = cookieStore.isLoggedIn()

    /**
     * 首次启动先拿一次设备指纹（buvid）。
     *
     * 实测结论（2026-11 于模拟器 + 真实网络验证）：
     * 1. 只带 buvid4、不带 buvid3 时搜索能正常返回结果，但 playurl 拿到的直链
     *    buvid 参数为空，CDN 一律 403；
     * 2. 手动把 spi 的 b_3 拼成 buvid3，会被判定为"未激活的设备指纹"，
     *    搜索返回 code=0 但 data 只有 v_voucher（风控包）；
     * 3. 只有首页 Set-Cookie 下发的那份 buvid3 + b_nut，才是被服务端认账的，
     *    带它的 playurl 直链里 buvid 非空，CDN 返回 206 可播放。
     * 所以这里先访问一次网页首页，让 CookieJar 自动接住 Set-Cookie。
     */
    suspend fun ensureBuvid() {
        val hasBuvid3 = cookieStore.value("buvid3") != null
        val hasBuvid4 = cookieStore.value("buvid4") != null
        if (hasBuvid3 && hasBuvid4) return

        // 只有完全没有指纹时才去首页。已有 buvid3 的话绝不能再走一次：
        // 首页的 Set-Cookie 会覆盖成新的 buvid3，设备指纹一变，
        // 服务端会认为这是一台新设备，之前登录用的 SESSDATA 就可能被判失效。
        if (!hasBuvid3) {
            client.warmupHome()
            if (cookieStore.value("buvid3") != null) {
                // buvid3 拿到了，buvid4 还缺就单独补，不要为了 buvid4 再刷一次首页
                fillBuvid4()
                return
            }
        }

        // 兜底：首页没给（极少见）时用 spi 的 b_4 占位，至少保留设备指纹
        fillBuvid4()
    }

    private suspend fun fillBuvid4() {
        if (cookieStore.value("buvid4") != null) return
        val resp = client.api.buvid()
        if (resp.isSuccess) {
            resp.data?.b4?.let { cookieStore.put("buvid4", it) }
        }
    }

    suspend fun generateQr(): AppResult<QrCodeInfo> = try {
        val resp: ApiResponse<QrGenerateData> = client.authApi.qrGenerate()
        val data = resp.data
        if (resp.isSuccess && data?.url != null && data.qrcodeKey != null) {
            AppResult.Success(QrCodeInfo(data.url, data.qrcodeKey))
        } else {
            AppResult.Failure(resp.code, resp.errorText)
        }
    } catch (t: Throwable) {
        AppResult.Error(t)
    }

    /** 轮询一次二维码状态 */
    suspend fun pollQr(qrcodeKey: String): AppResult<QrPollState> = try {
        val resp: ApiResponse<QrPollData> = client.authApi.qrPoll(qrcodeKey)
        val data = resp.data
        when {
            !resp.isSuccess || data == null -> AppResult.Failure(resp.code, resp.errorText)
            data.code == 0 -> {
                // 扫码成功时 Cookie 已经由服务端 Set-Cookie 写入 CookieJar
                ensureBuvid()
                AppResult.Success(QrPollState.Success)
            }
            data.code == 86101 -> AppResult.Success(QrPollState.Waiting)
            data.code == 86090 -> AppResult.Success(QrPollState.Scanning)
            data.code == 86038 -> AppResult.Success(QrPollState.Expired)
            else -> AppResult.Failure(data.code, data.message ?: "未知扫码状态")
        }
    } catch (t: Throwable) {
        AppResult.Error(t)
    }

    suspend fun profile(): AppResult<UserProfile?> = try {
        val resp: ApiResponse<NavData> = client.api.nav()
        val data = resp.data
        if (resp.isSuccess && data != null && data.isLogin) {
            AppResult.Success(UserProfile(data.mid, data.uname.orEmpty(), data.face))
        } else {
            // 未登录不是错误，返回 null 让 UI 决定是否引导登录
            AppResult.Success(null)
        }
    } catch (t: Throwable) {
        AppResult.Error(t)
    }

    fun logout() = cookieStore.clear()

    // ---------------- 密码 / 短信登录 ----------------

    /**
     * 取一组极验参数。
     *
     * 密码登录和短信发送都必须带 token + challenge + validate + seccode，
     * 而这组参数的 challenge 有时效（几十秒），所以每次点击前现取，不缓存。
     */
    suspend fun requestCaptcha(): AppResult<CaptchaChallenge> = try {
        val resp: ApiResponse<CaptchaData> = client.authApi.captcha()
        val data = resp.data
        val geetest = data?.geetest
        if (resp.isSuccess && data?.token != null && geetest?.gt != null && geetest.challenge != null) {
            AppResult.Success(
                CaptchaChallenge(
                    type = data.type.orEmpty(),
                    token = data.token,
                    gt = geetest.gt,
                    challenge = geetest.challenge
                )
            )
        } else {
            AppResult.Failure(resp.code, resp.errorText)
        }
    } catch (t: Throwable) {
        AppResult.Error(t)
    }

    /**
     * 账号密码登录。
     *
     * 顺序不能变：先取公钥（hash + PEM 都会过期）→ 本地 RSA 加密 → 提交。
     * 成功时 SESSDATA / bili_jct 等由服务端 Set-Cookie 下发，CookieJar 会自动接住。
     */
    suspend fun passwordLogin(
        username: String,
        password: String,
        captcha: CaptchaChallenge,
        validate: String,
        seccode: String,
        challenge: String
    ): AppResult<Unit> = try {
        val keyResp: ApiResponse<WebKeyData> = client.authApi.webKey()
        val hash = keyResp.data?.hash
        val pem = keyResp.data?.key
        if (!keyResp.isSuccess || hash == null || pem.isNullOrBlank()) {
            AppResult.Failure(keyResp.code, keyResp.errorText)
        } else {
            val encrypted = runCatching { BiliRsa.encryptPassword(hash, password, pem) }
                .getOrElse { return AppResult.Failure(86000, "密码加密失败，请重试") }
            val resp: ApiResponse<LoginData> = client.authApi.passwordLogin(
                username = username,
                password = encrypted,
                token = captcha.token,
                challenge = challenge.ifBlank { captcha.challenge },
                validate = validate,
                seccode = seccode
            )
            if (resp.isSuccess) AppResult.Success(Unit)
            else AppResult.Failure(resp.code, loginErrorText(resp.code, resp.errorText))
        }
    } catch (t: Throwable) {
        AppResult.Error(t)
    }

    /** 发送短信验证码，成功后返回后续登录要用的 captcha_key */
    suspend fun sendSmsCode(
        tel: String,
        captcha: CaptchaChallenge,
        validate: String,
        seccode: String,
        challenge: String
    ): AppResult<String> = try {
        val resp: ApiResponse<SmsSendData> = client.authApi.smsSend(
            tel = tel,
            token = captcha.token,
            challenge = challenge.ifBlank { captcha.challenge },
            validate = validate,
            seccode = seccode
        )
        val key = resp.data?.captchaKey
        if (resp.isSuccess && !key.isNullOrBlank()) {
            AppResult.Success(key)
        } else {
            AppResult.Failure(resp.code, loginErrorText(resp.code, resp.errorText))
        }
    } catch (t: Throwable) {
        AppResult.Error(t)
    }

    /** 短信验证码登录。captcha_key 来自 sendSmsCode，过期就重新发一条 */
    suspend fun smsLogin(tel: String, code: String, captchaKey: String): AppResult<Unit> = try {
        val resp: ApiResponse<LoginData> = client.authApi.smsLogin(
            tel = tel,
            code = code,
            captchaKey = captchaKey
        )
        if (resp.isSuccess) AppResult.Success(Unit)
        else AppResult.Failure(resp.code, loginErrorText(resp.code, resp.errorText))
    } catch (t: Throwable) {
        AppResult.Error(t)
    }

    /**
     * 把 passport 的错误码翻成人话。
     * 码表来自 API-collect 的 password 登录页，实测 -629 / -105 两个高频。
     */
    private fun loginErrorText(code: Int, fallback: String): String = when (code) {
        -105 -> "人机验证没通过，请重新验证"
        -629 -> "账号或密码错误"
        -653 -> "账号和密码都要填"
        -662 -> "提交超时，请重试"
        -504 -> "验证码已失效，请重新获取"
        -2100 -> "该账号需要先完成手机号或邮箱验证，去网页版登录一次再回来"
        2400 -> "登录密钥已失效，请重试"
        2406 -> "极验服务出错，请重试"
        86000 -> "密码加密失败，请重试"
        86203 -> "验证码发得太频繁，等一会儿再试"
        else -> fallback
    }

    /**
     * 网页登录回填 Cookie。
     *
     * 内嵌登录页（WebView）拿到的 Cookie 只存在于 WebView 的 CookieManager，
     * OkHttp 这边完全看不到，所以要逐条搬进 SecureCookieStore，再调 nav 校验一次。
     * 只搬登录真正需要的几项，别把网页上的 _uuid、buvid3 之类覆盖掉（那些是接口层自己拿的指纹）。
     */
    fun putLoginCookie(name: String, value: String) {
        if (name !in LOGIN_COOKIE_NAMES) return
        if (value.isBlank()) return
        cookieStore.put(name, value)
    }

    private companion object {
        val LOGIN_COOKIE_NAMES = setOf(
            "SESSDATA",
            "bili_jct",
            "DedeUserID",
            "DedeUserID__ckMd5",
            "sid"
        )
    }
}

/** 二维码信息 */
data class QrCodeInfo(val url: String, val qrcodeKey: String)

/** 一组极验参数：token 用于接口鉴权，gt + challenge 用于初始化极验组件 */
data class CaptchaChallenge(
    val type: String,
    val token: String,
    val gt: String,
    val challenge: String
)

/** 扫码状态机 */
sealed interface QrPollState {
    data object Waiting : QrPollState
    data object Scanning : QrPollState
    data object Expired : QrPollState
    data object Success : QrPollState
}
