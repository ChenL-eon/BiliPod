package com.bilipod.app.biliapi

import com.bilipod.app.biliapi.model.CaptchaData
import com.bilipod.app.biliapi.model.LoginData
import com.bilipod.app.biliapi.model.QrGenerateData
import com.bilipod.app.biliapi.model.QrPollData
import com.bilipod.app.biliapi.model.SmsSendData
import com.bilipod.app.biliapi.model.WebKeyData
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * passport 登录相关接口，域名与主站不同，单独一套 Retrofit。
 *
 * 扫码、密码、短信三条路都在这里：
 * - 扫码：generate / poll；
 * - 密码：captcha（拿极验 gt+challenge）→ 本地极验 → web/key（拿 RSA 公钥）→ login；
 * - 短信：captcha → 本地极验 → sms/send 拿 captcha_key → login/sms。
 *
 * 风险点：密码和短信都必须先过极验，token / challenge / validate / seccode 四个参数缺一不可；
 * 扫码轮询有频率限制，必须按 Constants.QR_POLL_INTERVAL_MS 节奏来，不做并发轮询。
 */
interface AuthApiService {

    @GET("x/passport-login/web/qrcode/generate")
    suspend fun qrGenerate(): ApiResponse<QrGenerateData>

    @GET("x/passport-login/web/qrcode/poll")
    suspend fun qrPoll(@Query("qrcode_key") qrcodeKey: String): ApiResponse<QrPollData>

    /** 极验参数，每次操作前现取：challenge 有时效，不能缓存复用 */
    @GET("x/passport-login/captcha")
    suspend fun captcha(@Query("source") source: String = "main_web"): ApiResponse<CaptchaData>

    /** 密码加密用的公钥，hash 与 key 成对出现，同样有时效 */
    @GET("x/passport-login/web/key")
    suspend fun webKey(): ApiResponse<WebKeyData>

    @FormUrlEncoded
    @POST("x/passport-login/web/login")
    suspend fun passwordLogin(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("keep") keep: Int = 0,
        @Field("token") token: String,
        @Field("challenge") challenge: String,
        @Field("validate") validate: String,
        @Field("seccode") seccode: String,
        @Field("go_url") goUrl: String = "https://www.bilibili.com",
        @Field("source") source: String = "main_web"
    ): ApiResponse<LoginData>

    @FormUrlEncoded
    @POST("x/passport-login/web/sms/send")
    suspend fun smsSend(
        @Field("cid") cid: Int = 86,
        @Field("tel") tel: String,
        @Field("source") source: String = "main_web",
        @Field("token") token: String,
        @Field("challenge") challenge: String,
        @Field("validate") validate: String,
        @Field("seccode") seccode: String
    ): ApiResponse<SmsSendData>

    @FormUrlEncoded
    @POST("x/passport-login/web/login/sms")
    suspend fun smsLogin(
        @Field("cid") cid: Int = 86,
        @Field("tel") tel: String,
        @Field("code") code: String,
        @Field("captcha_key") captchaKey: String,
        @Field("source") source: String = "main_web",
        @Field("keep") keep: Int = 0
    ): ApiResponse<LoginData>
}
