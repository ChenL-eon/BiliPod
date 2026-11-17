package com.bilipod.app.di

import android.content.Context
import androidx.room.Room
import com.bilipod.app.biliapi.BiliApiClient
import com.bilipod.app.cache.CacheManager
import com.bilipod.app.common.SecureCookieStore
import com.bilipod.app.database.AppDatabase
import com.bilipod.app.repository.AuthRepository
import com.bilipod.app.repository.ContentRepository
import com.bilipod.app.repository.PlayUrlRepository

/**
 * 手动依赖注入（不引入 Hilt）。
 * 生命周期与应用一致，全部 lazy，避免 Application.onCreate 里做重活。
 * 必须在 Application.onCreate 里先调用 init()。
 */
object ServiceLocator {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** 给需要读 SharedPreferences 的 ViewModel 用，避免各自再传一遍 Application */
    val context: Context get() = appContext

    val cookieStore: SecureCookieStore by lazy { SecureCookieStore(appContext) }

    val apiClient: BiliApiClient by lazy { BiliApiClient(cookieStore) }

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "bilipod.db")
            // 学习项目、库版本只有 1，遇到 schema 变更直接重建，不做迁移脚本
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    val authRepository: AuthRepository by lazy { AuthRepository(apiClient, cookieStore) }

    val contentRepository: ContentRepository by lazy { ContentRepository(apiClient) }

    val playUrlRepository: PlayUrlRepository by lazy { PlayUrlRepository(apiClient, cookieStore) }

    val cacheManager: CacheManager by lazy {
        CacheManager(
            context = appContext,
            apiClient = apiClient,
            playUrlRepository = playUrlRepository,
            cacheDao = database.cacheDao(),
            // 历史表也要做一次 key 迁移：旧记录用 v_{bvid}，不迁的话断点续播会失效
            historyDao = database.historyDao()
        )
    }

    /**
     * 预热缓存模块。
     * CacheManager 是 lazy 的，如果启动时不取一次，进程重启后不会有任何代码触发
     * 僵尸状态自愈（DB 里残留的 WAITING/DOWNLOADING 会永远卡在"下载中"）。
     * 这里只是触发实例化，真正的恢复逻辑在 CacheManager.init 里异步跑。
     */
    fun warmUpCache() {
        cacheManager
    }
}
