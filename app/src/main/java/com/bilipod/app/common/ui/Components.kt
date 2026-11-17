package com.bilipod.app.common.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bilipod.app.R
import com.bilipod.app.common.Formatters
import com.bilipod.app.repository.model.Episode

/**
 * 通用视觉组件：
 * 节目卡片（横向布局、20dp 圆角、内边距 14dp）、安静型标签、骨架屏、空状态。
 * 所有组件默认关闭 shadow，靠留白和层级色区分。
 */

/**
 * 节目行。
 *
 * 2026-11-16 对齐小宇宙：容器从"大卡片"收成紧凑行 —— 封面 76→60dp、内边距 14→12dp、
 * 容器圆角 20→14dp，UP 名与时长/进度合并成一行。小宇宙的列表是靠"字号层级 + 留白"
 * 撑起密度的，不适合每行都套一个明显的大圆角容器。
 */
@Composable
fun EpisodeCard(
    episode: Episode,
    modifier: Modifier = Modifier,
    meta: String? = null,
    /** 当前正在播放的那一集：极淡主色底 + 左侧细竖线，不用高饱和强调色 */
    highlight: Boolean = false,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (highlight) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (highlight) {
                Box(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .width(3.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                )
            }
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                BiliImage(url = episode.cover)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // 小宇宙是"标题两行 + 一行元信息"，UP 名不单独占一行，省出来的高度给留白
                val metaText = buildList {
                    episode.upName?.takeIf { it.isNotBlank() }?.let { add(it) }
                    meta?.takeIf { it.isNotBlank() }?.let { add(it) }
                    if (episode.durationSec > 0L) add(Formatters.duration(episode.durationSec))
                }.joinToString("  ·  ")
                if (metaText.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            trailing?.let {
                Spacer(Modifier.width(4.dp))
                it()
            }
        }
    }
}

/** 安静型标签：无高饱和填充，选中时只有极淡的主色底 */
@Composable
fun QuietChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    } else {
        Color.Transparent
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}

/** 轻量页签：选中靠字重和一条 2dp 细线，不用高亮色块 */
@Composable
fun QuietTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = if (selected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            }
        )
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    else Color.Transparent
                )
        )
    }
}

/** 空页面：简约图文提示，不放大插图 */
@Composable
fun EmptyHint(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
        subtitle?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 发现流里的单条卡片（对齐小宇宙"为你推荐"列表）。
 *
 * 小宇宙那一版的构成很固定：左边方封面、右边四行文字（播主 / 标题 / 一句摘要 / 元信息），
 * 最右侧一个实心圆播放键。所以这里也不套卡片容器、不画分割线，整行靠上下留白隔开。
 */
@Composable
fun DiscoveryCard(
    episode: Episode,
    channel: String?,
    summary: String?,
    meta: String,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(10.dp))
        ) {
            BiliImage(url = episode.cover)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (!channel.isNullOrBlank()) {
                Text(
                    text = channel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
            }
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            // 摘要和元信息压在同一个灰度层级、并成一行：小宇宙只有一行灰字，不铺第二段
            val hint = buildString {
                summary?.takeIf { it.isNotBlank() }?.let { append(it) }
                if (meta.isNotBlank()) {
                    if (isNotEmpty()) append("  ·  ")
                    append(meta)
                }
            }
            if (hint.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        // 播放键：实心主色圆 + 白色三角。小宇宙这个按钮是页面上最"实"的一块，不能做成描边
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "播放",
                modifier = Modifier.size(24.dp),
                tint = Color.White
            )
        }
    }
}

/**
 * 发现页顶部的大卡。
 * 小宇宙那个位置是运营位；我们没有运营内容，就用"当前榜单第一名"顶上，
 * 点整卡进详情，点"点击收听"直接起播 —— 位置一样、信息密度一样，但不编假活动。
 */
@Composable
fun FeaturedBanner(
    episode: Episode,
    tag: String,
    upName: String?,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 142dp 时内容比卡片高 8dp，底部那颗"点击收听"被圆角裁掉半截；
            // 按实际内容（标签 16 + 间距 8 + 标题两行 48 + 间距 6 + UP 名 16 + 间距 8 + 胶囊 28）
            // 撑到 158dp 才留得住它
            .height(158.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)
        ) {
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = upName.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickable(onClick = onPlay)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "点击收听",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Box(
            modifier = Modifier
                .width(122.dp)
                .fillMaxHeight()
                // 封面在卡片右边缘，不裁的话右上角是直角、戳出卡片的圆角
                .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
        ) {
            BiliImage(url = episode.cover)
        }
    }
}

/** 分类 chips：浅底胶囊，选中转成主色描字，和小宇宙首页那一排一致 */
@Composable
fun CategoryChip(
    text: String,
    selected: Boolean,
    leadingIcon: ImageVector? = null,
    onClick: () -> Unit
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1
        )
    }
}

/**
 * 顶栏右上角的品牌标识：BiliPod 螺旋（来自 BiliPod.ico，透明底）。
 * 高度固定 32dp，和左边"发现"大标题（32sp、lineHeight 32sp）等高对齐；
 * 原图 507×544 不是正方形，用 ContentScale.Fit 保持螺旋不变形。
 */
@Composable
fun PlanetMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.brand_mark),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(width = 30.dp, height = 32.dp)
    )
}

/**
 * 底部三档导航（发现 / 我的播客 / 个人）。
 *
 * 对齐小宇宙：图标 + 文字，选中态只是换成主色，不做底部指示条、不做胶囊，
 * 也不加阴影和分隔线，让它和页面底色连成一片。
 */
@Composable
fun BottomTabBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        "发现" to Icons.Outlined.Explore,
        "我的播客" to Icons.Outlined.LibraryMusic,
        "个人" to Icons.Outlined.Person
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 避让系统手势条：不抬起来的话导航文字会和底部导航条重叠
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, (label, icon) ->
            val active = selected == index
            val tint = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onSelect(index) }
                    .padding(horizontal = 20.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.height(3.dp))
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = tint)
            }
        }
    }
}

/**
 * 个人页菜单行：左图标 + 标题（+ 可选副文案）+ 右箭头。
 * 和小宇宙个人页一致：行之间靠留白分开，不套卡片、不画分割线。
 */
@Composable
fun ProfileRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            )
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 骨架屏列表：柔和渐变、慢速循环，避免闪烁感 */
@Composable
fun ShimmerEpisodeList(
    modifier: Modifier = Modifier,
    rows: Int = 6
) {
    val brush = shimmerBrush()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(rows) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(brush)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .height(13.dp)
                                .clip(RoundedCornerShape(100.dp))
                                .background(brush)
                        )
                        Spacer(Modifier.height(9.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.38f)
                                .height(9.dp)
                                .clip(RoundedCornerShape(100.dp))
                                .background(brush)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeletonProgress"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x = progress * 900f - 300f, y = 0f),
        end = Offset(x = progress * 900f, y = 240f)
    )
}
