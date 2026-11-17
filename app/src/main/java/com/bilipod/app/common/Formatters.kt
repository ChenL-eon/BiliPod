package com.bilipod.app.common

import java.util.Locale
import java.util.concurrent.TimeUnit

/** 时间/时长格式化，只放纯函数，方便单测 */
object Formatters {

    // 全部固定 Locale.ROOT：设备切到阿拉伯语/印度语区时，默认 Locale 会把数字换成当地字形，
    // 时长和体积这种技术性数字不该跟着变，否则进度条会显示成看不懂的符号

    /** 秒 -> mm:ss 或 h:mm:ss */
    fun duration(seconds: Long): String {
        if (seconds <= 0) return "00:00"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%02d:%02d", m, s)
    }

    /** 毫秒 -> mm:ss，播放器进度用 */
    fun durationMs(ms: Long): String = duration(TimeUnit.MILLISECONDS.toSeconds(ms))

    /** 字节 -> 人类可读 */
    fun size(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format(Locale.ROOT, "%.1f GB", bytes / (1.0 * (1L shl 30)))
        bytes >= 1L shl 20 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1.0 * (1L shl 20)))
        bytes >= 1L shl 10 -> String.format(Locale.ROOT, "%.1f KB", bytes / (1.0 * (1L shl 10)))
        else -> "$bytes B"
    }

    /** 计数（粉丝/播放）-> 1.2万 / 496.9万，UP 主搜索结果用 */
    fun count(value: Int): String = when {
        value <= 0 -> "0"
        value >= 100_000_000 -> String.format(Locale.ROOT, "%.1f亿", value / 100_000_000.0)
        value >= 10_000 -> String.format(Locale.ROOT, "%.1f万", value / 10_000.0)
        else -> value.toString()
    }

    /** "12:34" 这种时长字符串转秒，空间/搜索接口返回的是这种格式 */
    fun parseClock(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val parts = text.split(":")
        return try {
            when (parts.size) {
                2 -> parts[0].toLong() * 60 + parts[1].toLong()
                3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
                else -> text.toLongOrNull() ?: 0L
            }
        } catch (_: NumberFormatException) {
            0L
        }
    }
}
