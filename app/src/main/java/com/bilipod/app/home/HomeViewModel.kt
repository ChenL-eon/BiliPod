package com.bilipod.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilipod.app.cache.CacheState
import com.bilipod.app.common.AppResult
import com.bilipod.app.di.ServiceLocator
import com.bilipod.app.repository.DiscoveryItem
import com.bilipod.app.repository.PageResult
import com.bilipod.app.repository.model.Episode
import com.bilipod.app.repository.model.FavFolder
import com.bilipod.app.repository.model.PodcastDetail
import com.bilipod.app.repository.model.toEpisode
import com.bilipod.app.repository.model.UserHit
import com.bilipod.app.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 本地历史条目，带断点位置 */
data class LocalHistoryItem(
    val episode: Episode,
    val positionMs: Long,
    val playedAt: Long
)

/**
 * 首页"发现"流状态。
 * 独立于 HomeUiState：发现流是浏览型列表（热门榜），和本地历史/收藏/搜索那几套分页
 * 互不相干，塞进同一个 state 只会让 loading / error 互相覆盖。
 */
data class DiscoveryState(
    val items: List<DiscoveryItem> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: String? = null,
    /** 0 = 推荐（个性化 rcmd 流）；>0 = B 站分区 rid，发现页的分类 chips 用 */
    val rid: Int = 0,
    val label: String = "推荐",
    /**
     * 推荐流降级成了热门榜。
     * rcmd 偶发被风控/签名过期挡掉时退回热门榜，翻页也得跟着走 popular 的 pn，
     * 不能再按 fresh_idx 递增，所以这个标记必须跟着状态走。
     */
    val usePopularFallback: Boolean = false
)

/**
 * UP 主主页状态。
 * 只保留"已加载的投稿 + 分页游标"，不做缓存，退出即丢；
 * space 接口不返回总数，用"本页是否拉满"推断还有没有下一页。
 */
data class UpSpace(
    val mid: Long,
    val name: String,
    val videos: List<Episode> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: String? = null
)

data class HomeUiState(
    val loggedIn: Boolean = false,
    val loading: Boolean = false,
    val userName: String? = null,
    val avatar: String? = null,
    val error: String? = null,
    val remoteHistory: List<Episode> = emptyList(),
    val favFolders: List<FavFolder> = emptyList(),
    /** 这两个开关来自设置页：关掉就只读本机，不碰 B 站账号数据 */
    val syncHistory: Boolean = true,
    val syncFavorites: Boolean = true,
    val selectedFolderId: Long? = null,
    val folderEpisodes: List<Episode> = emptyList(),
    val folderLoading: Boolean = false,
    /** 收藏夹内容拉取失败；和"夹子本来就是空的"分开，避免两种提示叠在一起 */
    val folderFailed: Boolean = false,
    val searchResults: List<Episode> = emptyList(),
    val searching: Boolean = false,
    val searched: Boolean = false,
    /**
     * 分页状态：搜索 / 收藏夹 / B站历史各一套，互不覆盖。
     * 只在翻页时用 loadingMore，不能复用 searching，否则列表会整屏切成骨架屏。
     */
    val searchPage: Int = 1,
    val searchHasMore: Boolean = false,
    val searchLoadingMore: Boolean = false,
    val folderPage: Int = 1,
    val folderHasMore: Boolean = false,
    val folderLoadingMore: Boolean = false,
    val historyHasMore: Boolean = false,
    val historyLoadingMore: Boolean = false,
    /** UP 主搜索结果与稿件结果分开存，两个档位互不覆盖 */
    val userResults: List<UserHit> = emptyList(),
    val searchingUsers: Boolean = false,
    val searchedUsers: Boolean = false,
    val detail: PodcastDetail? = null,
    val detailLoading: Boolean = false,
    val space: UpSpace? = null
)

/**
 * 首页 ViewModel。
 * 本地历史/收藏来自 Room（StateFlow 直接驱动 UI），远端数据走 ContentRepository。
 */
class HomeViewModel : ViewModel() {

    private val auth = ServiceLocator.authRepository
    private val content = ServiceLocator.contentRepository
    private val historyDao = ServiceLocator.database.historyDao()
    private val favoriteDao = ServiceLocator.database.favoriteDao()

    private val _state = MutableStateFlow(HomeUiState(loggedIn = auth.isLoggedIn()))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** 本地播放历史（含断点位置） */
    val localHistory: StateFlow<List<LocalHistoryItem>> = historyDao.observeRecent(50)
        .map { list ->
            list.map { LocalHistoryItem(it.toEpisode(), it.positionMs, it.playedAt) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 本地收藏的单集 */
    val localFavorites: StateFlow<List<Episode>> = favoriteDao.observeAll()
        .map { list -> list.map { it.toEpisode() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 实时下载状态：供列表按钮显示环形进度、取消、重试。
     * 直接复用 CacheManager 的 StateFlow，不新建队列。
     * 附带 v_{bvid} 别名，让 cid 未知的搜索/收藏卡片也能看到进度。
     */
    val cacheStates: StateFlow<Map<String, CacheState>> =
        ServiceLocator.cacheManager.states
            .map { it.withBvidAliases() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * 持久化的缓存状态（来自 Room），用于 App 重启后仍能显示"已缓存 / 下载失败"。
     * 内存态 states 在重启后是空的，光靠它列表会全部退回"可下载"。
     */
    val cacheStatuses: StateFlow<Map<String, CacheState.Status>> =
        ServiceLocator.cacheManager.observeAll()
            .map { list ->
                list.associate { entity ->
                    entity.episodeKey to runCatching {
                        CacheState.Status.valueOf(entity.status)
                    }.getOrDefault(CacheState.Status.IDLE)
                }.withBvidAliases()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private var mid: Long = 0L

    init {
        // 同步开关：关掉立刻把远端那一半清空，用户看到的就是"只剩本机"，
        // 不用等下次刷新；打开则只置标记，真正的拉取交给随后的 refresh()。
        val ctx = ServiceLocator.context
        viewModelScope.launch {
            SettingsStore.syncHistoryFlow(ctx).collect { enabled ->
                val prev = _state.value.syncHistory
                _state.update {
                    it.copy(
                        syncHistory = enabled,
                        remoteHistory = if (enabled) it.remoteHistory else emptyList(),
                        historyHasMore = enabled && it.historyHasMore
                    )
                }
                // 不是初值（init 里 refresh() 已经拉过一次），说明是用户刚打开的开关，
                // 这时候主动补一次，否则得等下次启动才看得到 B 站历史
                if (enabled && !prev && auth.isLoggedIn()) loadRemoteHistory()
            }
        }
        viewModelScope.launch {
            SettingsStore.syncFavoritesFlow(ctx).collect { enabled ->
                val prev = _state.value.syncFavorites
                _state.update {
                    it.copy(
                        syncFavorites = enabled,
                        favFolders = if (enabled) it.favFolders else emptyList(),
                        selectedFolderId = if (enabled) it.selectedFolderId else null,
                        folderEpisodes = if (enabled) it.folderEpisodes else emptyList(),
                        folderFailed = false
                    )
                }
                if (enabled && !prev && auth.isLoggedIn()) {
                    // 收藏夹接口要 mid：还没拿到就先走一次完整刷新
                    if (mid > 0L) loadFavFolders() else refresh()
                }
            }
        }
    }

    /** 发现流：热门榜，独立分页游标，和四个 tab 的分页互不干扰 */
    private val _discovery = MutableStateFlow(DiscoveryState())
    val discovery: StateFlow<DiscoveryState> = _discovery.asStateFlow()
    private var discoveryRequestId = 0

    /**
     * 推荐流的批次号（rcmd 的 fresh_idx）。
     * 每成功拿一批就 +1：刷新换新内容、翻页也是靠它推进，
     * 和热门榜的 pn 不是一回事，所以单独一个变量，别混进 DiscoveryState.page。
     */
    private var discoveryFreshIdx = 1

    /**
     * 请求序号。
     * 收藏夹可能 30 秒超时后重试、UP 主页可能 412 退避重试，慢请求回来时用户往往已经切到别处，
     * 落地前比对序号，避免"点 B 夹子却显示了 A 夹子的内容"。
     */
    private var folderRequestId = 0
    private var detailRequestId = 0
    private var searchRequestId = 0
    private var userSearchRequestId = 0
    private var spaceRequestId = 0
    private var historyRequestId = 0

    /** 翻页要原样带上上一次的关键字，用户改词重搜时靠 searchRequestId 丢弃旧页 */
    private var lastQuery: String? = null

    /** B 站历史游标，由上一页的 cursor 推进；max 为 0 表示到底 */
    private var historyMax: Long = 0L
    private var historyViewAt: Long = 0L

    init {
        refresh()
        refreshDiscovery()
    }

    /**
     * 刷新发现流。
     * "推荐"档拉热门第一页；分类档就重拉该分区排行（分区榜一次整榜、不分页）。
     * 未登录也能取，但先确保有 buvid3，否则热门接口容易被 412 挡。
     *
     * loading 一律置 true：列表已经有内容时它同时充当"下拉刷新转圈"的开关
     * （骨架屏那侧的条件是 loading && items.isEmpty()，所以不会整屏闪骨架）。
     */
    fun refreshDiscovery() {
        val requestId = ++discoveryRequestId
        // 下拉刷新 / 点底栏"发现"都走这里，打一行 INFO 方便用
        // adb logcat -s HomeViewModel:I 确认手势和点击真的到了 ViewModel
        android.util.Log.i("HomeViewModel", "refreshDiscovery rid=${_discovery.value.rid}")
        _discovery.update {
            it.copy(loading = true, page = 1, error = null)
        }
        fetchDiscovery(requestId)
    }

    /**
     * 发现页切分类。
     * 直接换掉整个状态（而不是只改 rid），否则旧榜的条目会在新榜回来前闪一下。
     * 同一个分类重复点不重复拉，省一次请求。
     */
    fun selectCategory(rid: Int, label: String) {
        val current = _discovery.value
        if (current.rid == rid && current.items.isNotEmpty()) return
        val requestId = ++discoveryRequestId
        _discovery.update { DiscoveryState(loading = true, rid = rid, label = label) }
        fetchDiscovery(requestId)
    }

    private fun fetchDiscovery(requestId: Int) {
        val rid = _discovery.value.rid
        viewModelScope.launch {
            auth.ensureBuvid()
            if (rid == 0) {
                // rcmd 是无限流，靠 fresh_idx 换批次；而且播客化过滤（>=10 分钟、无视觉分区）
                // 会把一批 12 条砍到两三条，所以这里连续拉几批凑一页，别让首屏只剩一条。
                var idx = discoveryFreshIdx
                var batches = 0
                var failure: AppResult<PageResult<DiscoveryItem>>? = null
                val collected = ArrayList<DiscoveryItem>()
                val seen = HashSet<String>()
                while (batches < RCMD_BATCHES_PER_PAGE && collected.size < RCMD_PAGE_TARGET) {
                    when (val result = content.recommend(idx)) {
                        is AppResult.Success -> {
                            val fresh = result.data.items.filter { seen.add(it.episode.key) }
                            if (fresh.isEmpty()) break
                            collected.addAll(fresh)
                            idx++
                            batches++
                        }
                        else -> {
                            failure = result
                            break
                        }
                    }
                }

                when {
                    collected.isEmpty() && failure != null -> {
                        when (failure) {
                            is AppResult.Failure -> {
                                android.util.Log.i(
                                    "HomeViewModel",
                                    "rcmd 失败 ${failure.code} ${failure.message}，降级热门榜"
                                )
                                fallbackToPopular(requestId)
                            }
                            is AppResult.Error -> {
                                android.util.Log.i(
                                    "HomeViewModel",
                                    "rcmd 异常 ${failure.throwable.message}，降级热门榜"
                                )
                                fallbackToPopular(requestId)
                            }
                            // failure 只会是失败两类，这里兜一层让 when 穷尽
                            else -> fallbackToPopular(requestId)
                        }
                    }

                    // 过滤完什么都没有（全被时长和视觉词砍掉）：宁可退热门榜，也别给空屏
                    collected.isEmpty() -> {
                        android.util.Log.i("HomeViewModel", "rcmd 拉 " + batches + " 批后仍无可用条目，退回热门榜")
                        fallbackToPopular(requestId)
                    }

                    else -> {
                        discoveryFreshIdx = idx
                        applyDiscovery(requestId) {
                            android.util.Log.i(
                                "HomeViewModel",
                                "discovery apply source=rcmd batches=" + batches + " next_idx=" + idx +
                                    " oldHead=" + it.items.firstOrNull()?.episode?.bvid +
                                    " newHead=" + collected.firstOrNull()?.episode?.bvid +
                                    " count=" + collected.size
                            )
                            it.copy(
                                items = collected,
                                loading = false,
                                page = idx,
                                // 无限流，永远还有下一批
                                hasMore = true,
                                usePopularFallback = false,
                                error = null
                            )
                        }
                    }
                }
            } else {
                when (val result = content.ranking(rid)) {
                    is AppResult.Success -> applyDiscovery(requestId) {
                        it.copy(
                            items = result.data,
                            loading = false,
                            page = 1,
                            // 分区榜没有下一页，永远不给"加载更多"
                            hasMore = false,
                            error = null
                        )
                    }

                    is AppResult.Failure -> applyDiscovery(requestId) {
                        it.copy(loading = false, error = "分类榜获取失败：${result.message}")
                    }

                    is AppResult.Error -> applyDiscovery(requestId) {
                        it.copy(loading = false, error = "分类榜获取失败：${result.throwable.message}")
                    }
                }
            }
        }
    }

    /** 推荐流不可用时的兜底：退回热门榜第一页（内容稳定但至少不是空屏） */
    private suspend fun fallbackToPopular(requestId: Int) {
        when (val result = content.popular(page = 1)) {
            is AppResult.Success -> applyDiscovery(requestId) {
                it.copy(
                    items = result.data.items,
                    loading = false,
                    page = 1,
                    hasMore = result.data.hasMore,
                    usePopularFallback = true,
                    error = null
                )
            }

            is AppResult.Failure -> applyDiscovery(requestId) {
                it.copy(loading = false, error = "发现流获取失败：${result.message}")
            }

            is AppResult.Error -> applyDiscovery(requestId) {
                it.copy(loading = false, error = "发现流获取失败：${result.throwable.message}")
            }
        }
    }

    /** 只认最新一次请求的结果，切分类途中回来的旧榜直接丢 */
    private fun applyDiscovery(
        requestId: Int,
        block: (DiscoveryState) -> DiscoveryState
    ) {
        if (requestId != discoveryRequestId) return
        _discovery.update(block)
    }

    /** 发现流下一页：推荐档走 rcmd 的下一批，分类榜整榜一次给完、没有下一页 */
    fun loadMoreDiscovery() {
        val current = _discovery.value
        if (current.rid != 0) return
        if (current.loading || current.loadingMore || !current.hasMore) return
        val requestId = discoveryRequestId
        _discovery.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            if (current.usePopularFallback) {
                val nextPage = current.page + 1
                when (val result = content.popular(page = nextPage)) {
                    is AppResult.Success -> {
                        if (requestId != discoveryRequestId) {
                            _discovery.update { it.copy(loadingMore = false) }
                            return@launch
                        }
                        _discovery.update {
                            it.copy(
                                items = it.items + result.data.items,
                                loadingMore = false,
                                page = nextPage,
                                hasMore = result.data.hasMore,
                                error = null
                            )
                        }
                    }

                    is AppResult.Failure -> _discovery.update {
                        it.copy(loadingMore = false, error = "发现流加载更多失败：${result.message}")
                    }

                    is AppResult.Error -> _discovery.update {
                        it.copy(loadingMore = false, error = "发现流加载更多失败：${result.throwable.message}")
                    }
                }
                return@launch
            }

            var idx = discoveryFreshIdx
            var batches = 0
            var lastError: AppResult<PageResult<DiscoveryItem>>? = null
            val collected = ArrayList<DiscoveryItem>()
            while (batches < RCMD_BATCHES_PER_PAGE && collected.size < RCMD_PAGE_TARGET) {
                when (val result = content.recommend(idx)) {
                    is AppResult.Success -> {
                        val byKey = HashSet(collected.map { it.episode.key })
                        val fresh = result.data.items.filter { byKey.add(it.episode.key) }
                        if (fresh.isEmpty()) break
                        collected.addAll(fresh)
                        idx++
                        batches++
                    }
                    else -> {
                        lastError = result
                        break
                    }
                }
            }

            if (collected.isEmpty()) {
                _discovery.update {
                    it.copy(
                        loadingMore = false,
                        hasMore = false,
                        error = (lastError as? AppResult.Failure)?.message
                            ?.let { msg -> "推荐加载更多失败：$msg" }
                    )
                }
                return@launch
            }
            if (requestId != discoveryRequestId) {
                _discovery.update { it.copy(loadingMore = false) }
                return@launch
            }
            discoveryFreshIdx = idx
            _discovery.update { state ->
                // 按 episode.key 去重：不同 fresh_idx 之间会撞条目，
                // LazyColumn 的 key 重复会直接抛 IllegalArgumentException
                val existing = state.items.map { it.episode.key }.toHashSet()
                val fresh = collected.filterNot { it.episode.key in existing }
                state.copy(
                    items = state.items + fresh,
                    loadingMore = false,
                    page = idx,
                    hasMore = true,
                    error = null
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            auth.ensureBuvid()
            when (val profile = auth.profile()) {
                is AppResult.Success -> {
                    val user = profile.data
                    if (user != null) {
                        mid = user.mid
                        _state.update {
                            it.copy(
                                loggedIn = true,
                                userName = user.name,
                                avatar = user.face,
                                loading = false
                            )
                        }
                        // 用户在设置里关掉了同步，就别再去拉 B 站的数据
                        if (_state.value.syncFavorites) loadFavFolders()
                        if (_state.value.syncHistory) loadRemoteHistory()
                    } else {
                        _state.update {
                            it.copy(
                                loggedIn = false,
                                userName = null,
                                avatar = null,
                                loading = false
                            )
                        }
                    }
                }

                is AppResult.Failure -> _state.update {
                    it.copy(loading = false, error = profile.message)
                }

                is AppResult.Error -> _state.update {
                    it.copy(loading = false, error = profile.throwable.message ?: "网络异常")
                }
            }
        }
    }

    /**
     * 从登录页回来时用：只同步账号态和需要登录的资源，不置 loading。
     * 直接调 [refresh] 会把 loading 置 true，首页会闪一下骨架屏；
     * 可此刻页面上已经有内容，不该再重铺骨架。
     */
    fun onLoginStateChanged() {
        viewModelScope.launch {
            when (val profile = auth.profile()) {
                is AppResult.Success -> {
                    val user = profile.data
                    if (user != null) {
                        mid = user.mid
                        _state.update {
                            it.copy(loggedIn = true, userName = user.name, avatar = user.face)
                        }
                        // 用户在设置里关掉了同步，就别再去拉 B 站的数据
                        if (_state.value.syncFavorites) loadFavFolders()
                        if (_state.value.syncHistory) loadRemoteHistory()
                    }
                }

                is AppResult.Failure -> _state.update { it.copy(error = profile.message) }

                is AppResult.Error -> _state.update {
                    it.copy(error = profile.throwable.message ?: "网络异常")
                }
            }
        }
    }

    private fun loadRemoteHistory() {
        val requestId = ++historyRequestId
        viewModelScope.launch {
            when (val result = content.history()) {
                is AppResult.Success -> {
                    // 期间又刷新过一次（切 tab 回来）就丢掉这份旧结果，否则会被上一份盖回去
                    if (requestId != historyRequestId) return@launch
                    // 用户刚把同步关掉，这份在飞的旧数据不能再填回去
                    if (!_state.value.syncHistory) return@launch
                    historyMax = result.data.nextMax
                    historyViewAt = result.data.nextViewAt
                    _state.update {
                        it.copy(
                            remoteHistory = result.data.items,
                            historyHasMore = result.data.hasMore
                        )
                    }
                }

                is AppResult.Failure -> _state.update { it.copy(error = "历史获取失败：${result.message}") }
                is AppResult.Error -> _state.update { it.copy(error = "历史获取失败：${result.throwable.message}") }
            }
        }
    }

    /**
     * 清空本地播放历史。
     * 只删 Room 里的记录，断点位置一起丢；B 站账号里的观看记录在服务端，这里动不到，
     * 所以清完刷新一下 B 站历史照样还在——提示文案要把这点说清楚。
     */
    fun clearLocalHistory() {
        viewModelScope.launch { historyDao.clear() }
    }

    /** 往 B 站历史下一页；游标来自上一页，没有游标就认为到底 */    fun loadMoreHistory() {
        val current = _state.value
        if (!current.syncHistory) return
        if (current.historyLoadingMore || !current.historyHasMore) return
        if (historyMax <= 0L) {
            _state.update { it.copy(historyHasMore = false) }
            return
        }
        _state.update { it.copy(historyLoadingMore = true) }
        // 记下翻页时的序号：期间用户切 tab 触发过一次刷新的话，这一页不能追加到新列表
        val requestId = historyRequestId
        viewModelScope.launch {
            when (val result = content.history(max = historyMax, viewAt = historyViewAt)) {
                is AppResult.Success -> {
                    if (requestId != historyRequestId) {
                        _state.update { it.copy(historyLoadingMore = false) }
                        return@launch
                    }
                    historyMax = result.data.nextMax
                    historyViewAt = result.data.nextViewAt
                    _state.update {
                        it.copy(
                            remoteHistory = it.remoteHistory + result.data.items,
                            historyLoadingMore = false,
                            historyHasMore = result.data.hasMore,
                            error = null
                        )
                    }
                }

                is AppResult.Failure -> _state.update {
                    it.copy(historyLoadingMore = false, error = "历史加载更多失败：${result.message}")
                }

                is AppResult.Error -> _state.update {
                    it.copy(historyLoadingMore = false, error = "历史加载更多失败：${result.throwable.message}")
                }
            }
        }
    }

    private fun loadFavFolders() {
        if (!_state.value.syncFavorites) return
        if (mid <= 0L) return
        viewModelScope.launch {
            when (val result = content.favFolders(mid)) {
                is AppResult.Success -> {
                    if (!_state.value.syncFavorites) return@launch
                    _state.update { it.copy(favFolders = result.data) }
                }
                is AppResult.Failure -> _state.update { it.copy(error = "收藏夹获取失败：${result.message}") }
                is AppResult.Error -> _state.update { it.copy(error = "收藏夹获取失败：${result.throwable.message}") }
            }
        }
    }

    /** 选中某个收藏夹；传 null 表示本地收藏 */
    fun selectFolder(folderId: Long?) {
        // 同步关掉时远端夹子已经不存在了，只保留"本地收藏"这档
        if (folderId != null && !_state.value.syncFavorites) return
        if (folderId == null) {
            _state.update {
                it.copy(
                    selectedFolderId = null,
                    folderEpisodes = emptyList(),
                    folderFailed = false
                )
            }
            return
        }
        // 切夹子先把旧内容清掉，失败时才不会挂着上一个夹子的列表
        val requestId = ++folderRequestId
        _state.update {
            it.copy(
                selectedFolderId = folderId,
                folderEpisodes = emptyList(),
                folderFailed = false,
                folderLoading = true,
                folderPage = 1,
                folderHasMore = false,
                folderLoadingMore = false
            )
        }
        viewModelScope.launch {
            val result = content.favEpisodes(folderId, page = 1)
            // 已经不是最新一次切换（用户又点了别的夹子），直接丢弃这次结果
            if (requestId != folderRequestId) return@launch
            if (!_state.value.syncFavorites) return@launch
            when (result) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        folderEpisodes = result.data.items,
                        folderLoading = false,
                        folderFailed = false,
                        folderPage = 1,
                        folderHasMore = result.data.hasMore
                    )
                }

                is AppResult.Failure -> _state.update {
                    it.copy(
                        folderLoading = false,
                        folderFailed = true,
                        error = "收藏夹内容获取失败：${result.message}"
                    )
                }

                is AppResult.Error -> _state.update {
                    it.copy(
                        folderLoading = false,
                        folderFailed = true,
                        error = "收藏夹内容获取失败：${result.throwable.message}"
                    )
                }
            }
        }
    }

    /** 收藏夹翻下一页；翻页期间切了夹子就丢弃这一页 */
    fun loadMoreFolder() {
        val current = _state.value
        if (!current.syncFavorites) return
        val folderId = current.selectedFolderId ?: return
        if (current.folderLoading || current.folderLoadingMore || !current.folderHasMore) return
        val nextPage = current.folderPage + 1
        _state.update { it.copy(folderLoadingMore = true) }
        viewModelScope.launch {
            when (val result = content.favEpisodes(folderId, page = nextPage)) {
                is AppResult.Success -> _state.update { s ->
                    if (s.selectedFolderId != folderId) return@update s
                    s.copy(
                        folderEpisodes = s.folderEpisodes + result.data.items,
                        folderLoadingMore = false,
                        folderPage = nextPage,
                        folderHasMore = result.data.hasMore,
                        error = null
                    )
                }

                is AppResult.Failure -> _state.update { s ->
                    if (s.selectedFolderId != folderId) return@update s
                    s.copy(folderLoadingMore = false, error = "收藏夹加载更多失败：${result.message}")
                }

                is AppResult.Error -> _state.update { s ->
                    if (s.selectedFolderId != folderId) return@update s
                    s.copy(
                        folderLoadingMore = false,
                        error = "收藏夹加载更多失败：${result.throwable.message}"
                    )
                }
            }
        }
    }

    /**
     * 退出搜索态、回到"发现"流。
     * 新版首页没有独立的"搜索"tab，搜索结果就地展示在发现页上，
     * 所以要有一个明确的"取消搜索"动作把状态清干净，否则会一直停在结果页。
     */
    fun clearSearch() {
        lastQuery = null
        // 序号自增，让还在飞的旧请求回来时被丢掉
        searchRequestId++
        userSearchRequestId++
        _state.update {
            it.copy(
                searched = false,
                searching = false,
                searchResults = emptyList(),
                searchHasMore = false,
                searchLoadingMore = false,
                searchedUsers = false,
                searchingUsers = false,
                userResults = emptyList()
            )
        }
    }

    fun search(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        lastQuery = trimmed
        val requestId = ++searchRequestId
        _state.update {
            it.copy(
                searching = true,
                searched = true,
                error = null,
                searchPage = 1,
                searchHasMore = false,
                searchLoadingMore = false
            )
        }
        viewModelScope.launch {
            val result = content.search(trimmed, page = 1)
            if (requestId != searchRequestId) return@launch
            when (result) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        searchResults = result.data.items,
                        searching = false,
                        searchPage = 1,
                        searchHasMore = result.data.hasMore
                    )
                }

                is AppResult.Failure -> _state.update {
                    it.copy(searching = false, error = "搜索失败：${result.message}")
                }

                is AppResult.Error -> _state.update {
                    it.copy(searching = false, error = "搜索失败：${result.throwable.message}")
                }
            }
        }
    }

    /**
     * 搜索结果翻下一页。
     * 用 searchRequestId 判断是否还属于同一次搜索：用户改关键词重搜后，
     * 旧关键字的这一页回来必须丢掉，否则会把两个结果集拼在一起。
     */
    fun loadMoreSearch() {
        val current = _state.value
        val query = lastQuery ?: return
        if (current.searching || current.searchLoadingMore || !current.searchHasMore) return
        val requestId = searchRequestId
        val nextPage = current.searchPage + 1
        _state.update { it.copy(searchLoadingMore = true) }
        viewModelScope.launch {
            val result = content.search(query, page = nextPage)
            if (requestId != searchRequestId) {
                _state.update { it.copy(searchLoadingMore = false) }
                return@launch
            }
            when (result) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        searchResults = it.searchResults + result.data.items,
                        searchLoadingMore = false,
                        searchPage = nextPage,
                        searchHasMore = result.data.hasMore,
                        error = null
                    )
                }

                is AppResult.Failure -> _state.update {
                    it.copy(searchLoadingMore = false, error = "搜索加载更多失败：${result.message}")
                }

                is AppResult.Error -> _state.update {
                    it.copy(searchLoadingMore = false, error = "搜索加载更多失败：${result.throwable.message}")
                }
            }
        }
    }

    /**
     * UP 主搜索。单独一套 loading / searched 标志，
     * 这样在"稿件"和"UP 主"档位之间来回切不会把对方的结果冲掉。
     */
    fun searchUsers(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        val requestId = ++userSearchRequestId
        _state.update { it.copy(searchingUsers = true, searchedUsers = true, error = null) }
        viewModelScope.launch {
            val result = content.searchUsers(trimmed)
            if (requestId != userSearchRequestId) return@launch
            when (result) {
                is AppResult.Success -> _state.update {
                    it.copy(userResults = result.data, searchingUsers = false)
                }

                is AppResult.Failure -> _state.update {
                    it.copy(searchingUsers = false, error = "搜索失败：${result.message}")
                }

                is AppResult.Error -> _state.update {
                    it.copy(searchingUsers = false, error = "搜索失败：${result.throwable.message}")
                }
            }
        }
    }

    /** 打开稿件详情（选集列表） */
    fun openDetail(bvid: String) {
        val requestId = ++detailRequestId
        _state.update { it.copy(detailLoading = true) }
        viewModelScope.launch {
            val result = content.videoDetail(bvid)
            if (requestId != detailRequestId) return@launch
            when (result) {
                is AppResult.Success -> _state.update {
                    it.copy(detail = result.data, detailLoading = false)
                }

                is AppResult.Failure -> _state.update {
                    it.copy(detailLoading = false, error = "详情获取失败：${result.message}")
                }

                is AppResult.Error -> _state.update {
                    it.copy(detailLoading = false, error = "详情获取失败：${result.throwable.message}")
                }
            }
        }
    }

    fun closeDetail() = _state.update { it.copy(detail = null) }

    /**
     * 打开"我自己的主页"（个人页第一个入口）。
     * mid 是登录后从 profile 接口拿到的，未登录时是 0，这时返回 false 交给 UI 引导登录，
     * 不要在这里直接抛 error —— 个人页弹一条"打不开主页"的红字很突兀。
     */
    fun openMySpace(): Boolean {
        if (mid <= 0L) return false
        openSpace(mid, _state.value.userName)
        return true
    }

    /**
     * 打开 UP 主主页。
     * 入口在详情页的 UP 名上（搜索/收藏接口都不带 mid，只有 videoDetail 的 owner.mid 可靠）。
     * UP 主页是比详情更高一层的入口，进来先把详情收掉，返回时回到原来的 tab，避免状态叠三层。
     */
    fun openSpace(mid: Long, name: String?) {
        if (mid <= 0L) {
            _state.update { it.copy(error = "这条稿件没有带回 UP 主 ID，打不开主页") }
            return
        }
        val requestId = ++spaceRequestId
        _state.update {
            it.copy(
                detail = null,
                space = UpSpace(mid = mid, name = name.orEmpty(), loading = true)
            )
        }
        viewModelScope.launch {
            val result = content.spaceVideos(mid = mid, page = 1)
            if (requestId != spaceRequestId) return@launch
            when (result) {
                is AppResult.Success -> _state.update { s ->
                    s.copy(
                        space = s.space?.copy(
                            videos = result.data,
                            loading = false,
                            page = 1,
                            hasMore = result.data.size >= SPACE_PAGE_SIZE,
                            error = null
                        )
                    )
                }

                is AppResult.Failure -> _state.update { s ->
                    s.copy(space = s.space?.copy(loading = false, error = "主页获取失败：${result.message}"))
                }

                is AppResult.Error -> _state.update { s ->
                    s.copy(
                        space = s.space?.copy(
                            loading = false,
                            error = "主页获取失败：${result.throwable.message}"
                        )
                    )
                }
            }
        }
    }

    /** 翻下一页投稿；space 接口没有总数，空页或不满一页就认为到底了 */
    fun loadMoreSpace() {
        val space = _state.value.space ?: return
        if (space.loading || space.loadingMore || !space.hasMore) return
        val nextPage = space.page + 1
        _state.update { it.copy(space = space.copy(loadingMore = true)) }
        viewModelScope.launch {
            when (val result = content.spaceVideos(mid = space.mid, page = nextPage)) {
                is AppResult.Success -> _state.update { s ->
                    val current = s.space ?: return@update s
                    // 翻页期间用户切到了别的 UP 主，这一页属于上一个主页，不能追加进来
                    if (current.mid != space.mid) return@update s
                    s.copy(
                        space = current.copy(
                            videos = current.videos + result.data,
                            loadingMore = false,
                            page = nextPage,
                            hasMore = result.data.size >= SPACE_PAGE_SIZE,
                            error = null
                        )
                    )
                }

                is AppResult.Failure -> _state.update { s ->
                    val current = s.space ?: return@update s
                    if (current.mid != space.mid) return@update s
                    s.copy(
                        space = current.copy(
                            loadingMore = false,
                            hasMore = false,
                            error = "加载更多失败：${result.message}"
                        )
                    )
                }

                is AppResult.Error -> _state.update { s ->
                    val current = s.space ?: return@update s
                    if (current.mid != space.mid) return@update s
                    s.copy(
                        space = current.copy(
                            loadingMore = false,
                            hasMore = false,
                            error = "加载更多失败：${result.throwable.message}"
                        )
                    )
                }
            }
        }
    }

    fun closeSpace() = _state.update { it.copy(space = null) }

    /** 加入缓存队列 */
    fun cacheEpisode(episode: Episode) = ServiceLocator.cacheManager.enqueue(episode)

    /** 取消下载：CacheManager 内部会清理 .part 并写入 CANCELED */
    fun cancelCache(episodeKey: String) = ServiceLocator.cacheManager.cancel(episodeKey)

    fun logout() {
        auth.logout()
        mid = 0L
        _state.value = HomeUiState(loggedIn = false)
    }

    private companion object {
        /** 与 BiliApiService.spaceVideos 的 ps 保持一致，用来判断是否还有下一页 */
        const val SPACE_PAGE_SIZE = 30

        /**
         * 一次刷新最多连拉几批 rcmd。
         * 播客化过滤（>=10 分钟 + 砍视觉分区）后一批 12 条常常只剩两三条，
         * 只拉一批首页会很空；3 批约 1 秒，再多就明显卡顿。
         */
        const val RCMD_BATCHES_PER_PAGE = 3

        /** 攒够这么多条就停止凑批，省下多余的请求 */
        const val RCMD_PAGE_TARGET = 10
    }
}

 /**
 * 给缓存状态表补一份"稿件级"别名（v_{bvid}）。
 *
 * 原因：搜索结果拿不到 cid，卡片 key 是 v_{bvid}；缓存记录却精确到 v_{bvid}_{cid}。
 * 只有该稿件在状态表里恰好只出现一次时才补别名——多集同时存在时会串到别的集，宁可不补。
 */
private fun <T> Map<String, T>.withBvidAliases(): Map<String, T> {
    if (isEmpty()) return this
    val byBvid = HashMap<String, MutableList<String>>()
    for (key in keys) {
        val bvid = Episode.bvidOfKey(key) ?: continue
        byBvid.getOrPut(bvid) { mutableListOf() }.add(key)
    }
    val extra = LinkedHashMap<String, T>()
    for ((bvid, keysOfBvid) in byBvid) {
        val alias = Episode.bvidLevelKey(bvid)
        if (alias in this || keysOfBvid.size != 1) continue
        val value = this[keysOfBvid.first()] ?: continue
        extra[alias] = value
    }
    return if (extra.isEmpty()) this else this + extra
}
