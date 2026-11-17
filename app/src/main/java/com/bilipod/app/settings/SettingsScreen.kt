package com.bilipod.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bilipod.app.common.Formatters
import com.bilipod.app.common.ui.QuietChip

/**
 * 设置页：缓存上限、默认倍速、本地历史清理。
 *
 * @param onApplySpeed 改完倍速立刻作用到播放器，不然"设置里改了却没反应"很容易被当成 bug
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onApplySpeed: (Float) -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val maxBytes by vm.maxBytes.collectAsStateWithLifecycle()
    val defaultSpeed by vm.defaultSpeed.collectAsStateWithLifecycle()
    val cacheUsage by vm.cacheUsage.collectAsStateWithLifecycle()
    val historyCount by vm.historyCount.collectAsStateWithLifecycle()
    val syncHistory by vm.syncHistory.collectAsStateWithLifecycle()
    val syncFavorites by vm.syncFavorites.collectAsStateWithLifecycle()

    var showClearHistoryConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
        }

        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Text(
            text = "缓存上限",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )
        Text(
            text = "已用 ${Formatters.size(cacheUsage)}  ·  超出上限会自动删除最久未播放的缓存",
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

        Spacer(Modifier.height(8.dp))

        Text(
            text = "默认倍速",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )
        Text(
            text = "新打开的播放器用这个速度，播放中仍可在播放页临时切换",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        LazyRow(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(SPEED_OPTIONS.size) { index ->
                val option = SPEED_OPTIONS[index]
                QuietChip(
                    text = formatSpeed(option) + "x",
                    selected = kotlin.math.abs(defaultSpeed - option) < 0.01f,
                    onClick = {
                        vm.setDefaultSpeed(option)
                        onApplySpeed(option)
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "与 B 站同步",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )
        Text(
            text = "选“仅本机”就只读本地记录，不拉取也不显示 B 站账号里的内容（不影响你在 B 站网页端的数据）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        SyncRow(
            title = "收听历史",
            enabled = syncHistory,
            onChange = vm::setSyncHistory
        )
        SyncRow(
            title = "我的收藏",
            enabled = syncFavorites,
            onChange = vm::setSyncFavorites
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "数据",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "本地播放历史",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$historyCount 条记录  ·  只影响本地断点与首页历史，不动 B 站账号数据",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "清空",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (historyCount > 0) 0.85f else 0.35f),
                    modifier = Modifier.clickable(enabled = historyCount > 0) {
                        showClearHistoryConfirm = true
                    }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "播放队列会在重启后恢复（恢复后停在原位等你自己点播放，不会偷偷开始下载直链）。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }

    if (showClearHistoryConfirm) {
        ConfirmDialog(
            title = "清空本地播放历史",
            message = "将删除 $historyCount 条记录，断点续播位置一并丢失，收藏与缓存不受影响。",
            onDismiss = { showClearHistoryConfirm = false },
            onConfirm = {
                showClearHistoryConfirm = false
                vm.clearLocalHistory()
            }
        )
    }
}

/** 一行"标题 + 两个档位"，和缓存上限那排 chip 同一套视觉 */
@Composable
private fun SyncRow(
    title: String,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        QuietChip(
            text = "本机 + B 站",
            selected = enabled,
            onClick = { onChange(true) }
        )
        Spacer(Modifier.width(8.dp))
        QuietChip(
            text = "仅本机",
            selected = !enabled,
            onClick = { onChange(false) }
        )
    }
}

/** 无阴影浮层，和缓存页的清空确认保持同一套视觉 */
@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "取消",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "清空",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onConfirm)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0').trimEnd('.')

/** 与缓存页同一组档位，避免两处上限选项不一致 */
private val CAPACITY_OPTIONS = listOf(
    512L * 1024 * 1024,
    1024L * 1024 * 1024,
    2L * 1024 * 1024 * 1024,
    4L * 1024 * 1024 * 1024
)

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f)
