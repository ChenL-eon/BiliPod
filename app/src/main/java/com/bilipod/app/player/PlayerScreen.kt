package com.bilipod.app.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bilipod.app.cache.CacheState
import com.bilipod.app.common.Constants
import com.bilipod.app.common.Formatters
import com.bilipod.app.common.ui.BiliImage
import com.bilipod.app.repository.model.Episode
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 播放页专用的一组颜色。
 *
 * 这一页是"封面模糊铺底 + 压暗"的沉浸式版式，用不了 Theme 里那套浅色语义色，
 * 所以单独定一份；取的全是同一组品牌色在暗底上的版本（青绿用 BrandDark）。
 */
private val PlayerInk = Color(0xFFFFFFFF)
private val PlayerInkDim = Color(0xFFFFFFFF).copy(alpha = 0.72f)
private val PlayerInkFaint = Color(0xFFFFFFFF).copy(alpha = 0.48f)
private val PlayerAccent = Color(0xFF5FC8FF)
private val PlayerFallbackBg = Color(0xFF131616)

/**
 * 全屏播放器浮层。
 *
 * 版式按小宇宙播放页：整页背景是当前封面放大后的模糊图，压一层自上而下加深的黑遮罩，
 * 内容浮在上面——顶栏（收起 / 队列位置 / 定时 / 收藏）、方形封面、标题、播客名与收藏胶囊、
 * 波形进度、控制行（倍速 / -15s / 播放 / +30s / 缓存）。
 *
 * 封面模糊用 `Modifier.blur`；系统低于 Android 12 时这个修饰符不生效，
 * 靠那层黑遮罩把背景压下去，仍然能读，不会出现一块刺眼的清晰大图。
 */
@Composable
fun PlayerOverlay(
    visible: Boolean,
    vm: PlayerViewModel,
    onCollapse: () -> Unit
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val favorited by vm.favorited.collectAsStateWithLifecycle()
    val liked by vm.liked.collectAsStateWithLifecycle()
    val coined by vm.coined.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val sleepAtEnd by vm.sleepAtEnd.collectAsStateWithLifecycle()
    val sleepRemaining by vm.sleepRemainingMs.collectAsStateWithLifecycle()
    val speedOnlyCurrent by vm.speedOnlyCurrent.collectAsStateWithLifecycle()
    val playing by vm.currentEpisode.collectAsStateWithLifecycle()
    val cacheStates by vm.cacheStates.collectAsStateWithLifecycle()
    val cacheStatuses by vm.cacheStatuses.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val collapseThreshold = with(LocalDensity.current) { 140.dp.toPx() }

    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showCoinSheet by remember { mutableStateOf(false) }

    // 背景是深色模糊封面，状态栏图标得跟着反白，不然黑图标压在这层上根本看不见
    val view = LocalView.current
    DisposableEffect(visible) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (visible) controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = true }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = tween(280)) { it } + fadeIn(tween(220)),
        exit = slideOutVertically(animationSpec = tween(240)) { it } + fadeOut(tween(180))
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, dragOffset.value.roundToInt()) },
            color = PlayerFallbackBg,
            tonalElevation = 0.dp,
            shadowElevation = 4.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                CoverBackdrop(state.cover)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        if (dragOffset.value > collapseThreshold) {
                                            onCollapse()
                                            dragOffset.snapTo(0f)
                                        } else {
                                            dragOffset.animateTo(0f, tween(260))
                                        }
                                    }
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    scope.launch {
                                        dragOffset.snapTo(
                                            (dragOffset.value + dragAmount).coerceAtLeast(0f)
                                        )
                                    }
                                }
                            )
                        }
                        .padding(horizontal = 24.dp)
                ) {
                    // ---------- 顶部一行 ----------
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onCollapse, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "收起播放器",
                                tint = PlayerInkDim
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        // 参考图中间那个"28 人在听"胶囊，这里换成真实数据：队列位置
                        if (state.queueSize > 0 && state.currentIndex >= 0) {
                            QueuePill(
                                text = if (state.queueSize > 1) {
                                    "第 ${state.currentIndex + 1} / ${state.queueSize} 集"
                                } else {
                                    "单集"
                                }
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        // 投币：B 站真实接口，投过之后图标变青；定时入口收进底部齿轮里的设置面板
                        IconButton(
                            onClick = { showCoinSheet = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            CoinIcon(
                                tint = if (coined) PlayerAccent else PlayerInkDim,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(
                            onClick = { vm.toggleFavorite() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                // 收藏用星形（B 站客户端也是这颗星），别再用爱心——
                                // 爱心是"喜欢/点赞"的符号，和点赞按钮放一起会读重
                                imageVector = if (favorited) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (favorited) "取消收藏" else "收藏单集",
                                tint = if (favorited) PlayerAccent else PlayerInkDim
                            )
                        }
                    }

                    Spacer(Modifier.weight(0.28f))

                    // 封面比参考图的比例略大一点：我们标题短、没有简介行
                    Box(
                        modifier = Modifier
                            .size(196.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .align(Alignment.CenterHorizontally)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        BiliImage(url = state.cover)
                    }

                    Spacer(Modifier.height(26.dp))

                    Text(
                        text = state.title ?: "未在播放",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 30.sp
                        ),
                        color = PlayerInk,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(12.dp))

                    // 播客名 + 收藏胶囊（参考图那里是"+ 订阅"，这里换成真实存在的收藏）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.upName.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = PlayerAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.upName.orEmpty().isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            FollowPill(
                                favorited = favorited,
                                onClick = { vm.toggleFavorite() }
                            )
                        }
                    }

                    playing?.let { episode ->
                        Spacer(Modifier.height(8.dp))
                        CacheStateChip(
                            episode = episode,
                            live = cacheStates.forEpisode(episode),
                            persisted = cacheStatuses.forEpisode(episode),
                            onDownload = { vm.cacheCurrent() },
                            onCancel = { vm.cancelCache(episode.key) },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    // 三段弹性留白：顶部 0.28 / 中段 0.34 / 底部 0.38，
                    // 让封面下移、进度条离开屏幕最下沿，整体比例贴近参考图
                    Spacer(Modifier.weight(0.34f))

                    // ---------- 进度 ----------
                    val durationMs = state.durationMs.takeIf { it > 0L } ?: 0L
                    val progress = when {
                        dragging -> dragValue
                        durationMs > 0L -> (state.positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                        else -> 0f
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val shown = if (dragging && durationMs > 0L) {
                            (dragValue * durationMs).toLong()
                        } else {
                            state.positionMs
                        }
                        Text(
                            text = Formatters.durationMs(shown),
                            style = MaterialTheme.typography.labelMedium,
                            color = PlayerInkDim
                        )
                        Text(
                            text = if (durationMs > 0L) {
                                "-${Formatters.durationMs((durationMs - shown).coerceAtLeast(0L))}"
                            } else {
                                "--:--"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = PlayerInkFaint
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    WaveformProgress(
                        progress = progress,
                        enabled = durationMs > 0L,
                        onScrub = { value ->
                            dragging = true
                            dragValue = value
                        },
                        onScrubEnd = {
                            if (durationMs > 0L) vm.seekTo((dragValue * durationMs).toLong())
                            dragging = false
                        }
                    )

                    Spacer(Modifier.height(18.dp))

                    // ---------- 控制区 ----------
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左端：设置（倍速 / 睡眠都在里面）
                        SettingsButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.weight(1f)
                        )
                        SeekButton(
                            seconds = 15,
                            forward = false,
                            enabled = durationMs > 0L,
                            onClick = { vm.seekTo((state.positionMs - 15_000L).coerceAtLeast(0L)) }
                        )
                        Spacer(Modifier.width(22.dp))
                        PlayButton(
                            connected = state.connected,
                            isPlaying = state.isPlaying,
                            onClick = { vm.toggle() }
                        )
                        Spacer(Modifier.width(22.dp))
                        SeekButton(
                            seconds = 30,
                            forward = true,
                            enabled = durationMs > 0L,
                            onClick = {
                                val target = state.positionMs + 30_000L
                                vm.seekTo(if (durationMs > 0L) target.coerceAtMost(durationMs) else target)
                            }
                        )
                        // 右端：点赞（走 B 站真实接口，已赞变实心青）
                        LikeButton(
                            liked = liked,
                            onClick = { vm.toggleLike() },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // ---------- 底部提示 ----------
                    val hint = notice ?: sleepRemaining?.let {
                        "还剩 ${Formatters.durationMs(it)} 自动暂停"
                    } ?: if (sleepAtEnd) "本集播完自动暂停" else null
                    if (hint != null) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (notice != null) PlayerAccent else PlayerInkFaint,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    state.error?.let { error ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFE3A79C),
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "重试",
                                style = MaterialTheme.typography.labelMedium,
                                color = PlayerAccent,
                                modifier = Modifier
                                    .clickable { vm.retry() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Spacer(Modifier.weight(0.38f))
                }
            }
        }
    }

    if (showCoinSheet) {
        CoinSheet(
            coined = coined,
            onCoin = { count ->
                vm.coin(count)
                showCoinSheet = false
            },
            onDismiss = { showCoinSheet = false }
        )
    }

    if (showSettingsSheet) {
        PlayerSettingsSheet(
            speed = state.speed,
            sleepRemaining = sleepRemaining,
            sleepAtEnd = sleepAtEnd,
            onlyCurrent = speedOnlyCurrent,
            onSpeedPick = { speed -> vm.setSpeedFromPanel(speed) },
            onOnlyCurrentChange = { vm.setSpeedOnlyCurrent(it) },
            onSleepPick = { minutes -> vm.setSleepTimer(minutes) },
            onSleepAtEnd = { vm.setSleepAtEnd(it) },
            onDismiss = { showSettingsSheet = false }
        )
    }
}

/**
 * 整页背景：当前封面放大后模糊铺满，再压一层自上而下加深的黑遮罩。
 * 遮罩不是装饰——没有它，浅色封面上的白字会彻底看不见。
 */
@Composable
private fun CoverBackdrop(cover: String?) {
    val context = LocalContext.current
    val data = cover?.trim()?.let { raw ->
        when {
            raw.isEmpty() -> null
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("http://") -> raw.replaceFirst("http://", "https://")
            else -> raw
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PlayerFallbackBg)
        )
        if (data != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(data)
                    .setHeader("Referer", Constants.REFERER)
                    .setHeader("User-Agent", Constants.UA)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(42.dp, BlurredEdgeTreatment.Unbounded)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.42f),
                        0.45f to Color.Black.copy(alpha = 0.54f),
                        1f to Color.Black.copy(alpha = 0.68f)
                    )
                )
        )
    }
}

/** 顶部中间那颗胶囊：队列位置。没有队列时干脆不画，避免出现"第 0 / 0 集" */
@Composable
private fun QueuePill(text: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.14f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = PlayerInkDim,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** 播客名后面那颗胶囊：未收藏是实底 "+ 收藏"，已收藏换成半透明底"已收藏" */
@Composable
private fun FollowPill(favorited: Boolean, onClick: () -> Unit) {
    val bg = if (favorited) Color.White.copy(alpha = 0.16f) else PlayerAccent
    val fg = if (favorited) PlayerInk else Color(0xFF002B3F)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = bg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Text(
            text = if (favorited) "已收藏" else "+ 收藏",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

/** 波形里那根白色进度光标的粗细 */
private val CURSOR_WIDTH = 2.dp

/**
 * 波形进度条。
 *
 * 形制照参考图：整条波形装在一个圆角矩形容器里（半透明白底），
 * 已播的柱子是亮青、未播的是暗灰，进度位置竖一根白色细光标贯穿容器高度。
 * 竖条高度用固定函数生成，同一集每次重绘都一样（不能是随机数，否则每秒都在跳）。
 * 点一下定位，按住横向拖也能拖。
 */
@Composable
private fun WaveformProgress(
    progress: Float,
    enabled: Boolean,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            // pointerInput 挂在 padding 之后，拿到的坐标就是波形区的坐标，不用自己再减边距
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onScrub((offset.x / size.width).coerceIn(0f, 1f))
                    onScrubEnd()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        onScrub((offset.x / size.width).coerceIn(0f, 1f))
                    },
                    onHorizontalDrag = { change, _ ->
                        onScrub((change.position.x / size.width).coerceIn(0f, 1f))
                    },
                    onDragEnd = { onScrubEnd() },
                    onDragCancel = { onScrubEnd() }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val bars = 58
            val gap = size.width / bars
            val barWidth = gap * 0.44f
            val restColor = Color.White.copy(alpha = 0.22f)
            for (i in 0 until bars) {
                val barHeight = size.height * barFraction(i)
                val played = progress * bars > i
                drawRoundRect(
                    color = if (played) PlayerAccent else restColor,
                    topLeft = Offset(i * gap, (size.height - barHeight) / 2),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                )
            }
            // 播放光标：白色细线，贯穿容器高度（参考图里那根竖白线）
            val cursorX = (progress * size.width).coerceIn(0f, size.width)
            drawRect(
                color = Color.White.copy(alpha = 0.9f),
                topLeft = Offset(cursorX - CURSOR_WIDTH.toPx() / 2, 0f),
                size = Size(CURSOR_WIDTH.toPx(), size.height)
            )
        }
    }
}

/** 0.3~1.0 之间的稳定"伪随机"高度，只跟序号有关 */
private fun barFraction(index: Int): Float {
    val raw = abs(kotlin.math.sin(index * 12.9898f + 4.1414f) * 43758.5453f)
    val frac = raw - raw.toInt()
    return 0.3f + frac * 0.7f
}

/**
 * 中央播放键：半透明白圆底 + 实心三角（参考图二那种"圆形浅底 + 纯色图标"的形制）。
 * 底是白色低透明、图标是品牌青，放到封面的模糊底上不会糊成一片。
 */
@Composable
private fun PlayButton(connected: Boolean, isPlaying: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.14f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.size(72.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!connected) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = PlayerAccent
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = PlayerAccent,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
    }
}

/** 自绘的"⟲15 / 30⟳"：一段 300° 圆弧 + 端点箭头 + 中间的数字 */
@Composable
private fun SeekButton(
    seconds: Int,
    forward: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tint = PlayerInk.copy(alpha = if (enabled) 0.8f else 0.25f)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Replay,
            contentDescription = if (forward) "快进 $seconds 秒" else "后退 $seconds 秒",
            tint = tint,
            // icon 库只有逆时针的 Replay，+30s 旋转 180° 就变成顺时针那支箭头
            modifier = Modifier
                .size(42.dp)
                .rotate(if (forward) 180f else 0f)
        )
        Text(
            text = "$seconds",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp
            ),
            color = tint,
            modifier = Modifier.offset(y = (-1.5).dp)
        )
    }
}

/** 投币按钮：直接走 icon 库的硬币图标，投过之后整体变青 */
@Composable
private fun CoinIcon(tint: Color, modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Outlined.Paid,
        contentDescription = "投币",
        tint = tint,
        modifier = modifier
    )
}

/**
 * 投币面板：B 站一集最多投 2 枚，这里给 1 / 2 两个档。
 * 投过之后按钮进禁用态，文案改成"已经投过"，不做成点了没反应的假按钮。
 */
@Composable
private fun CoinSheet(coined: Boolean, onCoin: (Int) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1B1F1F),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "给这一集投币",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = PlayerInk
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (coined) "这一集已经投过币了" else "硬币来自 B 站的日常任务，一集最多两枚",
                    style = MaterialTheme.typography.labelSmall,
                    color = PlayerInkDim
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(1, 2).forEach { count ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (coined) Color.White.copy(alpha = 0.04f)
                                    else Color.White.copy(alpha = 0.10f)
                                )
                                .clickable(enabled = !coined) { onCoin(count) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "投 $count 枚",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (coined) PlayerInkFaint else PlayerInk
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 控制行左端的设置入口：一颗描边齿轮 */
@Composable
private fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "播放设置",
                tint = PlayerInk.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** 控制行右端的点赞：没赞是空心拇指，赞过变实心青。点击直接打 B 站点赞接口 */
@Composable
private fun LikeButton(liked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = if (liked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                contentDescription = if (liked) "取消点赞" else "点赞",
                tint = if (liked) PlayerAccent else PlayerInk.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 播放设置面板：倍速 + 睡眠两块。
 *
 * 版式照小宇宙的播放设置：每块一张深色圆角卡片，标题在左上、当前值在右上（青色），
 * 中间是一条"刻度尺"选择器（选中的那格竖线更高、变青），下面一行是开关。
 * 参考图里的"跳过空白""人声加强"没有对应能力，不做。
 */
@Composable
private fun PlayerSettingsSheet(
    speed: Float,
    sleepRemaining: Long?,
    sleepAtEnd: Boolean,
    onlyCurrent: Boolean,
    onSpeedPick: (Float) -> Unit,
    onOnlyCurrentChange: (Boolean) -> Unit,
    onSleepPick: (Int) -> Unit,
    onSleepAtEnd: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val speedIndex = SPEED_OPTIONS.indexOfFirst { abs(it - speed) < 0.01f }.takeIf { it >= 0 } ?: 2
    // 睡眠是倒计时在走的，不能按"毫秒差小于 2 秒"匹配——点完 30 分的下一秒就选不中了。
    // 向上取整成分钟数再对档位，29:58 照样落在 30 那一格。
    val sleepIndex = sleepRemaining?.let { remaining ->
        val minutes = ((remaining + 59_999L) / 60_000L).toInt()
        SLEEP_OPTIONS.indexOf(minutes).let { if (it >= 0) it + 1 else 0 }
    } ?: 0

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1B1F1F),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ---------- 倍速 ----------
                SettingsCard {
                    SettingsCardHeader(
                        title = "倍速播放",
                        value = speedLabel(speed)
                    )
                    Spacer(Modifier.height(10.dp))
                    ScaleSelector(
                        labels = SPEED_OPTIONS.map { speedTag(it) },
                        selectedIndex = speedIndex,
                        onSelect = { onSpeedPick(SPEED_OPTIONS[it]) }
                    )
                    Spacer(Modifier.height(6.dp))
                    SettingsSwitchRow(
                        text = "只应用到当前节目",
                        checked = onlyCurrent,
                        onCheckedChange = onOnlyCurrentChange
                    )
                }

                // ---------- 睡眠定时 ----------
                SettingsCard {
                    SettingsCardHeader(
                        title = "睡眠定时",
                        value = when {
                            sleepRemaining != null -> Formatters.durationMs(sleepRemaining)
                            sleepAtEnd -> "本集结束"
                            else -> "OFF"
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    ScaleSelector(
                        labels = SLEEP_LABELS,
                        selectedIndex = sleepIndex,
                        onSelect = { minutes ->
                            onSleepPick(if (minutes == 0) 0 else SLEEP_OPTIONS[minutes - 1])
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    SettingsSwitchRow(
                        text = "定时到本集结束",
                        checked = sleepAtEnd,
                        onCheckedChange = onSleepAtEnd
                    )
                }
            }
        }
    }
}

/** 设置面板里的一张卡片：深色圆角底 + 内边距 */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF262B2B))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        content = content
    )
}

/** 卡片标题行：左边名字，右边当前值（青色） */
@Composable
private fun SettingsCardHeader(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = PlayerInk,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = PlayerAccent
        )
    }
}

/** 卡片底部的开关行 */
@Composable
private fun SettingsSwitchRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = PlayerInkDim,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.height(28.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF002B3F),
                checkedTrackColor = PlayerAccent,
                uncheckedThumbColor = Color(0xFF8A9494),
                uncheckedTrackColor = Color(0xFF3A4141),
                uncheckedBorderColor = Color(0xFF3A4141)
            )
        )
    }
}

/**
 * "刻度尺"选择器：每格一根竖线 + 下面一个标签。
 * 选中的那根线更高、变青，标签也跟着变青——参考图里倍速和睡眠都是这个形态。
 */
@Composable
private fun ScaleSelector(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .width(if (selected) 2.5.dp else 1.5.dp)
                        .height(if (selected) 26.dp else 16.dp)
                        .background(
                            if (selected) PlayerAccent else PlayerInk.copy(alpha = 0.35f)
                        )
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) PlayerAccent else PlayerInkFaint
                )
            }
        }
    }
}

/** 刻度尺上的标签：整数倍速也带一位小数（1.0），和参考图一致 */
private fun speedTag(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}.0" else speed.toString().trimEnd('0').trimEnd('.')

/** 控制区那颗圆形按钮上的文案：整数倍速也要带一位小数（1.0x），不然只剩个"1x"看着像占位 */
private fun speedLabel(speed: Float): String = speedTag(speed) + "x"

/**
 * 播放页的缓存状态胶囊：未缓存 / 下载中（进度 + 百分比，点击取消）/ 已缓存 / 失败重试。
 * 与列表侧 DownloadAction 同一套状态判定：Room 的 SUCCESS 是最终口径，优先于内存态。
 */
@Composable
private fun CacheStateChip(
    episode: Episode,
    live: CacheState?,
    persisted: CacheState.Status?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = when {
        persisted == CacheState.Status.SUCCESS -> CacheState.Status.SUCCESS
        else -> live?.status ?: persisted ?: CacheState.Status.IDLE
    }
    val running = live != null && live.isRunning && status != CacheState.Status.SUCCESS

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = status != CacheState.Status.SUCCESS) {
                if (running) onCancel() else onDownload()
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = PlayerInk.copy(alpha = 0.58f)
        when {
            running -> {
                val hasTotal = live!!.totalBytes > 0L
                if (hasTotal) {
                    CircularProgressIndicator(
                        progress = { live.progress },
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 1.5.dp,
                        color = PlayerAccent,
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 1.5.dp,
                        color = PlayerAccent,
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        live.status == CacheState.Status.WAITING -> "排队中 · 点击取消"
                        hasTotal -> "${(live.progress * 100).toInt()}% · 点击取消"
                        else -> "${Formatters.size(live.downloadedBytes)} · 点击取消"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = tint
                )
            }

            status == CacheState.Status.SUCCESS -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已缓存",
                    tint = PlayerAccent.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "已缓存",
                    style = MaterialTheme.typography.labelSmall,
                    color = PlayerAccent.copy(alpha = 0.8f)
                )
            }

            status == CacheState.Status.FAILED -> {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "重试下载",
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(text = "下载失败 · 点击重试", style = MaterialTheme.typography.labelSmall, color = tint)
            }

            else -> {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "加入缓存",
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(text = "加入缓存", style = MaterialTheme.typography.labelSmall, color = tint)
            }
        }
    }
}

/**
 * 按 key 精确查；查不到再按 bvid 回退。
 * 原因：搜索/收藏拿到的 key 是 v_{bvid}，而 CacheManager 落库的是 v_{bvid}_{cid}，
 * 播放页此时如果只认精确 key，会一直显示"未缓存"。
 */
private fun <T> Map<String, T>.forEpisode(episode: Episode): T? {
    this[episode.key]?.let { return it }
    // cid 已知时只认这一集。按 bvid 回退是给"key 只到稿件级"的场景兜底，
    // 若在这里无差别回退，910 集的稿件只要有一集缓存，其他集全会显示"已缓存"、还点不了下载。
    val cid = episode.cid
    if (cid != null && cid > 0L) return null
    val bvid = episode.bvid ?: return null
    return entries.firstOrNull { Episode.bvidOfKey(it.key) == bvid }?.value
}

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f)
private val SLEEP_OPTIONS = listOf(15, 30, 45, 60)
/** 睡眠刻度尺第一格是"关"，后面才是分钟数，所以标签单独列一份 */
private val SLEEP_LABELS = listOf("关") + SLEEP_OPTIONS.map { it.toString() }

/** Compose 里的 context 常常是 ContextWrapper，往上剥到 Activity 才能拿到 window */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
