package com.bilipod.app.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.bilipod.app.repository.model.Episode
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 播放器对 UI 暴露的状态快照 */
data class PlayerUiState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val currentKey: String? = null,
    /** 播放队列内索引；-1 表示没有队列。详情页靠它精确高亮当前集 */
    val currentIndex: Int = -1,
    /** 队列总长度，用来判断"这个队列是否就是详情页的选集列表" */
    val queueSize: Int = 0,
    val title: String? = null,
    val cover: String? = null,
    val upName: String? = null,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val error: String? = null
)

/**
 * MediaController 封装层。
 * UI 只跟这个类打交道，不直接持有 ExoPlayer / Service，
 * 所有状态通过 StateFlow 抛给 Compose，避免 callback 满天飞。
 */
class PlayerConnection(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private var tickerJob: Job? = null

    /** 未连接时暂存待播放内容，连接成功后自动开始 */
    private var pending: Pair<List<Episode>, Int>? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = syncState()

        override fun onPlaybackStateChanged(playbackState: Int) = syncState()

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = syncState()

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            syncState()
        }

        override fun onPlayerError(error: PlaybackException) {
            // 交给 UI 展示；用户点"重试"会重新走一次地址解析
            _state.update { it.copy(error = "播放失败：${error.message}") }
        }
    }

    fun connect() {
        if (controller != null || future != null) return
        val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
        val mediaFuture = MediaController.Builder(context, token).buildAsync()
        future = mediaFuture
        mediaFuture.addListener({
            try {
                val c = mediaFuture.get()
                controller = c
                c.addListener(listener)
                _state.update { it.copy(connected = true, error = null) }
                syncState()
                startTicker()
                pending?.let { (episodes, index) ->
                    pending = null
                    play(episodes, index)
                }
            } catch (t: Throwable) {
                _state.update { it.copy(error = "无法连接播放服务：${t.message}") }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun play(episodes: List<Episode>, startIndex: Int, startPositionMs: Long = 0L) {
        if (episodes.isEmpty()) return
        val c = controller
        if (c == null) {
            pending = episodes to startIndex
            connect()
            return
        }
        val index = startIndex.coerceIn(0, episodes.lastIndex)
        c.setMediaItems(episodes.map { it.toMediaItem() }, index, startPositionMs)
        c.prepare()
        c.playWhenReady = true
        // 记下这次队列，进程被杀/重启后由 PlayerService 恢复
        QueueStore.save(context, episodes, index)
        syncState()
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
        syncState()
    }

    fun pause() {
        controller?.pause()
        syncState()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
        syncState()
    }

    fun setSpeed(speed: Float) {
        // 0.5x ~ 3x，超出范围直接裁剪，避免 Media3 抛 IllegalArgumentException
        controller?.setPlaybackSpeed(speed.coerceIn(0.5f, 3f))
        syncState()
    }

    fun next() {
        controller?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem()
        syncState()
    }

    fun previous() {
        controller?.takeIf { it.hasPreviousMediaItem() }?.seekToPreviousMediaItem()
        syncState()
    }

    /** 出错后重新解析并重试当前集 */
    fun retry() {
        val c = controller ?: return
        _state.update { it.copy(error = null) }
        c.prepare()
    }

    fun release() {
        tickerJob?.cancel()
        controller?.release()
        controller = null
        future = null
        scope.cancel()
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                val c = controller
                if (c != null && c.isPlaying) {
                    _state.update {
                        it.copy(
                            positionMs = c.currentPosition.coerceAtLeast(0L),
                            durationMs = safeDuration(c)
                        )
                    }
                }
                delay(500)
            }
        }
    }

    private fun syncState() {
        val c = controller ?: return
        val item = c.currentMediaItem
        _state.update {
            it.copy(
                connected = true,
                isPlaying = c.isPlaying,
                positionMs = c.currentPosition.coerceAtLeast(0L),
                durationMs = safeDuration(c),
                speed = c.playbackParameters.speed,
                currentKey = item?.mediaId,
                currentIndex = c.currentMediaItemIndex,
                queueSize = c.mediaItemCount,
                title = item?.mediaMetadata?.title?.toString(),
                cover = item?.mediaMetadata?.artworkUri?.toString(),
                upName = item?.mediaMetadata?.artist?.toString(),
                hasPrevious = c.hasPreviousMediaItem(),
                hasNext = c.hasNextMediaItem(),
                error = null
            )
        }
    }

    private fun safeDuration(player: Player): Long {
        val duration = player.duration
        return if (duration == C.TIME_UNSET || duration < 0L) 0L else duration
    }
}
