package com.bilipod.app.player

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bilipod.app.cache.CacheState
import com.bilipod.app.database.entity.FavoriteEntity
import com.bilipod.app.di.ServiceLocator
import com.bilipod.app.repository.model.Episode
import com.bilipod.app.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 播放器 ViewModel：只做三件事
 * 1. 持有 PlayerConnection（真正播放交给 Service）；
 * 2. 睡眠定时；
 * 3. 收藏单集（Room）。
 *
 * 进度持久化在 Service 里做，因为 ViewModel 可能先死而播放还在继续。
 */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val connection = PlayerConnection(app)
    private val favoriteDao = ServiceLocator.database.favoriteDao()

    val uiState: StateFlow<PlayerUiState> = connection.state

    private val _sleepRemainingMs = MutableStateFlow<Long?>(null)
    val sleepRemainingMs: StateFlow<Long?> = _sleepRemainingMs.asStateFlow()

    private val _favorited = MutableStateFlow(false)
    val favorited: StateFlow<Boolean> = _favorited.asStateFlow()

    /** 当前这一集是否已经点过赞（B 站账号维度） */
    private val _liked = MutableStateFlow(false)
    val liked: StateFlow<Boolean> = _liked.asStateFlow()

    /** 当前这一集是否已经投过币 */
    private val _coined = MutableStateFlow(false)
    val coined: StateFlow<Boolean> = _coined.asStateFlow()

    /** 一次性提示（点赞结果、未登录等），UI 弹完就清 */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** 定时到本集结束：不按分钟算，靠播放进度判断 */
    private val _sleepAtEnd = MutableStateFlow(false)
    val sleepAtEnd: StateFlow<Boolean> = _sleepAtEnd.asStateFlow()

    /** 点赞状态缓存，避免每切一集都请求一次 view 接口 */
    private val likedCache = mutableMapOf<String, Boolean>()

    /** 投币枚数缓存，和 likedCache 同一次 view 请求一起填 */
    private val coinCache = mutableMapOf<String, Int>()

    private var sleepJob: Job? = null

    /**
     * 非空 = "只应用到当前节目"这一档正在生效，值是当时那一集的 key。
     * Media3 的倍速是 player 级的、不跟着 item 走，切集时得自己把它退回默认，
     * 否则"只应用到当前节目"会变成"只应用到本次播放会话"。
     */
    private var pinnedSpeedKey: String? = null

    /** 设置面板上"只应用到当前节目"开关的真实状态，面板直接绑这一份，不做局部副本 */
    private val _speedOnlyCurrent = MutableStateFlow(false)
    val speedOnlyCurrent: StateFlow<Boolean> = _speedOnlyCurrent.asStateFlow()

    /**
     * 队列在 App 被杀后由 PlayerService 恢复，这里同步读一份，
     * 让"当前单集"能立刻从队列里拿到 bvid/cid，收藏按钮不必只靠播放器快照。
     */
    private var queue: List<Episode> = QueueStore.load(app)?.items.orEmpty()

    private val _currentEpisode = MutableStateFlow<Episode?>(null)

    /** 当前这一集；播放页的"加入缓存 / 已缓存"按钮靠它取 key 与 bvid */
    val currentEpisode: StateFlow<Episode?> = _currentEpisode.asStateFlow()

    /** 内存态下载进度（播放页用；这里的 key 已是 cid 级，不需要 bvid 别名） */
    val cacheStates: StateFlow<Map<String, CacheState>> = ServiceLocator.cacheManager.states
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Room 里的落库状态，重启后仍能显示"已缓存 / 下载失败" */
    val cacheStatuses: StateFlow<Map<String, CacheState.Status>> =
        ServiceLocator.cacheManager.observeAll()
            .map { list ->
                list.associate { entity ->
                    entity.episodeKey to runCatching {
                        CacheState.Status.valueOf(entity.status)
                    }.getOrDefault(CacheState.Status.IDLE)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        connection.connect()
        // 连接建立后应用偏好里的默认倍速（连接前 controller 为 null，setSpeed 会静默失败）
        viewModelScope.launch {
            uiState.map { it.connected }.distinctUntilChanged().collect { connected ->
                if (connected) connection.setSpeed(SettingsStore.defaultSpeed(app))
            }
        }
        // 切集后同步"当前单集"，收藏按钮与缓存按钮状态跟着变
        viewModelScope.launch {
            uiState.map { it.currentKey }.distinctUntilChanged().collect { key ->
                // 上一集用过"只应用到当前节目"，换集就把倍速退回默认
                pinnedSpeedKey?.let { pinned ->
                    if (pinned != key) {
                        pinnedSpeedKey = null
                        _speedOnlyCurrent.value = false
                        connection.setSpeed(SettingsStore.defaultSpeed(app))
                    }
                }
                _currentEpisode.value = queue.firstOrNull { it.key == key }
                    // 进程重启后 ViewModel 是新的、队列可能还没恢复，但播放服务还在跑；
                    // 这时用播放器快照兜底，否则收藏/缓存按钮点了没反应
                    ?: key?.takeIf { it.isNotBlank() && !it.startsWith("a_") }?.let { k ->
                        val snapshot = uiState.value
                        Episode(
                            key = k,
                            title = snapshot.title.orEmpty(),
                            bvid = Episode.bvidOfKey(k),
                            cid = k.removePrefix("v_").substringAfter('_', "").toLongOrNull(),
                            cover = snapshot.cover,
                            upName = snapshot.upName
                        )
                    }
                refreshFavorite()
                refreshLiked(_currentEpisode.value?.bvid)
            }
        }
        // 定时到本集结束：位置进入最后 1.5 秒就暂停。
        // ticker 是 500ms 一跳，这个窗口足够在内置播放器自动切下一集之前拦住。
        viewModelScope.launch {
            uiState.collect { s ->
                if (_sleepAtEnd.value && s.isPlaying && s.durationMs > 0L &&
                    s.positionMs >= s.durationMs - 1_500L
                ) {
                    _sleepAtEnd.value = false
                    connection.pause()
                    notify("已到本集结尾，自动暂停")
                }
            }
        }
    }

    /**
     * 播放一个列表。
     * @param startPositionMs 断点续播位置，仅对 startIndex 指向的那一集生效
     */
    fun play(episodes: List<Episode>, startIndex: Int, startPositionMs: Long = 0L) {
        queue = episodes
        _currentEpisode.value = episodes.getOrNull(startIndex)
        connection.play(episodes, startIndex, startPositionMs)
        viewModelScope.launch { refreshFavorite() }
    }

    fun toggle() = connection.toggle()

    fun seekTo(positionMs: Long) = connection.seekTo(positionMs)

    fun setSpeed(speed: Float) = connection.setSpeed(speed)

    fun next() = connection.next()

    fun previous() = connection.previous()

    fun retry() = connection.retry()

    /** minutes 为 null 或 0 表示取消定时 */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        sleepJob = null
        if (minutes == null || minutes <= 0) {
            _sleepRemainingMs.value = null
            return
        }
        // 两种定时互斥：设了分钟数就把"到本集结束"关掉
        _sleepAtEnd.value = false
        sleepJob = viewModelScope.launch {
            var remaining = minutes.toLong() * 60_000L
            while (remaining > 0L) {
                _sleepRemainingMs.value = remaining
                delay(1_000L)
                remaining -= 1_000L
            }
            _sleepRemainingMs.value = null
            connection.pause()
        }
    }

    /** "定时到本集结束"：和分钟定时互斥 */
    fun setSleepAtEnd(on: Boolean) {
        _sleepAtEnd.value = on
        if (on) {
            sleepJob?.cancel()
            sleepJob = null
            _sleepRemainingMs.value = null
            notify("本集播完就暂停")
        }
    }

    /**
     * 面板里改倍速。
     * onlyCurrent 对应设置面板上的"只应用到当前节目"：
     * 开着就只改这一次播放，关掉同时写进默认倍速，下次开播还是这个速度。
     */
    fun setSpeedFromPanel(speed: Float) {
        connection.setSpeed(speed)
        val onlyCurrent = _speedOnlyCurrent.value
        pinnedSpeedKey = if (onlyCurrent) _currentEpisode.value?.key else null
        if (!onlyCurrent) SettingsStore.setDefaultSpeed(getApplication(), speed)
    }

    /** 面板开关：只改标记，不改当前速度。开关关掉后再选刻度才会把倍速写进默认 */
    fun setSpeedOnlyCurrent(on: Boolean) {
        _speedOnlyCurrent.value = on
        if (!on) pinnedSpeedKey = null
    }

    /** 点赞 / 取消点赞当前这一集（走 B 站真实接口，不是本地假状态） */
    fun toggleLike() {
        val bvid = _currentEpisode.value?.bvid
        if (bvid.isNullOrBlank()) {
            notify("这一集拿不到稿件号，点不了赞")
            return
        }
        val csrf = ServiceLocator.cookieStore.value("bili_jct")
        // 光看 csrf 不够：退出登录后 bili_jct 不一定被清，但 SESSDATA 已经过期，
        // 这时候请求出去只会拿到 -101，提示成"网络异常"就误导人了。
        if (csrf.isNullOrBlank() || !ServiceLocator.cookieStore.isLoggedIn()) {
            notify("登录 B 站账号后才能点赞")
            return
        }
        val target = !_liked.value
        viewModelScope.launch {
            val resp = runCatching {
                ServiceLocator.apiClient.api.likeVideo(
                    bvid = bvid,
                    like = if (target) 1 else 2,
                    csrf = csrf
                )
            }.onFailure {
                Log.w(TAG, "点赞请求失败 bvid=$bvid", it)
            }.getOrNull()
            when {
                resp == null -> notify("点赞失败：网络异常")
                resp.isSuccess -> {
                    likedCache[bvid] = target
                    _liked.value = target
                    notify(if (target) "已点赞" else "已取消点赞")
                }
                // -101 是没登录 / 登录态失效，-111 是 csrf 不对，两种都得重新登录
                resp.code == -101 || resp.code == -111 -> notify("登录已失效，请重新登录")
                else -> notify("点赞失败：${resp.errorText}")
            }
        }
    }

    /**
     * 查当前这一集的"我点过赞没有 / 投过币没有"。
     * view 接口的 req_user 一次给全，所以只查一次并缓存；未登录时这一段是空的，直接当没点没投。
     */
    private fun refreshLiked(bvid: String?) {
        if (bvid.isNullOrBlank()) {
            _liked.value = false
            _coined.value = false
            return
        }
        likedCache[bvid]?.let {
            _liked.value = it
            _coined.value = (coinCache[bvid] ?: 0) > 0
            return
        }
        if (ServiceLocator.cookieStore.value("bili_jct").isNullOrBlank() ||
            !ServiceLocator.cookieStore.isLoggedIn()
        ) {
            _liked.value = false
            _coined.value = false
            return
        }
        viewModelScope.launch {
            val data = runCatching { ServiceLocator.apiClient.api.videoDetail(bvid) }
                .getOrNull()?.data
            val reqUser = data?.reqUser
            val liked = (reqUser?.like ?: 0) > 0
            val coins = reqUser?.coin ?: 0
            if (data != null) {
                likedCache[bvid] = liked
                coinCache[bvid] = coins
            }
            _liked.value = liked
            _coined.value = coins > 0
        }
    }

    /** 投币：一集最多两枚，投过就不再重复请求（B 站会直接返回 -103） */
    fun coin(count: Int) {
        val bvid = _currentEpisode.value?.bvid
        if (bvid.isNullOrBlank()) {
            notify("这一集拿不到稿件号，投不了币")
            return
        }
        val csrf = ServiceLocator.cookieStore.value("bili_jct")
        if (csrf.isNullOrBlank() || !ServiceLocator.cookieStore.isLoggedIn()) {
            notify("登录 B 站账号后才能投币")
            return
        }
        if (_coined.value) {
            notify("这一集已经投过币了")
            return
        }
        viewModelScope.launch {
            val resp = runCatching {
                ServiceLocator.apiClient.api.coinAdd(
                    bvid = bvid,
                    multiply = count.coerceIn(1, 2),
                    csrf = csrf
                )
            }.onFailure {
                Log.w(TAG, "投币请求失败 bvid=$bvid", it)
            }.getOrNull()
            when {
                resp == null -> notify("投币失败：网络异常")
                resp.isSuccess -> {
                    coinCache[bvid] = count
                    _coined.value = true
                    notify("已投 $count 枚硬币")
                }
                resp.code == -101 || resp.code == -111 -> notify("登录已失效，请重新登录")
                resp.code == -104 -> notify("硬币不足，投币失败")
                resp.code == -103 -> notify("这一集已经投过币了")
                else -> notify("投币失败：${resp.errorText}")
            }
        }
    }

    private fun notify(message: String) {
        _notice.value = message
        viewModelScope.launch {
            delay(2_500L)
            if (_notice.value == message) _notice.value = null
        }
    }

    /**
     * 收藏 / 取消收藏当前这一集。
     *
     * key 统一策略（2026-11-16 修）：写入时尽量落到 cid 级的 v_{bvid}_{cid}，
     * cid 未知（搜索结果直接播放）就先走一次 view 接口补齐；
     * 判断"是否已收藏"时先查精确 key，查不到再按 bvid 回退，
     * 这样从收藏夹（bvid 级）点进播放页也不会显示空心。
     */
    fun toggleFavorite() {
        val episode = _currentEpisode.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val existing = favoriteDao.findKey(episode.key)
                    ?: episode.bvid?.let { favoriteDao.findKeyByBvid(it) }
                if (existing != null) {
                    favoriteDao.delete(existing)
                    return@withContext
                }
                val realCid = episode.cid?.takeIf { it > 0L }
                    ?: episode.bvid?.let {
                        runCatching { ServiceLocator.playUrlRepository.firstCidOf(it) }.getOrNull()
                    }
                val bvid = episode.bvid
                val key = if (bvid != null && realCid != null && realCid > 0L) {
                    Episode.videoKey(bvid, realCid)
                } else {
                    episode.key
                }
                favoriteDao.insert(
                    FavoriteEntity(
                        episodeKey = key,
                        title = episode.title,
                        cover = episode.cover,
                        upName = episode.upName,
                        bvid = bvid,
                        cid = realCid ?: episode.cid,
                        sid = episode.sid,
                        durationSec = episode.durationSec,
                        addedAt = System.currentTimeMillis()
                    )
                )
            }
            refreshFavorite()
        }
    }

    private suspend fun refreshFavorite() {
        val episode = _currentEpisode.value
        _favorited.value = if (episode == null) {
            false
        } else withContext(Dispatchers.IO) {
            favoriteDao.findKey(episode.key) != null ||
                episode.bvid?.let { favoriteDao.findKeyByBvid(it) != null } == true
        }
    }

    /** 播放页"加入缓存"：复用同一个 CacheManager 队列，不新建下载逻辑 */
    fun cacheCurrent() {
        _currentEpisode.value?.let { ServiceLocator.cacheManager.enqueue(it) }
    }

    /** 播放页取消当前这一集的下载（CacheManager 会清 .part 并写 CANCELED） */
    fun cancelCache(episodeKey: String) = ServiceLocator.cacheManager.cancel(episodeKey)

    override fun onCleared() {
        connection.release()
        super.onCleared()
    }

    private companion object {
        const val TAG = "PlayerViewModel"
    }
}
