package com.bilipod.app

import android.app.Application
import com.bilipod.app.di.ServiceLocator

class BiliPodApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 依赖容器初始化必须早于任何 ViewModel / Service
        ServiceLocator.init(this)
        // 预热缓存模块：CacheManager 是 lazy 的，不主动取一次就不会实例化，
        // 那样进程重启后没人触发"僵尸下载状态"自愈，列表会一直停在"下载中"。
        ServiceLocator.warmUpCache()
    }
}
