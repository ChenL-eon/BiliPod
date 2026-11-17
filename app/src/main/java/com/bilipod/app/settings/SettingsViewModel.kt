package com.bilipod.app.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bilipod.app.cache.CacheState
import com.bilipod.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页状态：缓存上限、默认倍速、本地历史清理。
 * 全部读写既有数据源（CacheManager / SettingsStore / PlayHistoryDao），不新建存储。
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val manager = ServiceLocator.cacheManager
    private val historyDao = ServiceLocator.database.historyDao()

    private val _maxBytes = MutableStateFlow(manager.maxBytes)
    val maxBytes: StateFlow<Long> = _maxBytes.asStateFlow()

    private val _defaultSpeed = MutableStateFlow(SettingsStore.defaultSpeed(app))
    val defaultSpeed: StateFlow<Float> = _defaultSpeed.asStateFlow()

    /** 与 B 站的同步开关：直接复用 SettingsStore 里的 StateFlow，首页那边也在监听同一份 */
    val syncHistory: StateFlow<Boolean> = SettingsStore.syncHistoryFlow(app)
    val syncFavorites: StateFlow<Boolean> = SettingsStore.syncFavoritesFlow(app)

    fun setSyncHistory(enabled: Boolean) {
        SettingsStore.setSyncHistory(getApplication(), enabled)
    }

    fun setSyncFavorites(enabled: Boolean) {
        SettingsStore.setSyncFavorites(getApplication(), enabled)
    }

    /** 当前缓存占用（只统计 SUCCESS 的文件长度），用于"上限 vs 已用"一行提示 */
    val cacheUsage: StateFlow<Long> = manager.observeAll()
        .map { list ->
            list.filter { it.status == CacheState.Status.SUCCESS.name }
                .sumOf { it.totalBytes }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val historyCount: StateFlow<Int> = historyDao.observeRecent(500)
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setMaxBytes(value: Long) {
        manager.maxBytes = value
        _maxBytes.value = value
    }

    fun setDefaultSpeed(value: Float) {
        SettingsStore.setDefaultSpeed(getApplication(), value)
        _defaultSpeed.value = value
    }

    fun clearLocalHistory() {
        viewModelScope.launch { historyDao.clear() }
    }
}
