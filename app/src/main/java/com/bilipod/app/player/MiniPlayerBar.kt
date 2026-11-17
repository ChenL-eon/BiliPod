package com.bilipod.app.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bilipod.app.common.Formatters
import com.bilipod.app.common.ui.BiliImage

/**
 * 底部迷你播放条。
 *
 * 小宇宙那一版是"浮在底栏上方的圆角卡片"：左边方封面、中间两行（标题 / UP 名 + 进度）、
 * 右边一个带环形进度的实心圆按钮。这里照这个结构做，只在描边和圆角上留自己的调子。
 */
@Composable
fun MiniPlayerBar(
    state: PlayerUiState,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fraction = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
    val progressColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        // 页面底色和卡片色只差一档，靠这条极淡描边把边界说清楚，不用阴影
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                BiliImage(url = state.cover)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                // 恢复的队列在准备完成前 duration 还是 0，
                // 这时显示 "00:00 / 00:00" 很怪，退回播放状态文案
                val second = if (state.durationMs > 0L) {
                    buildString {
                        state.upName?.takeIf { it.isNotBlank() }?.let { append(it).append("  ·  ") }
                        append(Formatters.durationMs(state.positionMs))
                        append(" / ")
                        append(Formatters.durationMs(state.durationMs))
                    }
                } else {
                    buildString {
                        state.upName?.takeIf { it.isNotBlank() }?.let { append(it).append("  ·  ") }
                        append(if (state.isPlaying) "正在播放" else "已暂停")
                    }
                }
                Text(
                    text = second,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            // 播放键：外圈是进度环，内圈是主色三角/暂停，和小宇宙右下那个按钮同构。
            // 环直接用 M3 的 CircularProgressIndicator，不再自绘 Canvas。
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxSize(),
                    color = progressColor,
                    trackColor = trackColor,
                    strokeWidth = 2.2.dp,
                    strokeCap = StrokeCap.Round
                )
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
