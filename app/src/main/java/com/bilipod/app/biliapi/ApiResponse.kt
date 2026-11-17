package com.bilipod.app.biliapi

import com.google.gson.annotations.SerializedName

/**
 * B 站接口统一响应体。
 * code == 0 表示成功；常见错误码：
 *  -101 未登录 / 账号未实名，-403 权限不足，-352 风控校验失败，-412 请求被拦截，
 *  -400 请求参数错误，-404 资源不存在。
 */
data class ApiResponse<T>(
    @SerializedName("code") val code: Int = -1,
    @SerializedName(value = "message", alternate = ["msg"]) val message: String? = null,
    @SerializedName("data") val data: T? = null
) {
    val isSuccess: Boolean get() = code == 0
    val errorText: String get() = message?.takeIf { it.isNotBlank() } ?: "接口错误码 $code"
}
