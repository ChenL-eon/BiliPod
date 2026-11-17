package com.bilipod.app.player

import android.content.Context
import com.bilipod.app.repository.model.Episode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** 恢复出来的队列：条目 + 上次播到第几集 */
data class SavedQueue(val items: List<Episode>, val index: Int)

/**
 * 播放队列的轻量持久化。
 *
 * 为什么不进 Room：队列只是"上次播放的上下文"，丢了可以重来，不值得为它建表、改 schema
 * （`AppDatabase` 是 version=1 + 破坏式迁移，一动表结构真机缓存就被清空）。
 * 为什么不用 DataStore：工程没引这个依赖，SharedPreferences + Gson 足够。
 *
 * 写入策略：
 * - 换队列（play）时写全量 items + index；
 * - 切集时只改 index，避免大合集（910 集）每次切集都重写几十上百 KB 的 JSON。
 */
object QueueStore {

    private const val PREF_NAME = "player_queue"
    private const val KEY_ITEMS = "items_json"
    private const val KEY_INDEX = "index"

    private val gson = Gson()
    private val listType = object : TypeToken<List<Episode>>() {}.type

    fun save(context: Context, items: List<Episode>, index: Int) {
        if (items.isEmpty()) return
        runCatching {
            pref(context).edit()
                .putString(KEY_ITEMS, gson.toJson(items, listType))
                .putInt(KEY_INDEX, index.coerceIn(0, items.lastIndex))
                .apply()
        }
    }

    fun saveIndex(context: Context, index: Int) {
        if (index < 0) return
        runCatching { pref(context).edit().putInt(KEY_INDEX, index).apply() }
    }

    fun load(context: Context): SavedQueue? {
        val raw = runCatching { pref(context).getString(KEY_ITEMS, null) }.getOrNull()
            ?: return null
        val items = runCatching { gson.fromJson<List<Episode>>(raw, listType) }.getOrNull()
            ?: return null
        if (items.isEmpty()) return null
        val index = runCatching { pref(context).getInt(KEY_INDEX, 0) }.getOrDefault(0)
        return SavedQueue(items, index.coerceIn(0, items.lastIndex))
    }

    fun clear(context: Context) {
        runCatching { pref(context).edit().clear().apply() }
    }

    private fun pref(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
