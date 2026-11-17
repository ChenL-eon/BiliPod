package com.bilipod.app.cache

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bilipod.app.common.Formatters
import com.bilipod.app.common.ui.EmptyHint
import com.bilipod.app.common.ui.QuietChip

/**
 * 缓存管理：容量设置、下载状态、删除。
 * 视觉与首页一致：无阴影卡片、极淡进度线、次要文字降低透明度。
 */
@Composable
fun CacheScreen(
    onBack: () -> Unit,
    vm: CacheViewModel = viewModel()
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val states by vm.states.collectAsStateWithLifecycle()
    val maxBytes by vm.maxBytes.collectAsStateWithLifecycle()

    // 清空是不可恢复操作（会删文件、取消下载任务），先弹一次确认
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 避让状态栏：否则"返回/清空"会被状态栏压住，点击也会错位
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "←  返回",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.clickable(onClick = onBack)
            )
            Spacer(Modifier.weight(1f))
            if (items.isNotEmpty()) {
                Text(
                    text = "清空",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier
                        .clickable { showClearConfirm = true }
                        .padding(4.dp)
                )
            }
        }

        if (showClearConfirm) {
            ClearConfirmDialog(
                count = items.size,
                totalBytes = items.sumOf { it.totalBytes },
                onDismiss = { showClearConfirm = false },
                onConfirm = {
                    showClearConfirm = false
                    vm.clearAll()
                }
            )
        }

        Text(
            text = "缓存",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Text(
            text = "超出上限会自动删除最久未播放的缓存",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        LazyRow(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(CAPACITY_OPTIONS.size) { index ->
                val option = CAPACITY_OPTIONS[index]
                QuietChip(
                    text = Formatters.size(option),
                    selected = maxBytes == option,
                    onClick = { vm.setMaxBytes(option) }
                )
            }
        }

        if (items.isEmpty()) {
            EmptyHint(
                title = "还没有缓存",
                subtitle = "在列表里点下载图标，音频会存到 App 私有目录"
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items.size) { index ->
                val entity = items[index]
                val live = states[entity.episodeKey]
                val status = live?.status ?: runCatching {
                    CacheState.Status.valueOf(entity.status)
                }.getOrDefault(CacheState.Status.IDLE)

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entity.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                val detail = buildList {
                                    add(statusText(status))
                                    if (entity.totalBytes > 0L) add(Formatters.size(entity.totalBytes))
                                    live?.message?.takeIf { it.isNotBlank() }?.let { add(it) }
                                }.joinToString("  ·  ")
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text(
                                text = if (live?.isRunning == true) "取消" else "删除",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .clickable {
                                        if (live?.isRunning == true) {
                                            vm.cancel(entity.episodeKey)
                                        } else {
                                            vm.delete(entity.episodeKey)
                                        }
                                    }
                                    .padding(6.dp)
                            )
                        }

                        if (live?.status == CacheState.Status.DOWNLOADING) {
                            Spacer(Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(live.progress)
                                        .height(2.dp)
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 清空缓存的二次确认。
 * 与退出登录弹层同一套视觉：20dp 圆角、无阴影、两枚文字按钮。
 */
@Composable
private fun ClearConfirmDialog(
    count: Int,
    totalBytes: Long,
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
                    text = "清空全部缓存",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = buildString {
                        append("将删除 $count 项缓存")
                        if (totalBytes > 0L) append("（${Formatters.size(totalBytes)}）")
                        append("，正在下载的任务会被取消。删除后需要重新下载，播放记录和收藏不受影响。")
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
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(
                        text = "清空",
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

private fun statusText(status: CacheState.Status): String = when (status) {
    CacheState.Status.IDLE -> "未下载"
    CacheState.Status.WAITING -> "等待下载"
    CacheState.Status.DOWNLOADING -> "下载中"
    CacheState.Status.SUCCESS -> "已缓存"
    CacheState.Status.FAILED -> "下载失败"
    CacheState.Status.CANCELED -> "已取消"
}

private val CAPACITY_OPTIONS = listOf(
    512L * 1024 * 1024,
    1L * 1024 * 1024 * 1024,
    2L * 1024 * 1024 * 1024,
    3L * 1024 * 1024 * 1024
)
