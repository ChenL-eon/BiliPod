package com.bilipod.app.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 轻量的应用偏好。
 *
 * 只放"跨进程可读、和业务状态无关"的开关/默认值；
 * 缓存上限与队列另有归属（CacheManager / QueueStore），不要在这里重复一份，
 * 否则两处会各写各的、读到的值互相打架。
 *
 * 同步开关额外挂了一份内存 StateFlow：设置页改完，首页的 ViewModel 要立刻跟着变，
 * 光靠 SharedPreferences 得等下次重建才生效。
 */
object SettingsStore {

    private const val PREF_NAME = "app_settings"
    private const val KEY_DEFAULT_SPEED = "default_speed"
    private const val KEY_SYNC_HISTORY = "sync_history"
    private const val KEY_SYNC_FAVORITES = "sync_favorites"

    const val SPEED_MIN = 0.5f
    const val SPEED_MAX = 3.0f

    /** 默认播放倍速；第一次进设置页之前就是 1.0 */
    fun defaultSpeed(context: Context): Float {
        val speed = prefs(context).getFloat(KEY_DEFAULT_SPEED, 1f)
        return speed.takeIf { it in SPEED_MIN..SPEED_MAX } ?: 1f
    }

    fun setDefaultSpeed(context: Context, speed: Float) {
        prefs(context).edit().putFloat(KEY_DEFAULT_SPEED, speed.coerceIn(SPEED_MIN, SPEED_MAX)).apply()
    }

    // ---------------- 收听历史 / 我的收藏 是否同步 B 站 ----------------

    private val _syncHistory = MutableStateFlow(true)
    private val _syncFavorites = MutableStateFlow(true)
    private var loaded = false

    /**
     * 懒加载：第一次有人要这个开关时才读盘。
     * 默认 true（和之前"本机 + B 站"的行为一致，不能让老用户升级后数据凭空消失）。
     */
    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val p = prefs(context)
            _syncHistory.value = p.getBoolean(KEY_SYNC_HISTORY, true)
            _syncFavorites.value = p.getBoolean(KEY_SYNC_FAVORITES, true)
            loaded = true
        }
    }

    fun syncHistoryFlow(context: Context): StateFlow<Boolean> {
        ensureLoaded(context)
        return _syncHistory.asStateFlow()
    }

    fun syncFavoritesFlow(context: Context): StateFlow<Boolean> {
        ensureLoaded(context)
        return _syncFavorites.asStateFlow()
    }

    fun setSyncHistory(context: Context, enabled: Boolean) {
        ensureLoaded(context)
        prefs(context).edit().putBoolean(KEY_SYNC_HISTORY, enabled).apply()
        _syncHistory.value = enabled
    }

    fun setSyncFavorites(context: Context, enabled: Boolean) {
        ensureLoaded(context)
        prefs(context).edit().putBoolean(KEY_SYNC_FAVORITES, enabled).apply()
        _syncFavorites.value = enabled
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
