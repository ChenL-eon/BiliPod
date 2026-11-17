package com.bilipod.app.biliapi.model

import com.google.gson.annotations.SerializedName

/** 导航接口，既用来校验登录态，也是 wbi 密钥的来源 */
data class NavData(
    @SerializedName("isLogin") val isLogin: Boolean = false,
    @SerializedName("uname") val uname: String? = null,
    @SerializedName("mid") val mid: Long = 0L,
    @SerializedName("face") val face: String? = null,
    @SerializedName("wbi_img") val wbiImg: WbiImg? = null
)

data class WbiImg(
    @SerializedName("img_url") val imgUrl: String? = null,
    @SerializedName("sub_url") val subUrl: String? = null
)

/** 扫码登录：二维码生成结果 */
data class QrGenerateData(
    @SerializedName("url") val url: String? = null,
    @SerializedName("qrcode_key") val qrcodeKey: String? = null
)

/** 扫码登录：轮询结果，code 含义见 AuthRepository.parseQrState */
data class QrPollData(
    @SerializedName("url") val url: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("code") val code: Int = 0,
    @SerializedName("message") val message: String? = null
)

/** 设备指纹 buvid，未登录也需要，用于降低风控概率 */
data class BuvidData(
    @SerializedName("b_3") val b3: String? = null,
    @SerializedName("b_4") val b4: String? = null
)

/**
 * 极验参数。
 * 实测 2026-11：type 固定是 "geetest"（V3），geetest 里给 gt + challenge，
 * token 要和 validate / seccode 一起回传给登录接口，三者必须同源，不能串用。
 */
data class CaptchaData(
    @SerializedName("type") val type: String? = null,
    @SerializedName("token") val token: String? = null,
    @SerializedName("geetest") val geetest: GeetestData? = null
)

data class GeetestData(
    @SerializedName("gt") val gt: String? = null,
    @SerializedName("challenge") val challenge: String? = null
)

/** 密码加密公钥：hash 是服务端下发的前缀，key 是 PKCS8 PEM */
data class WebKeyData(
    @SerializedName("hash") val hash: String? = null,
    @SerializedName("key") val key: String? = null
)

/** 密码 / 短信登录成功后的返回体，真正的登录态在 Set-Cookie 里 */
data class LoginData(
    @SerializedName("status") val status: Int = 0,
    @SerializedName("url") val url: String? = null,
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null
)

/** 短信发送结果：captcha_key 是后续 login/sms 必须带上的凭据 */
data class SmsSendData(
    @SerializedName("captcha_key") val captchaKey: String? = null,
    @SerializedName("captcha_key_expire") val captchaKeyExpire: Int = 0
)
