package com.bilipod.app.home

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bilipod.app.cache.CacheState
import com.bilipod.app.common.Formatters
import com.bilipod.app.common.ui.BiliImage
import com.bilipod.app.common.ui.CategoryChip
import com.bilipod.app.common.ui.DiscoveryCard
import com.bilipod.app.common.ui.EmptyHint
import com.bilipod.app.common.ui.EpisodeCard
import com.bilipod.app.common.ui.FeaturedBanner
import com.bilipod.app.common.ui.PlanetMark
import com.bilipod.app.common.ui.ProfileRow
import com.bilipod.app.common.ui.QuietChip
import com.bilipod.app.common.ui.QuietTab
import com.bilipod.app.common.ui.ShimmerEpisodeList
import com.bilipod.app.player.PlayerViewModel
import com.bilipod.app.repository.model.Episode
import com.bilipod.app.repository.model.FavFolder
import com.bilipod.app.repository.model.PodcastDetail
import com.bilipod.app.repository.model.UserHit

/**
 * 首页：留白优先，卡片无阴影，靠层级色与间距区分。
 * 底部迷你播放条由 AppRoot 统一挂载，这里不再重复实现。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    playerVm: PlayerViewModel,
    /** 底部三档：0 发现 / 1 我的播客 / 2 个人，由 AppRoot 持有，和底栏共用 */
    homeTab: Int = 0,
    /**
     * "我的播客"下的二级分栏：0 本地历史 / 1 B站历史 / 2 收藏。
     * 状态放在 AppRoot 而不是这里，是为了让个人页的"收听历史 / 我的收藏"能把这一档一起切走
     * （点一下要同时换一级 tab 和二级分栏，两个状态在同一个地方才好一次改完）。
     */
    libraryTab: Int = 0,
    onLibraryTabChange: (Int) -> Unit = {},
    /** 让 PersonaPane 里的入口能切一级 tab（"收听历史"要跳到我的播客），同样由 AppRoot 执行 */
    onHomeTabChange: (Int) -> Unit = {},
    /**
     * 点底部"发现"的次数。每次 +1 就重拉一次榜单：
     * 用户在首页停留久了，靠下拉手势外还得有个"点一下就能刷新"的入口。
     */
    discoverTick: Int = 0,
    onOpenCache: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val discovery by vm.discovery.collectAsStateWithLifecycle()
    val localHistory by vm.localHistory.collectAsStateWithLifecycle()
    val localFavorites by vm.localFavorites.collectAsStateWithLifecycle()
    val cacheStates by vm.cacheStates.collectAsStateWithLifecycle()
    val cacheStatuses by vm.cacheStatuses.collectAsStateWithLifecycle()
    // 详情页要把"正在播放的那一集"标出来并自动定位过去，大合集（910 集）靠手翻找不现实
    val playerState by playerVm.uiState.collectAsStateWithLifecycle()

    // "我的播客"下的二级分栏（0 本地历史 / 1 B站历史 / 2 收藏）已上提到 AppRoot，见参数 libraryTab

    // 分类 chips 的"分类"按钮是否展开全部 B 站分区
    var chipsExpanded by remember { mutableStateOf(false) }
    var keyword by remember { mutableStateOf("") }
    // 搜索档位：0=稿件，1=UP 主。放 UI 层即可，不需要进 ViewModel 状态
    var searchScope by remember { mutableIntStateOf(0) }
    // 退出登录的二次确认弹层
    var showLogoutConfirm by remember { mutableStateOf(false) }
    // 清空本地历史的二次确认：和退出登录分开两个状态，避免互相顶掉
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    // 问题反馈弹层：不连服务器，只把能复制的诊断信息摆出来
    var showFeedback by remember { mutableStateOf(false) }
    // 未登录点播放时的提示（首启不再强塞登录页，改成播放这一刻才提醒）
    var showLoginPrompt by remember { mutableStateOf(false) }

    val play: (List<Episode>, Int, Long) -> Unit = { episodes, index, position ->
        // 只起播、不切页：卡片点下去就在原地开始听，底部迷你条接管进度，
        // 想进全屏播放器点迷你条即可（不打断正在浏览的列表）
        if (!state.loggedIn) {
            // 未登录直接播会拿不到完整音流，先提示去登录，别让用户以为是播放器坏了
            showLoginPrompt = true
        } else {
            playerVm.play(episodes, index, position)
        }
    }

    // 发现页没有独立的"搜索 tab"：搜索结果就地覆盖在推荐流上，靠这两个布尔量分派
    val inSearch = state.searched || state.searchedUsers

    // 切底部导航时把盖在列表上的详情 / UP 主页先关掉，否则点了底栏还停在详情里
    LaunchedEffect(homeTab) {
        if (state.detail != null) vm.closeDetail()
        if (state.space != null) vm.closeSpace()
    }

    // 点底部"发现"= 刷新榜单。搜索态下点它先退回发现流，否则刷新结果看不见
    LaunchedEffect(discoverTick) {
        if (discoverTick <= 0) return@LaunchedEffect
        if (inSearch) {
            keyword = ""
            vm.clearSearch()
        }
        vm.refreshDiscovery()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 个人页不放大标题：昵称 + 头像就是页面起点，和小宇宙一致
        if (homeTab != 2) {
            Row(
            modifier = Modifier
                .fillMaxWidth()
                // 避让状态栏：不加的话标题和右上角图标会被状态栏压住，触摸坐标也会整体偏移
                .statusBarsPadding()
                // 顶栏上方统一留 60dp 空白（个人页同样是 60dp），标题离状态栏更远，整体重心下移
                .padding(start = 20.dp, end = 8.dp, top = 60.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (homeTab) {
                        0 -> "发现"
                        1 -> "我的播客"
                        else -> "个人"
                    },
                    // 小宇宙的大标题是"更大 + 更粗 + 负字距"，标题本身承担视觉重量
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.8).sp,
                        // 行高与右侧品牌标记同高（32dp），两者上下沿对齐
                        lineHeight = 32.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                // 发现页不再显示账号昵称：顶栏只留"发现"一行大字，和右侧品牌标记单行对齐
                val subtitle = when (homeTab) {
                    1 -> "历史 · 收藏 · 下载"
                    else -> null
                }
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // 发现档顶栏只留品牌标记：设置和账号都已挪到个人页，
            // 顶栏塞两个图标是上一版"不像小宇宙"最明显的地方
            if (homeTab == 0) {
                PlanetMark(modifier = Modifier.padding(end = 16.dp))
            }
            if (homeTab == 1) {
                IconButton(
                    onClick = {
                        // 未登录 → 去扫码；已登录 → 就地弹出退出确认，避免误退
                        if (state.loggedIn) {
                            showLogoutConfirm = true
                        } else {
                            onOpenLogin()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = if (state.loggedIn) "账号（已登录）" else "账号",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            }
            if (homeTab == 1) {
                IconButton(onClick = onOpenCache) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "缓存管理",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            }
            }
        }

        if (showLogoutConfirm) {
            LogoutConfirmDialog(
                userName = state.userName,
                onDismiss = { showLogoutConfirm = false },
                onConfirm = {
                    showLogoutConfirm = false
                    vm.closeDetail()
                    vm.logout()
                }
            )
        }

        if (showFeedback) {
            FeedbackDialog(onDismiss = { showFeedback = false })
        }

        if (showClearHistoryConfirm) {
            ConfirmDialog(
                title = "清空本地播放历史",
                message = "将删除本机 ${localHistory.size} 条记录，断点续播位置一并丢失。" +
                    "B 站账号里的观看记录在服务端，这里动不到，清完照样能看。",
                confirmText = "清空",
                onDismiss = { showClearHistoryConfirm = false },
                onConfirm = {
                    showClearHistoryConfirm = false
                    vm.clearLocalHistory()
                }
            )
        }

        if (showLoginPrompt) {
            ConfirmDialog(
                title = "播放需要登录",
                message = "未登录拿不到完整音流。登录只用于读取你自己的收藏与收听历史，不做其他用途。",
                confirmText = "去登录",
                onDismiss = { showLoginPrompt = false },
                onConfirm = {
                    showLoginPrompt = false
                    onOpenLogin()
                }
            )
        }

        if (homeTab == 0) {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                placeholder = {
                    Text(
                        text = if (searchScope == 1) "搜索 UP 主" else "搜索稿件 / 播客",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                // 放大镜在左边（小宇宙的搜索框就是左镜右扫），点它才发请求
                leadingIcon = {
                    IconButton(onClick = {
                        // 跟着当前档位走，避免"在 UP 主档点搜索却出稿件结果"的错位
                        if (searchScope == 1) vm.searchUsers(keyword) else vm.search(keyword)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }
                },
                trailingIcon = {
                    // 有输入才出现，点一下同时清空输入和搜索态，和"取消"是同一套状态
                    if (keyword.isNotEmpty()) {
                        IconButton(onClick = {
                            keyword = ""
                            vm.clearSearch()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "清空",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    // 小宇宙的搜索框是"浅灰实心 + 大圆角 + 无边框"，不画任何描边
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        // 分类 chips：横滑一排。"分类"负责展开/收起更多分区，其余 chips 直接换榜
        if (homeTab == 0 && !inSearch) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    CategoryChip(
                        text = "分类",
                        selected = chipsExpanded,
                        leadingIcon = Icons.Default.GridView,
                        onClick = { chipsExpanded = !chipsExpanded }
                    )
                }
                // 默认档（rid = 0 的推荐流）必须留一个能回来的入口，
                // 否则点过任意分区榜之后就再也回不到"猜你喜欢"了
                item {
                    CategoryChip(
                        text = "推荐",
                        selected = discovery.rid == 0,
                        onClick = { vm.selectCategory(0, "推荐") }
                    )
                }
                val visible = if (chipsExpanded) {
                    DISCOVERY_CATEGORIES
                } else {
                    DISCOVERY_CATEGORIES.take(4)
                }
                items(count = visible.size) { index ->
                    val (rid, name) = visible[index]
                    CategoryChip(
                        text = name,
                        selected = discovery.rid == rid,
                        onClick = { vm.selectCategory(rid, name) }
                    )
                }
            }
        }

        // 搜索态下显示"稿件 / UP 主"档位 + 取消；"我的播客"下显示二级分栏
        if (homeTab == 0 && inSearch) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuietTab(text = "稿件", selected = searchScope == 0) { searchScope = 0 }
                QuietTab(text = "UP 主", selected = searchScope == 1) { searchScope = 1 }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            keyword = ""
                            vm.clearSearch()
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }

        if (homeTab == 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MY_TABS.forEachIndexed { index, title ->
                    QuietTab(
                        text = title,
                        selected = libraryTab == index,
                        onClick = {
                            onLibraryTabChange(index)
                            if (state.detail != null) vm.closeDetail()
                            if (state.space != null) vm.closeSpace()
                        }
                    )
                }
                // 只挂在"本地历史"这档：B站历史在服务端，这里清不了也不该给个点了没用的按钮
                if (libraryTab == 0) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "清空",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (localHistory.isEmpty()) 0.3f else 0.6f
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = localHistory.isNotEmpty()) {
                                showClearHistoryConfirm = true
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(2.dp))

        Box(modifier = Modifier.weight(1f)) {
            val detail = state.detail
            val space = state.space
            val remoteHistory = state.remoteHistory
            val searchResults = state.searchResults
            when {
                state.loading -> ShimmerEpisodeList()

                // 详情正在拉取（detail 还没到）时给骨架屏，别让用户以为没反应
                state.detailLoading && detail == null -> ShimmerEpisodeList(rows = 4)

                // 层级：详情覆盖在 UP 主页之上；从主页点卡片进详情，详情返回键回落到主页。
                // 反方向（详情里点 UP 名）在 openSpace() 里主动清掉 detail，所以这里不会打架。
                detail != null -> DetailContent(
                    detail = detail,
                    loading = state.detailLoading,
                    cacheStates = cacheStates,
                    cacheStatuses = cacheStatuses,
                    currentKey = playerState.currentKey,
                    currentIndex = if (playerState.queueSize == detail.episodes.size) {
                        playerState.currentIndex
                    } else {
                        -1
                    },
                    onBack = { vm.closeDetail() },
                    onOpenSpace = { vm.openSpace(detail.ownerMid, detail.upName) },
                    onPlay = play,
                    onCache = { vm.cacheEpisode(it) },
                    onCancel = { vm.cancelCache(it) }
                )

                space != null -> UpSpaceContent(
                    space = space,
                    cacheStates = cacheStates,
                    cacheStatuses = cacheStatuses,
                    onBack = { vm.closeSpace() },
                    onCardClick = { episode ->
                        val bvid = episode.bvid
                        if (bvid.isNullOrBlank()) {
                            play(space.videos, space.videos.indexOf(episode), 0L)
                        } else {
                            vm.openDetail(bvid)
                        }
                    },
                    onCache = { vm.cacheEpisode(it) },
                    onCancel = { vm.cancelCache(it) },
                    onLoadMore = { vm.loadMoreSpace() },
                    onRetry = { vm.openSpace(space.mid, space.name) }
                )

                // 发现：对齐小宇宙首页 —— 榜单大卡 + "为你推荐" + 连续节目流
                homeTab == 0 && !inSearch -> {
                    when {
                        // 只有"真的没有内容"才铺骨架屏，切分类时旧榜保留一帧也无妨
                        discovery.loading && discovery.items.isEmpty() -> ShimmerEpisodeList(rows = 5)

                        discovery.items.isEmpty() -> EmptyHint(
                            title = "发现流没拉到内容",
                            subtitle = discovery.error ?: "点上方的分区，或切回推荐重新拉一次"
                        )

                        else -> {
                            val feedQueue = discovery.items.map { it.episode }
                            // 榜一单独做成顶部大卡，列表从第二条开始，避免同一条出现两遍
                            val head = discovery.items.first()
                            val rest = discovery.items.drop(1)
                            // 下拉刷新：isRefreshing 直接吃 discovery.loading，
                             // refreshDiscovery 无论有没有旧内容都会把它置 true
                            PullToRefreshBox(
                                isRefreshing = discovery.loading,
                                onRefresh = { vm.refreshDiscovery() },
                                modifier = Modifier.fillMaxSize()
                            ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)
                            ) {
                                item(key = "featured") {
                                    FeaturedBanner(
                                        episode = head.episode,
                                        tag = if (discovery.rid == 0) {
                                            // 推荐档底下已经是按账号画像出的 rcmd 流，再叫"今日热门"就不对了
                                            "猜你喜欢"
                                        } else {
                                            "${discovery.label}榜 1"
                                        },
                                        upName = head.episode.upName ?: head.channel,
                                        // Banner 整卡和"点击收听"胶囊统一成同一个动作：
                                        // 起播并进播放器，不再分"点卡片看详情 / 点胶囊才播"两套逻辑
                                        onClick = { play(feedQueue, 0, 0L) },
                                        onPlay = { play(feedQueue, 0, 0L) }
                                    )
                                    Spacer(Modifier.height(20.dp))
                                    Text(
                                        text = if (discovery.rid == 0) {
                                            "为你推荐"
                                        } else {
                                            "${discovery.label}热门"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                                items(
                                    count = rest.size,
                                    key = { index -> rest[index].episode.key }
                                ) { index ->
                                    val item = rest[index]
                                    // 队列是整榜（含榜一），所以位置要在整体里算
                                    val position = index + 1
                                    DiscoveryCard(
                                        episode = item.episode,
                                        // UP 名比分区名更像"主播名"，优先显示
                                        channel = item.episode.upName ?: item.channel,
                                        // 接口的 desc 经常是空，空就退回分区名当摘要
                                        summary = item.summary ?: item.channel,
                                        meta = buildString {
                                            // 热门榜带评论数；推荐流没有评论数，只有播放量，两个都别空着
                                            when {
                                                item.reply > 0L ->
                                                    append("评论 ").append(Formatters.count(item.reply.toInt()))

                                                item.view > 0L ->
                                                    append("播放 ").append(Formatters.count(item.view.toInt()))
                                            }
                                            if (item.episode.durationSec > 0L) {
                                                if (isNotEmpty()) append("  ·  ")
                                                append(Formatters.duration(item.episode.durationSec))
                                            }
                                        },
                                        // 播客的默认动作就是"开始听"：点卡片直接起播并进播放页，
                                        // 不再跳单稿详情（那是图文时代的作品页，在播客场景里是多余一跳）。
                                        // 详情仍然保留在搜索结果里，那边按"看简介"理解
                                        onClick = { play(feedQueue, position, 0L) },
                                        onPlay = { play(feedQueue, position, 0L) }
                                    )
                                }
                                if (discovery.hasMore) {
                                    item {
                                        LoadMoreFooter(
                                            loadingMore = discovery.loadingMore,
                                            onLoadMore = { vm.loadMoreDiscovery() }
                                        )
                                    }
                                }
                            }
                            }
                        }
                    }
                }

                homeTab == 1 && libraryTab == 0 -> {
                    if (localHistory.isEmpty()) {
                        EmptyHint(
                            title = "还没有播放记录",
                            subtitle = "从搜索或收藏里挑一集开始听"
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(localHistory.size) { index ->
                                val item = localHistory[index]
                                EpisodeCard(
                                    episode = item.episode,
                                    meta = "上次听到 " + Formatters.durationMs(item.positionMs),
                                    onClick = {
                                        play(
                                            localHistory.map { it.episode },
                                            index,
                                            item.positionMs
                                        )
                                    },
                                    trailing = {
                                        DownloadAction(
                                            key = item.episode.key,
                                            live = cacheStates[item.episode.key],
                                            persisted = cacheStatuses[item.episode.key],
                                            onDownload = { vm.cacheEpisode(item.episode) },
                                            onCancel = { vm.cancelCache(item.episode.key) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                homeTab == 1 && libraryTab == 1 -> {
                    when {
                        !state.syncHistory -> EmptyHint(
                            title = "已关闭与 B 站的同步",
                            subtitle = "在设置里改回「本机 + B 站」就能看到账号里的观看记录"
                        )

                        remoteHistory.isEmpty() -> EmptyHint(
                            title = if (state.loggedIn) "还没有 B 站观看记录" else "登录后同步 B 站历史",
                            subtitle = if (state.loggedIn) null else "扫码登录只用于读取你自己的数据"
                        )

                        else -> EpisodeList(
                            episodes = remoteHistory,
                            cacheStates = cacheStates,
                            cacheStatuses = cacheStatuses,
                            onCardClick = { index -> play(remoteHistory, index, 0L) },
                            onCache = { vm.cacheEpisode(it) },
                            onCancel = { vm.cancelCache(it) },
                            loadingMore = state.historyLoadingMore,
                            hasMore = state.historyHasMore,
                            onLoadMore = { vm.loadMoreHistory() }
                        )
                    }
                }

                homeTab == 1 && libraryTab == 2 -> FavContent(
                    folders = state.favFolders,
                    selectedFolderId = state.selectedFolderId,
                    localFavorites = localFavorites,
                    folderEpisodes = state.folderEpisodes,
                    loading = state.folderLoading,
                    failed = state.folderFailed,
                    syncEnabled = state.syncFavorites,
                    loadingMore = state.folderLoadingMore,
                    hasMore = state.folderHasMore,
                    cacheStates = cacheStates,
                    cacheStatuses = cacheStatuses,
                    onSelectFolder = { vm.selectFolder(it) },
                    onRetry = { vm.selectFolder(state.selectedFolderId) },
                    onLoadMore = { vm.loadMoreFolder() },
                    onPlay = { episodes, index -> play(episodes, index, 0L) },
                    onCache = { vm.cacheEpisode(it) },
                    onCancel = { vm.cancelCache(it) }
                )

                homeTab == 0 -> Column(modifier = Modifier.fillMaxSize()) {
                    // 档位行统一在顶栏下方渲染（带"取消"），这里不再重复画一遍

                    Box(modifier = Modifier.weight(1f)) {
                        if (searchScope == 1) {
                            // UP 主结果：头像 + 名字 + 简介 + 粉丝/投稿数，点一下进 UP 主页
                            when {
                                state.searchingUsers -> ShimmerEpisodeList(rows = 4)
                                !state.searchedUsers -> EmptyHint(
                                    title = "输入关键词搜索 UP 主",
                                    subtitle = "搜到后点头像旁的卡片进入 TA 的投稿列表"
                                )
                                state.userResults.isEmpty() -> EmptyHint(
                                    title = "没有找到相关 UP 主",
                                    subtitle = "换个关键词，或切回稿件档位搜"
                                )
                                else -> LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(state.userResults.size) { index ->
                                        val hit = state.userResults[index]
                                        UserHitCard(
                                            hit = hit,
                                            onClick = { vm.openSpace(hit.mid, hit.name) }
                                        )
                                    }
                                }
                            }
                        } else {
                            when {
                                state.searching -> ShimmerEpisodeList(rows = 4)
                                !state.searched -> EmptyHint(
                                    title = "输入关键词开始搜索",
                                    subtitle = "支持稿件标题与 UP 主相关结果"
                                )
                                searchResults.isEmpty() -> EmptyHint(
                                    title = "没有找到相关稿件",
                                    subtitle = "换个关键词试试"
                                )
                                else -> EpisodeList(
                                    episodes = searchResults,
                                    cacheStates = cacheStates,
                                    cacheStatuses = cacheStatuses,
                                    // 搜索结果点卡片先进详情看选集；没有 bvid 的异常条目退化成直接播放
                                    onCardClick = { index ->
                                        val episode = searchResults[index]
                                        val bvid = episode.bvid
                                        if (bvid.isNullOrBlank()) {
                                            play(searchResults, index, 0L)
                                        } else {
                                            vm.openDetail(bvid)
                                        }
                                    },
                                    onCache = { vm.cacheEpisode(it) },
                                    onCancel = { vm.cancelCache(it) },
                                    loadingMore = state.searchLoadingMore,
                                    hasMore = state.searchHasMore,
                                    onLoadMore = { vm.loadMoreSearch() }
                                )
                            }
                        }
                    }
                }

                // 个人：账号状态 + 菜单，和小宇宙个人页同构
                else -> ProfilePane(
                    loggedIn = state.loggedIn,
                    userName = state.userName,
                    avatar = state.avatar,
                    syncHistory = state.syncHistory,
                    syncFavorites = state.syncFavorites,
                    onOpenMySpace = {
                        // 未登录时 mid 是 0，直接送登录页；不在这里弹"打不开主页"的红字
                        if (!vm.openMySpace()) onOpenLogin()
                    },
                    onOpenHistory = {
                        // 一级 tab 和二级分栏要一起切，只换一级会停在"本地历史"上
                        onLibraryTabChange(1)
                        onHomeTabChange(1)
                    },
                    onOpenFavorites = {
                        onLibraryTabChange(2)
                        onHomeTabChange(1)
                    },
                    onOpenCache = onOpenCache,
                    onOpenSettings = onOpenSettings,
                    onFeedback = { showFeedback = true },
                    onAccountClick = {
                        if (state.loggedIn) showLogoutConfirm = true else onOpenLogin()
                    }
                )
            }
        }

        state.error?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** "我的播客"下的二级分栏 */
private val MY_TABS = listOf("本地历史", "B站历史", "收藏")

/**
 * 发现页的分类 chips：(B 站分区 rid, 显示名)。
 * rid 直接给 `x/web-interface/ranking/v2` 用，实测未登录也能返回整榜。
 *
 * 只留"人声口播密度高"的分区。音乐 MV、游戏实况、鬼畜、舞蹈、美妆穿搭、萌宠（动物）、
 * 吃播（美食）、体育集锦（运动）都是纯视觉驱动的内容，抽掉画面听不出东西，所以整块不提供入口。
 */
private val DISCOVERY_CATEGORIES = listOf(
    36 to "知识",
    188 to "科技",
    160 to "生活",
    181 to "影视",
    100 to "纪录片"
)

/** UP 主页自动翻页最多自动补到第几页，之后交给"加载更多"按钮 */
private const val AUTO_LOAD_MAX_PAGE = 2

/**
 * 个人页：账号信息 + 菜单。
 * 结构对齐小宇宙个人页：头像昵称在上，下面一列"图标 + 标题 + 右箭头"，不套卡片、不画底色。
 *
 * 关于少掉的几行（创作中心 / 我的通知 / 我的赞赏 / 付费账户）：这几项在本项目没有对应的 B 站
 * 数据源，做出来只能是点了不响的假按钮，所以宁可不做。能落地的都接了真实页面：
 * 个人主页走 space 接口、历史与收藏切到"我的播客"对应分栏、缓存和设置是原有二级页、
 * 反馈弹层给的是可复制的诊断信息。
 */
@Composable
private fun ProfilePane(
    loggedIn: Boolean,
    userName: String?,
    avatar: String?,
    syncHistory: Boolean,
    syncFavorites: Boolean,
    onOpenMySpace: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenCache: () -> Unit,
    onOpenSettings: () -> Unit,
    onFeedback: () -> Unit,
    onAccountClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 菜单从 3 行变 8 行，小屏或大字号下会顶出去，加一层滚动兜底
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 和发现 / 我的播客统一：状态栏之下再留 60dp，昵称不贴状态栏
                .padding(start = 20.dp, end = 20.dp, top = 60.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (loggedIn) userName?.takeIf { it.isNotBlank() } ?: "已登录" else "未登录",
                    // 昵称比原来的 titleLarge 再大一号，个人页第一眼就能认出是谁
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 27.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.4).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (loggedIn) "B 站账号已登录" else "登录后可同步收藏与观看历史",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            // 头像自己也是登录入口：没登录跳扫码，已登录弹退出确认
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .clickable(onClick = onAccountClick),
                contentAlignment = Alignment.Center
            ) {
                if (!avatar.isNullOrBlank()) {
                    BiliImage(url = avatar)
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 第一组：都能落到真实页面
        ProfileRow(
            icon = Icons.Default.Person,
            title = "个人主页",
            subtitle = if (loggedIn) null else "登录后打开",
            onClick = onOpenMySpace
        )
        ProfileRow(
            icon = Icons.Default.History,
            title = "收听历史",
            subtitle = if (syncHistory) "本机 + B 站" else "仅本机",
            onClick = onOpenHistory
        )
        ProfileRow(
            icon = Icons.Default.Star,
            title = "我的收藏",
            subtitle = if (syncFavorites) "本机 + B 站" else "仅本机",
            onClick = onOpenFavorites
        )
        ProfileRow(
            icon = Icons.Default.Download,
            title = "缓存管理",
            subtitle = "已下载的音频",
            onClick = onOpenCache
        )

        // 小宇宙在"付费账户"和"设置"之间留了一条极淡的分割线，这里保留同样的分组节奏
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )

        ProfileRow(
            icon = Icons.Default.Settings,
            title = "设置",
            subtitle = "默认倍速 / 缓存上限",
            onClick = onOpenSettings
        )
        ProfileRow(
            icon = Icons.Default.Feedback,
            title = "问题反馈",
            subtitle = "版本号 / 设备信息",
            onClick = onFeedback
        )

        Spacer(Modifier.height(10.dp))

        ProfileRow(
            icon = Icons.Default.AccountCircle,
            title = if (loggedIn) "退出登录" else "扫码登录",
            onClick = onAccountClick
        )

        Spacer(Modifier.height(20.dp))
        Text(
            text = "BiliPod · 非官方 B 站播客客户端",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun EpisodeList(
    episodes: List<Episode>,
    cacheStates: Map<String, CacheState>,
    cacheStatuses: Map<String, CacheState.Status>,
    onCardClick: (Int) -> Unit,
    onCache: (Episode) -> Unit,
    onCancel: (String) -> Unit,
    loadingMore: Boolean = false,
    hasMore: Boolean = false,
    onLoadMore: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(episodes.size) { index ->
            val episode = episodes[index]
            EpisodeCard(
                episode = episode,
                onClick = { onCardClick(index) },
                trailing = {
                    DownloadAction(
                        key = episode.key,
                        live = cacheStates[episode.key],
                        persisted = cacheStatuses[episode.key],
                        onDownload = { onCache(episode) },
                        onCancel = { onCancel(episode.key) }
                    )
                }
            )
        }
        if (hasMore) {
            item { LoadMoreFooter(loadingMore = loadingMore, onLoadMore = onLoadMore) }
        }
    }
}

/**
 * 列表底部的"加载更多"，搜索 / 收藏夹 / B站历史三处共用。
 * 只做手动触发：这些接口都有风控，不能像 UP 主页那样滚到底自动连拉。
 */
@Composable
private fun LoadMoreFooter(loadingMore: Boolean, onLoadMore: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loadingMore) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        } else {
            Text(
                text = "加载更多",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onLoadMore)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * 卡片右侧的下载按钮，四个形态：
 * - 可下载：下载图标，点击入队；
 * - 下载中 / 排队：环形进度 + 百分比，点击取消（CacheManager 会清理 .part 并写 CANCELED）；
 * - 已缓存：对勾，不可点；
 * - 下载失败：重试图标，点击重新入队。
 *
 * 状态来源：live 是内存态（有进度），persisted 是 Room 落库态（重启后仍能显示已缓存/失败）。
 */
@Composable
private fun DownloadAction(
    key: String,
    live: CacheState?,
    persisted: CacheState.Status?,
    onDownload: () -> Unit,
    onCancel: () -> Unit
) {
    // Room 是最终口径：已成功就一律显示"已缓存"，避免残留的内存态把成功态盖掉
    val status = when {
        persisted == CacheState.Status.SUCCESS -> CacheState.Status.SUCCESS
        else -> live?.status ?: persisted ?: CacheState.Status.IDLE
    }
    val running = live != null && live.isRunning && status != CacheState.Status.SUCCESS

    when {
        running -> {
            // B 站 CDN 的音频响应有时没有 Content-Length（totalBytes = 0），
            // 这种情况进度算不出来，用不确定态转圈 + 已下载体积，别死停在 0%
            val hasTotal = live!!.totalBytes > 0L
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (hasTotal) {
                        CircularProgressIndicator(
                            progress = { live.progress },
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when {
                        live.status == CacheState.Status.WAITING -> "排队"
                        hasTotal -> "${(live.progress * 100).toInt()}%"
                        else -> Formatters.size(live.downloadedBytes)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        status == CacheState.Status.SUCCESS -> {
            IconButton(onClick = {}, enabled = false) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已缓存",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
            }
        }

        status == CacheState.Status.FAILED -> {
            IconButton(onClick = onDownload) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "重试下载",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        else -> {
            IconButton(onClick = onDownload) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "缓存",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun UserHitCard(hit: UserHit, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
            ) {
                BiliImage(url = hit.face)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hit.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                hit.sign?.takeIf { it.isNotBlank() }?.let { sign ->
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = sign,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = Formatters.count(hit.fans) + " 粉丝 · " + hit.videos + " 投稿",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun FavContent(
    folders: List<FavFolder>,
    selectedFolderId: Long?,
    localFavorites: List<Episode>,
    folderEpisodes: List<Episode>,
    loading: Boolean,
    failed: Boolean,
    syncEnabled: Boolean,
    loadingMore: Boolean,
    hasMore: Boolean,
    cacheStates: Map<String, CacheState>,
    cacheStatuses: Map<String, CacheState.Status>,
    onSelectFolder: (Long?) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onPlay: (List<Episode>, Int) -> Unit,
    onCache: (Episode) -> Unit,
    onCancel: (String) -> Unit
) {
    val showLocal = !syncEnabled || selectedFolderId == null
    val episodes = if (showLocal) localFavorites else folderEpisodes

    Column(modifier = Modifier.fillMaxSize()) {
        if (!syncEnabled) {
            // 关掉同步就不给收藏夹 chip 了：留着也点不出内容，只会像假按钮
            Text(
                text = "仅显示本机收藏 · 在设置里可重新开启 B 站同步",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        } else {
            LazyRow(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    QuietChip(
                        text = "本地收藏",
                        selected = showLocal,
                        onClick = { onSelectFolder(null) }
                    )
                }
                items(folders.size) { index ->
                    val folder = folders[index]
                    QuietChip(
                        text = folder.title,
                        selected = selectedFolderId == folder.id,
                        onClick = { onSelectFolder(folder.id) }
                    )
                }
            }
        }

        when {
            loading -> ShimmerEpisodeList(rows = 4)
            // 拉取失败不能落成"这个收藏夹是空的"，否则用户会以为夹子里真没东西
            failed && !showLocal -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "收藏夹内容拉取失败",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "网络抖了一下，点这里重试",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    modifier = Modifier.clickable { onRetry() }
                )
            }
            episodes.isEmpty() -> EmptyHint(
                title = if (showLocal) "还没有收藏的节目" else "这个收藏夹是空的",
                subtitle = if (showLocal) "在播放器点右上角小心心即可收藏" else null
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(episodes.size) { index ->
                    val episode = episodes[index]
                    EpisodeCard(
                        episode = episode,
                        onClick = { onPlay(episodes, index) },
                        trailing = {
                            DownloadAction(
                                key = episode.key,
                                live = cacheStates[episode.key],
                                persisted = cacheStatuses[episode.key],
                                onDownload = { onCache(episode) },
                                onCancel = { onCancel(episode.key) }
                            )
                        }
                    )
                }
                // 本地收藏来自 Room，一次全给，没有分页；只有远端收藏夹需要"加载更多"
                if (!showLocal && hasMore) {
                    item { LoadMoreFooter(loadingMore = loadingMore, onLoadMore = onLoadMore) }
                }
            }
        }
    }
}

@Composable
private fun DetailContent(
    detail: PodcastDetail,
    loading: Boolean,
    cacheStates: Map<String, CacheState>,
    cacheStatuses: Map<String, CacheState.Status>,
    currentKey: String?,
    currentIndex: Int,
    onBack: () -> Unit,
    onOpenSpace: () -> Unit,
    onPlay: (List<Episode>, Int, Long) -> Unit,
    onCache: (Episode) -> Unit,
    onCancel: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 系统返回键也能退出详情，避免只能点左上角"返回"
        BackHandler(onBack = onBack)

        Text(
            text = "←  返回",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                BiliImage(url = detail.cover)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                // UP 名就是主页入口；ownerMid 为 0（接口没带）时不可点，避免点出一片空
                val canOpenSpace = detail.ownerMid > 0L && !detail.upName.isNullOrBlank()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = canOpenSpace, onClick = onOpenSpace)
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = detail.upName.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (canOpenSpace) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (canOpenSpace) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    // 合集和多分 P 稿件在播放体验上不一样，这里区分出来
                    text = if (detail.isSeason) {
                        "合集  ·  共 ${detail.episodes.size} 集"
                    } else {
                        "共 ${detail.episodes.size} 集"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        detail.desc?.takeIf { it.isNotBlank() }?.let { desc ->
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }

        when {
            loading -> ShimmerEpisodeList(rows = 4)
            detail.episodes.isEmpty() -> EmptyHint(title = "这一稿没有可播放的分集")
            else -> {
                val listState = rememberLazyListState()
                // 分 P 稿件的 key 都是 v_{bvid}（同一稿件共用），光靠 key 会把一屏同稿件的卡片全部点亮，
                // 所以这里用"播放队列索引"判定当前集：队列长度与选集数一致时才认这个索引。
                val playingIndex = if (
                    currentIndex in detail.episodes.indices &&
                    detail.episodes[currentIndex].key == currentKey
                ) {
                    currentIndex
                } else {
                    -1
                }
                // 打开详情时如果这一集正在播，直接定位过去；910 集的合集不定位基本找不到
                LaunchedEffect(detail.title, playingIndex, detail.episodes.size) {
                    if (playingIndex > 0) listState.scrollToItem(playingIndex)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(detail.episodes.size) { index ->
                        val episode = detail.episodes[index]
                        EpisodeCard(
                            episode = episode,
                            meta = "第 ${index + 1} 集",
                            highlight = index == playingIndex,
                            onClick = { onPlay(detail.episodes, index, 0L) },
                            trailing = {
                                DownloadAction(
                                    key = episode.key,
                                    live = cacheStates[episode.key],
                                    persisted = cacheStatuses[episode.key],
                                    onDownload = { onCache(episode) },
                                    onCancel = { onCancel(episode.key) }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * UP 主主页：只展示他名下的公开投稿。
 * 点卡片先进详情看选集（播客 UP 常用合集承载整季），没有 bvid 的异常条目才退化成直接播。
 */
@Composable
private fun UpSpaceContent(
    space: UpSpace,
    cacheStates: Map<String, CacheState>,
    cacheStatuses: Map<String, CacheState.Status>,
    onBack: () -> Unit,
    onCardClick: (Episode) -> Unit,
    onCache: (Episode) -> Unit,
    onCancel: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        BackHandler(onBack = onBack)

        Text(
            text = "←  返回",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text(
                text = space.name.ifBlank { "这个 UP 主" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (space.loading) "正在加载投稿" else "已加载 ${space.videos.size} 条投稿",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }

        when {
            space.loading -> ShimmerEpisodeList(rows = 4)
            space.videos.isEmpty() -> if (space.error != null) {
                // 失败态：报错就说得明白点，并给一个能自救的入口（412 这类前置风控等几秒通常就放行）
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = space.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 36.dp)
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "重新加载",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onRetry)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            } else {
                EmptyHint(
                    title = "这个 UP 主还没有可看的投稿",
                    subtitle = "可能只发了动态或音频区内容"
                )
            }
            else -> LazyColumn(
                // 必须是 weight(1f)：fillMaxSize() 会让列表按整屏高度测量、顶部压到标题下面且底部溢出屏幕，
                // 表现是列表根本滑不动
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(space.videos.size) { index ->
                    val episode = space.videos[index]
                    EpisodeCard(
                        episode = episode,
                        onClick = { onCardClick(episode) },
                        trailing = {
                            DownloadAction(
                                key = episode.key,
                                live = cacheStates[episode.key],
                                persisted = cacheStatuses[episode.key],
                                onDownload = { onCache(episode) },
                                onCancel = { onCancel(episode.key) }
                            )
                        }
                    )
                }

                if (space.hasMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (space.loadingMore) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            } else {
                                Text(
                                    text = "加载更多",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(onClick = onLoadMore)
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                    // 滚到底就自动拉下一页；但只自动补一页，
                    // 否则停在底部会连续拉取几十页，既耗流量又容易把 space 接口打成 412
                    item {
                        LaunchedEffect(space.videos.size) {
                            if (space.videos.isNotEmpty() && space.page < AUTO_LOAD_MAX_PAGE) {
                                onLoadMore()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 通用二次确认浮层（清空历史用）。
 * 与退出登录那个同款：无阴影、20dp 圆角，只有标题、说明和两个动作。
 */
@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "取消",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = confirmText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onConfirm)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * 退出登录确认弹层。
 * 无阴影、无描边，只有一块 20dp 圆角的浮层和两行文字，避免破坏阅读向的观感。
 */
@Composable
private fun LogoutConfirmDialog(
    userName: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    text = "退出登录",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = buildString {
                        val name = userName?.takeIf { it.isNotBlank() }
                        if (name != null) append(name).append("  ·  ")
                        append("退出后仅清除本机登录凭据，已缓存的音频和本地播放记录都会保留。")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "取消",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "退出登录",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onConfirm)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * 问题反馈。
 * 这个客户端没有工单后端，与其挂一个"提交"按钮发不出去，不如把开发者真正需要的东西摆出来：
 * 版本号、包名、机型、系统版本，一键复制。反馈时带上这些，比只写一句"用不了"有用得多。
 * 版本号从 PackageManager 读，不依赖 BuildConfig（AGP 8 默认关掉了 buildConfig）。
 */
@Composable
private fun FeedbackDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "未知" }
    }
    val diagnostics = remember(versionName) {
        buildString {
            append("BiliPod ").append(versionName).append(" · ").append(context.packageName).append('\n')
            append(Build.MANUFACTURER.replaceFirstChar { it.uppercase() })
            append(' ').append(Build.MODEL)
            append(" · Android ").append(Build.VERSION.RELEASE)
            append(" (API ").append(Build.VERSION.SDK_INT).append(')')
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    text = "问题反馈",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "这里是个人项目，没有在线工单。把下面的信息和复现步骤一起发给开发者，比只说一句\"用不了\"更容易定位问题。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = diagnostics,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "关闭",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (copied) "已复制" else "复制诊断信息",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                // 直接用系统剪贴板，不用 LocalClipboardManager（各 Compose 版本 API 不一致）
                                context.getSystemService(ClipboardManager::class.java)
                                    ?.setPrimaryClip(ClipData.newPlainText("BiliPod诊断信息", diagnostics))
                                copied = true
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
