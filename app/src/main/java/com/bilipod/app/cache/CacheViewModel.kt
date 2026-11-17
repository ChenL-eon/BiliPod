package com.bilipod.app.cache

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilipod.app.database.entity.CacheEntity
import com.bilipod.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CacheViewModel : ViewModel() {

    private val manager = ServiceLocator.cacheManager

    /** 实时下载状态 */
    val states: StateFlow<Map<String, CacheState>> = manager.states

    /** 缓存列表（Room 驱动，含失败/取消的残留记录） */
    val items: StateFlow<List<CacheEntity>> = manager.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _maxBytes = MutableStateFlow(manager.maxBytes)
    val maxBytes: StateFlow<Long> = _maxBytes.asStateFlow()

    fun setMaxBytes(value: Long) {
        manager.maxBytes = value
        _maxBytes.value = value
    }

    fun delete(episodeKey: String) {
        viewModelScope.launch { manager.delete(episodeKey) }
    }

    fun cancel(episodeKey: String) = manager.cancel(episodeKey)

    fun clearAll() {
        viewModelScope.launch { manager.clearAll() }
    }
}
