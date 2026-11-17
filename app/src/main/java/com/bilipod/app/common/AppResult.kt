package com.bilipod.app.common

/** 统一的业务结果包装，避免各层各自 try/catch 后返回风格不一致 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>

    /** 接口返回了业务错误码（如 -352 风控、-101 未登录） */
    data class Failure(val code: Int, val message: String) : AppResult<Nothing>

    /** 网络层异常、解析异常等 */
    data class Error(val throwable: Throwable) : AppResult<Nothing>
}

inline fun <T> runApiCatching(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (t: Throwable) {
    AppResult.Error(t)
}

@Suppress("UNCHECKED_CAST")
fun <T> AppResult<T>.dataOrNull(): T? = (this as? AppResult.Success<T>)?.data
