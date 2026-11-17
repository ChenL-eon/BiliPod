package com.bilipod.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bilipod.app.cache.CacheScreen
import com.bilipod.app.common.ui.BiliPodTheme
import com.bilipod.app.common.ui.BottomTabBar
import com.bilipod.app.home.HomeScreen
import com.bilipod.app.home.HomeViewModel
import com.bilipod.app.login.LoginScreen
import com.bilipod.app.player.MiniPlayerBar
import com.bilipod.app.player.PlayerOverlay
import com.bilipod.app.player.PlayerViewModel
import com.bilipod.app.settings.SettingsScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 13+ 通知栏播放控制器需要运行时权限
        requestNotificationPermissionIfNeeded()
        setContent {
            BiliPodTheme {
                AppRoot()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATION)
        }
    }

    private companion object {
        const val REQUEST_NOTIFICATION = 1001
    }
}

/** 极简页面栈：单 Activity + 状态切换，不引入 navigation 依赖 */
private enum class AppScreen { LOGIN, HOME, CACHE, SETTINGS }

/**
 * 全局结构：
 *   [内容区]
 *   [常驻迷你播放条]  ← 全屏播放器展开时仍保留，收起后无缝衔接
 * 全屏播放器是覆盖在内容区之上的浮层，下滑或点箭头收起。
 */
@Composable
private fun AppRoot() {
    // 播放 ViewModel 提到根作用域，保证迷你条与全屏播放器共用同一个播放连接
    val playerVm: PlayerViewModel = viewModel()
    // 首页 ViewModel 也提到根作用域：登录回来要立刻 refresh 一次，个人页与推荐流才认得新账号
    val homeVm: HomeViewModel = viewModel()

    // 首启直接进首页（未登录也能逛），播放时才提示登录，见 HomeScreen 的 play 拦截
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var playerExpanded by remember { mutableStateOf(false) }

    /** 首页三档：0 发现 / 1 我的播客 / 2 个人。放根作用域，底栏和页面共用 */
    var homeTab by remember { mutableIntStateOf(0) }
    /**
     * 点"发现"的次数：底栏每次点它都 +1，HomeScreen 收到变化就重拉一次榜单。
     * 不放在 HomeScreen 里，是因为刷新要由这次点击来触发，而不是 tab 值的变化
     * （已经在发现页时 tab 值不变，光靠它触发不了）。
     */
    var discoverTick by remember { mutableIntStateOf(0) }
    // "我的播客"的二级分栏也提到这里：个人页的"收听历史 / 我的收藏"要一次把一级和二级都切走
    var libraryTab by remember { mutableIntStateOf(0) }

    val playerState by playerVm.uiState.collectAsStateWithLifecycle()

    if (screen == AppScreen.LOGIN) {
        LoginScreen(
            // 登录回来只同步账号态（不重铺骨架屏），昵称、收藏、B 站历史跟着刷新
            onLoggedIn = {
                homeVm.onLoginStateChanged()
                screen = AppScreen.HOME
            },
            onSkip = { screen = AppScreen.HOME }
        )
        return
    }

    BackHandler(enabled = playerExpanded || screen != AppScreen.HOME) {
        if (playerExpanded) {
            playerExpanded = false
        } else {
            screen = AppScreen.HOME
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when (screen) {
                    AppScreen.HOME -> HomeScreen(
                        playerVm = playerVm,
                        vm = homeVm,
                        homeTab = homeTab,
                        libraryTab = libraryTab,
                        onLibraryTabChange = { libraryTab = it },
                        onHomeTabChange = { homeTab = it },
                        discoverTick = discoverTick,
                        onOpenCache = { screen = AppScreen.CACHE },
                        onOpenLogin = { screen = AppScreen.LOGIN },
                        onOpenSettings = { screen = AppScreen.SETTINGS }
                    )

                    AppScreen.CACHE -> CacheScreen(onBack = { screen = AppScreen.HOME })

                    AppScreen.SETTINGS -> SettingsScreen(
                        onBack = { screen = AppScreen.HOME },
                        // 改完默认倍速立刻作用到正在播的播放器
                        onApplySpeed = { playerVm.setSpeed(it) }
                    )

                    AppScreen.LOGIN -> Unit
                }
            }

            // 首页：迷你播放条在底栏之上（对齐小宇宙的"内容 / 播放条 / 三档导航"顺序）
            if (screen == AppScreen.HOME) {
                if (!playerState.currentKey.isNullOrBlank()) {
                    MiniPlayerBar(
                        state = playerState,
                        onToggle = { playerVm.toggle() },
                        onClick = { playerExpanded = true }
                    )
                }
                BottomTabBar(
                    selected = homeTab,
                    onSelect = { tab ->
                        // 只有"本来就停在发现页"时，再点一次发现才算主动刷新；
                        // 从"我的播客 / 个人"切回发现属于普通返回，不该重拉内容
                        if (tab == 0 && homeTab == 0) discoverTick++
                        homeTab = tab
                    }
                )
            } else if (!playerState.currentKey.isNullOrBlank()) {
                // 缓存/设置这类二级页仍然保留迷你条，底栏隐藏。
                // 没有底栏挡着，迷你条要自己避让系统手势条，否则圆角卡会压到导航条上
                MiniPlayerBar(
                    state = playerState,
                    onToggle = { playerVm.toggle() },
                    onClick = { playerExpanded = true },
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }

        PlayerOverlay(
            visible = playerExpanded,
            vm = playerVm,
            onCollapse = { playerExpanded = false }
        )
    }
}
