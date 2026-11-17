package com.bilipod.app.cache

/**
 * 缓存状态机：
 * WAITING（排队）→ DOWNLOADING（下载中）→ SUCCESS / FAILED，任意时刻可 CANCELED。
 * 状态值同时会以字符串形式落库（CacheEntity.status），重启后可恢复列表。
 */
data class CacheState(
    val status: Status = Status.IDLE,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val message: String? = null
) {
    enum class Status { IDLE, WAITING, DOWNLOADING, SUCCESS, FAILED, CANCELED }

    val progress: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val isRunning: Boolean
        get() = status == Status.WAITING || status == Status.DOWNLOADING
}
