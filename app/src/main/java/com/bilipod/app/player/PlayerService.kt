package com.bilipod.app.player

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.bilipod.app.common.AppResult
import com.bilipod.app.common.Constants
import com.bilipod.app.database.entity.PlayHistoryEntity
import com.bilipod.app.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * 播放前台服务。
 *
 * 职责：
 * 1. 持有 ExoPlayer 与 MediaSession，通知栏/锁屏控制器由 Media3 自动生成（播放/暂停/上一集/下一集）；
 * 2. 音频焦点：setAudioAttributes(handleAudioFocus = true)，来电或其他 App 播放时自动暂停；
 * 3. 耳机拔出：setHandleAudioBecomingNoisy(true) 自动暂停；
 * 4. 播放地址实时解析：MediaItem 里是 bili:// 伪协议，真正开流前才换成真实地址，
 *    顺带优先命中本地缓存；
 * 5. 每 5 秒落一次播放进度到 Room，实现断点续播。
 *
 * 风险点：CDN 地址带时效 token，解析失败/403 时不要在这里做无限重试，
 * 交给 Media3 的 LoadErrorHandlingPolicy 有限重试，并在 UI 提示"链接已过期，重新播放"。
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 落库专用作用域：不随 Service 销毁取消，保证最后一次进度不会丢 */
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var progressJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // 暂停/切集时立刻保存进度，避免只依赖 5 秒轮询
            if (!isPlaying) persistProgress()
            // 正常播起来了，说明这次的直链有效，允许下一次过期再重试
            if (isPlaying) expiredRetryKey = null
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            expiredRetryKey = null
            // 切集就更新队列游标，重启后能回到"上次播的那一集"
            QueueStore.saveIndex(this@PlayerService, player.currentMediaItemIndex)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) persistProgress()
        }

        override fun onPlayerError(error: PlaybackException) {
            // 常见：403（直链过期 / Referer 校验失败）、网络中断
            val code = httpStatusCode(error)
            val key = player.currentMediaItem?.mediaId
            Log.w(TAG, "播放错误 code=${error.errorCode} http=$code msg=${error.message}", error)

            // 直链过期：刷新 wbi 密钥 → 重新 prepare → seek 回原位置，同一集只自动恢复一次，
            // 避免在真正的网络故障上反复重试（注释里的风险点也写了：不做无限重试）
            if ((code == 403 || code == 410) && key != null && expiredRetryKey != key) {
                expiredRetryKey = key
                Log.w(TAG, "直链过期 HTTP $code，刷新 wbi 密钥并重解析一次 key=$key")
                ServiceLocator.playUrlRepository.onForbidden()
                val index = player.currentMediaItemIndex
                val position = player.currentPosition.coerceAtLeast(0L)
                player.prepare()
                player.seekTo(index, position)
                player.playWhenReady = true
            }
        }
    }

    /** 上一次因 403/410 自动重解析过的 episodeKey，null 表示可以再试 */
    private var expiredRetryKey: String? = null

    /** 从 PlaybackException 里挖出 HTTP 状态码（ExoPlayer 会把它包在 cause 链里） */
    private fun httpStatusCode(error: Throwable?): Int? {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode
            cause = cause.cause
        }
        return null
    }

    override fun onCreate() {
        super.onCreate()
        player = buildPlayer()
        player.addListener(playerListener)
        mediaSession = MediaSession.Builder(this, player)
            // 默认 BitmapLoader 不带 Referer，B 站封面会 403，通知/锁屏只剩占位图标
            .setBitmapLoader(BiliBitmapLoader(ServiceLocator.apiClient.httpClient))
            .build()
        restoreQueue()
        startProgressLoop()
    }

    /**
     * 恢复上次的播放队列。
     *
     * 只 setMediaItems + 定位，不 prepare：恢复后停在 STATE_IDLE，
     * 等用户真的点播放才去解析直链，避免"刚打开 App 就发一次 playurl 请求"。
     * 上一集/下一集、迷你条标题封面、详情页高亮都靠这份队列恢复。
     */
    private fun restoreQueue() {
        if (player.mediaItemCount > 0) return
        val saved = QueueStore.load(this) ?: return
        player.setMediaItems(
            saved.items.map { it.toMediaItem() },
            saved.index,
            0L
        )
        Log.i(TAG, "已恢复播放队列 ${saved.items.size} 集，定位第 ${saved.index + 1} 集")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户划掉任务：没在播放就彻底关掉服务，避免常驻前台通知
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // 先存一次进度，再释放资源
        persistProgress()
        progressJob?.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun buildPlayer(): ExoPlayer {
        val headers = ServiceLocator.playUrlRepository.playbackHeaders()

        val httpFactory = DefaultHttpDataSource.Factory()
            // 媒体流用桌面 UA：playurl 按 platform=pc 签发，UA 不一致会 403
            .setUserAgent(Constants.MEDIA_UA)
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)

        val baseFactory = DefaultDataSource.Factory(this, httpFactory)

        val resolver = object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: DataSpec): DataSpec = resolve(dataSpec)
        }
        val resolvingFactory = ResolvingDataSource.Factory(baseFactory, resolver)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            // 第二个参数 true = 交给 ExoPlayer 处理音频焦点（来电/其他 App 播放自动暂停）
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    /**
     * 把 bili:// 伪协议换成真实可播放的地址。
     * 该方法运行在 ExoPlayer 的加载线程，不是主线程，因此用 runBlocking 同步解析是安全的。
     */
    private fun resolve(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != Constants.PLAY_SCHEME) return dataSpec

        val episode = uri.toEpisodeOrNull() ?: return dataSpec

        // 1) 本地缓存优先
        ServiceLocator.cacheManager.cachedFile(episode.key)?.let { file ->
            ServiceLocator.cacheManager.touch(episode.key)
            return dataSpec.withUri(Uri.fromFile(file))
        }

        // 2) 无缓存：实时解析网络地址。
        //    解析失败往往是 wbi 密钥过期或被风控，这里刷新一次密钥后只重解析一次，
        //    仍然失败就抛出去交给 ExoPlayer，不在解析层做无上限重试。
        var result = runBlocking { ServiceLocator.playUrlRepository.resolveAudioUrl(episode) }
        if (result !is AppResult.Success) {
            Log.w(TAG, "解析失败，刷新 wbi 密钥后重试一次 key=${episode.key}")
            ServiceLocator.playUrlRepository.onForbidden()
            result = runBlocking { ServiceLocator.playUrlRepository.resolveAudioUrl(episode) }
        }
        return when (result) {
            is AppResult.Success -> dataSpec.withUri(Uri.parse(result.data.url))
            is AppResult.Failure -> throw IOException("音频地址解析失败：${result.message}")
            is AppResult.Error -> throw IOException("音频地址解析异常", result.throwable)
        }
    }

    private fun startProgressLoop() {
        progressJob = serviceScope.launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                if (player.isPlaying) persistProgress()
            }
        }
    }

    /** 把当前播放位置写进 Room，episodeKey 即 MediaItem 的 mediaId */
    private fun persistProgress() {
        val item = player.currentMediaItem ?: return
        val key = item.mediaId.takeIf { it.isNotBlank() } ?: return
        val title = item.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: return
        val position = player.currentPosition.coerceAtLeast(0L)
        // 播放不足 3 秒不记录，避免误点污染历史
        if (position < MIN_PROGRESS_MS) return

        val duration = player.duration.let { if (it == C.TIME_UNSET || it < 0L) 0L else it }
        val episode = item.localConfiguration?.uri?.toEpisodeOrNull()

        val entity = PlayHistoryEntity(
            episodeKey = key,
            title = title,
            cover = item.mediaMetadata.artworkUri?.toString(),
            upName = item.mediaMetadata.artist?.toString(),
            bvid = episode?.bvid,
            cid = episode?.cid,
            sid = episode?.sid,
            durationMs = duration,
            positionMs = position,
            playedAt = System.currentTimeMillis()
        )

        persistScope.launch {
            try {
                ServiceLocator.database.historyDao().upsert(entity)
            } catch (t: Throwable) {
                Log.w(TAG, "播放进度落库失败", t)
            }
        }
    }

    private companion object {
        const val TAG = "PlayerService"
        const val PROGRESS_INTERVAL_MS = 5_000L
        const val MIN_PROGRESS_MS = 3_000L
    }
}
