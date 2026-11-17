package com.bilipod.app.cache

import android.content.Context
import android.util.Log
import com.bilipod.app.biliapi.BiliApiClient
import com.bilipod.app.common.AppResult
import com.bilipod.app.common.Constants
import com.bilipod.app.database.dao.CacheDao
import com.bilipod.app.database.dao.PlayHistoryDao
import com.bilipod.app.database.entity.CacheEntity
import com.bilipod.app.repository.PlayUrlRepository
import com.bilipod.app.repository.model.Episode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 音频缓存管理器。
 *
 * 设计要点：
 * 1. 文件只写 App 私有目录 filesDir/audio_cache，不碰外部存储，无需存储权限；
 * 2. 下载前实时解析音频地址（B 站直链带时效 token），绝不把直链落库；
 * 3. 容量上限可配置，超出后按 lastAccessAt 升序淘汰最久未播放的缓存；
 * 4. 播放时优先读缓存文件（见 PlayerService 的 ResolvingDataSource 分支）。
 *
 * 风险点：并发下载过多会触发 CDN 限速甚至风控，这里限制同时只跑 2 个任务。
 */
class CacheManager(
    private val context: Context,
    private val apiClient: BiliApiClient,
    private val playUrlRepository: PlayUrlRepository,
    private val cacheDao: CacheDao,
    private val historyDao: PlayHistoryDao
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    /**
     * 正在进行的 OkHttp Call。
     *
     * 风险点（实测）：取消协程不会中断阻塞中的 socket read()，
     * 网络被掐断/限速到近乎停滞时，任务会挂在 read 上，按钮点"取消"也无效，只能杀进程。
     * 所以取消时必须同时 call.cancel()，让 OkHttp 主动关闭连接、read 立刻抛 IOException。
     */
    private val calls = ConcurrentHashMap<String, Call>()

    /**
     * 别名表：uiKey -> 真实 key。
     *
     * 搜索/收藏拿到的条目 cid 未知，UI 用的是 v_{bvid}；
     * 下载时补到 cid 后真实 key 变成 v_{bvid}_{cid}。
     * 这里记住这层映射，让 UI 拿旧 key 点取消/删除时也能落到真实任务上。
     */
    private val aliases = ConcurrentHashMap<String, String>()

    /** 并发闸门：同时最多 2 个下载任务，避免触发 CDN 限速/风控（与类注释保持一致） */
    private val downloadSlots = Semaphore(DOWNLOAD_CONCURRENCY)

    private val prefs = context.getSharedPreferences("cache_settings", Context.MODE_PRIVATE)

    private val cacheDir: File
        get() = File(context.filesDir, Constants.CACHE_DIR_NAME).apply { if (!exists()) mkdirs() }

    init {
        // 进程重启后内存里的 jobs 为空，DB 里残留的 WAITING/DOWNLOADING 会永远卡住，这里自愈一次
        // 顺带把历史上用 v_{bvid} 记下的缓存/历史原地升级成 v_{bvid}_{cid}
        scope.launch {
            migrateLegacyKeys()
            recoverStaleStates()
        }
    }

    private val _states = MutableStateFlow<Map<String, CacheState>>(emptyMap())

    /** 下载状态，UI 直接收集 */
    val states: StateFlow<Map<String, CacheState>> = _states.asStateFlow()

    /** 容量上限（字节），默认 1GB */
    var maxBytes: Long
        get() = prefs.getLong(KEY_MAX_BYTES, Constants.CACHE_MAX_BYTES_DEFAULT)
        set(value) {
            prefs.edit().putLong(KEY_MAX_BYTES, value).apply()
            // 调小上限后立刻触发一次淘汰
            scope.launch { evictIfNeeded(0L) }
        }

    fun observeAll(): kotlinx.coroutines.flow.Flow<List<CacheEntity>> = cacheDao.observeAll()

    /** 播放用：命中返回本地文件，未命中返回 null 走网络 */
    fun cachedFile(episodeKey: String): File? {
        val real = aliases[episodeKey] ?: episodeKey
        File(cacheDir, fileNameOf(real)).takeIf { it.exists() && it.length() > 0L }?.let { return it }
        // 回退：UI 拿的是 v_{bvid}，而文件按 v_{bvid}_{cid} 存。
        // 该稿件只有一份缓存时才认领，多集时宁可走网络也不要放错音频。
        val bvid = Episode.bvidOfKey(real)
        if (bvid != null && real == Episode.bvidLevelKey(bvid)) {
            val prefix = fileNameOf(real).removeSuffix(".m4a")
            val candidates = cacheDir.listFiles { file ->
                file.name.startsWith("${prefix}_") && file.name.endsWith(".m4a") && file.length() > 0L
            }
            if (candidates != null && candidates.size == 1) return candidates.first()
        }
        return null
    }

    /** 命中缓存时刷新访问时间（供容量淘汰使用），非阻塞 */
    fun touch(episodeKey: String) {
        val real = aliases[episodeKey] ?: episodeKey
        scope.launch { cacheDao.touch(real, System.currentTimeMillis()) }
    }

    /** 加入下载队列，重复点击不会重复下载 */
    fun enqueue(episode: Episode) {
        // 防护：搜索结果里偶尔会有 bvid 为空的条目（合集/异常稿件），
        // 这种既无法解析播放地址，还会在缓存列表里留下一条 "v_" 垃圾记录
        if (episode.bvid.isNullOrBlank() && episode.sid == null) {
            Log.w(TAG, "忽略无效条目 ${episode.key}（缺少 bvid/sid）")
            return
        }
        val requestedKey = episode.key
        val realKey = aliases[requestedKey] ?: requestedKey
        if (jobs[requestedKey]?.isActive == true || jobs[realKey]?.isActive == true) return

        // 同步占一个可取消的 Job 当"准备中"标记。
        // cid 未知时要先发一次 view 接口补齐，这段窗口里原来的实现会让连点重复打接口，
        // 极端并发下还会起两个任务写同一个 .part。
        // 用 slot 作为父 Job 启动补齐协程：用户点取消时取消 slot，补齐协程会一起停掉，下载不会偷偷开始。
        val slot = Job()
        jobs[requestedKey] = slot
        scope.launch(slot) {
            try {
                // cid 未知时先补齐：key 必须精确到"这一集"，否则多分 P 稿件会共用一条记录
                val normalized = normalize(episode)
                val existing = jobs[normalized.key]
                // key 没变时 jobs[normalized.key] 就是自己的占位，不能误判成"已在下载"
                if (existing != null && existing !== slot && existing.isActive) return@launch
                if (requestedKey != normalized.key) aliases[requestedKey] = normalized.key
                val job = scope.launch(start = CoroutineStart.LAZY) { runDownload(normalized) }
                jobs[normalized.key] = job
                job.start()
            } finally {
                if (jobs[requestedKey] === slot) jobs.remove(requestedKey)
            }
        }
    }

    fun cancel(episodeKey: String) {
        val real = aliases[episodeKey] ?: episodeKey
        jobs.remove(real)?.cancel()
        jobs.remove(episodeKey)?.cancel()
        // 光取消协程不够：阻塞在 read() 上的 socket 不会自己退出
        calls.remove(real)?.cancel()
        calls.remove(episodeKey)?.cancel()
    }

    suspend fun delete(episodeKey: String) = withContext(Dispatchers.IO) {
        val real = aliases.remove(episodeKey) ?: episodeKey
        jobs.remove(real)?.cancel()
        jobs.remove(episodeKey)?.cancel()
        calls.remove(real)?.cancel()
        calls.remove(episodeKey)?.cancel()
        File(cacheDir, fileNameOf(real)).delete()
        tempFileOf(real).delete()
        cacheDao.delete(real)
        if (real != episodeKey) cacheDao.delete(episodeKey)
        _states.update { it - real - episodeKey }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        calls.values.forEach { it.cancel() }
        calls.clear()
        cacheDir.listFiles()?.forEach { it.delete() }
        cacheDao.clear()
        _states.value = emptyMap()
    }

    // ---------------- 内部实现 ----------------

    /** 补齐 cid，得到"这一集唯一"的 key；补不到就退回原 key，不阻塞下载 */
    private suspend fun normalize(episode: Episode): Episode {
        if (episode.sid != null) return episode
        val bvid = episode.bvid ?: return episode
        if ((episode.cid ?: 0L) > 0L) return episode
        val cid = playUrlRepository.firstCidOf(bvid) ?: return episode
        return episode.copy(cid = cid, key = Episode.videoKey(bvid, cid))
    }

    /**
     * 旧 key（v_{bvid}）→ 新 key（v_{bvid}_{cid}）一次性迁移。
     *
     * 只迁移"能确定 cid"的记录：缓存表和历史表本来就有 cid 列，不用猜。
     * 文件同步改名，否则记录会指向不存在的文件，被僵尸态自愈误判成 FAILED。
     * 重复的直接删掉旧的一条，新 key 已存在就以新的为准。
     */
    private suspend fun migrateLegacyKeys() {
        cacheDao.listAll().forEach { entity ->
            val bvid = entity.bvid ?: return@forEach
            if (entity.episodeKey != Episode.bvidLevelKey(bvid)) return@forEach
            // cid 列可能为空（早期从搜索结果下载时没补过），补一次再判断
            val cid = entity.cid?.takeIf { it > 0L }
                ?: playUrlRepository.firstCidOf(bvid)
                ?: return@forEach
            val newKey = Episode.videoKey(bvid, cid)
            if (cacheDao.find(newKey) != null) {
                File(cacheDir, entity.fileName).delete()
                cacheDao.delete(entity.episodeKey)
                return@forEach
            }
            val newName = fileNameOf(newKey)
            val oldFile = File(cacheDir, entity.fileName)
            if (oldFile.exists()) {
                val target = File(cacheDir, newName)
                if (!target.exists()) oldFile.renameTo(target)
            }
            cacheDao.upsert(entity.copy(episodeKey = newKey, fileName = newName, cid = cid))
            cacheDao.delete(entity.episodeKey)
            Log.i(TAG, "缓存 key 迁移 ${entity.episodeKey} -> $newKey")
        }

        historyDao.listAll().forEach { entity ->
            val bvid = entity.bvid ?: return@forEach
            if (entity.episodeKey != Episode.bvidLevelKey(bvid)) return@forEach
            val cid = entity.cid?.takeIf { it > 0L }
                ?: playUrlRepository.firstCidOf(bvid)
                ?: return@forEach
            val newKey = Episode.videoKey(bvid, cid)
            if (historyDao.find(newKey) != null) {
                historyDao.delete(entity.episodeKey)
                return@forEach
            }
            historyDao.upsert(entity.copy(episodeKey = newKey, cid = cid))
            historyDao.delete(entity.episodeKey)
            Log.i(TAG, "历史 key 迁移 ${entity.episodeKey} -> $newKey")
        }
    }

    private suspend fun runDownload(episode: Episode) {
        // 排队状态先落到 UI/DB，真正的并发控制交给信号量
        updateState(episode.key, CacheState(status = CacheState.Status.WAITING))
        upsert(episode, CacheState.Status.WAITING, 0L, 0L)

        try {
            downloadSlots.withPermit { doDownload(episode) }
        } catch (c: CancellationException) {
            markCanceled(episode)
            throw c
        } catch (t: Throwable) {
            // call.cancel() 之后 socket 会抛 IOException，此时协程其实已经被取消，
            // 不能当成"下载失败"记，否则用户点了取消反而看到失败文案
            if (!currentCoroutineContext().isActive) {
                markCanceled(episode)
                throw CancellationException("下载已取消 ${episode.key}", t)
            }
            Log.w(TAG, "缓存失败 ${episode.key}", t)
            // 失败不删 .part：直链过期、断网这类失败用户点重试可以接着下，不用从头再来
            val tempSize = tempFileOf(episode.key).takeIf { it.exists() }?.length() ?: 0L
            upsert(episode, CacheState.Status.FAILED, tempSize, 0L)
            updateState(
                episode.key,
                CacheState(
                    status = CacheState.Status.FAILED,
                    downloadedBytes = tempSize,
                    message = t.message ?: "下载失败"
                )
            )
        } finally {
            jobs.remove(episode.key)
            calls.remove(episode.key)
        }
    }

    /**
     * 取消后的收尾。
     * 必须用 NonCancellable：当前协程已处于取消状态，直接调用 upsert 等 suspend 函数
     * 会立刻再抛 CancellationException，导致 CANCELED 状态写不进库。
     */
    private suspend fun markCanceled(episode: Episode) {
        withContext(NonCancellable) {
            cleanupFiles(episode.key)
            upsert(episode, CacheState.Status.CANCELED, 0L, 0L)
            updateState(episode.key, CacheState(status = CacheState.Status.CANCELED))
        }
    }

    private suspend fun doDownload(episode: Episode) {
        // 下载前实时解析，避免拿到一个已经过期的直链
        val resolved = when (val result = playUrlRepository.resolveAudioUrl(episode)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> throw IOException("解析音频地址失败：${result.message}")
            is AppResult.Error -> throw result.throwable
        }

        val target = File(cacheDir, fileNameOf(episode.key))
        if (target.exists() && target.length() > 0L) {
            // 已经有完整文件，直接标记成功
            upsert(episode, CacheState.Status.SUCCESS, target.length(), target.length())
            updateState(
                episode.key,
                CacheState(CacheState.Status.SUCCESS, target.length(), target.length())
            )
            return
        }

        withContext(Dispatchers.IO) {
            val temp = tempFileOf(episode.key)
            // 不删残片：失败态点"重试"时，downloadToTemp 会用 Range 从残片长度接着下
            downloadToTemp(episode, resolved.url, resolved.headers, temp)
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            val size = target.length()
            upsert(episode, CacheState.Status.SUCCESS, size, size)
            updateState(episode.key, CacheState(CacheState.Status.SUCCESS, size, size))
            evictIfNeeded(0L)
        }
    }

    /** 删除最终文件与临时文件，用于取消/失败后的清理 */
    private fun cleanupFiles(episodeKey: String) {
        File(cacheDir, fileNameOf(episodeKey)).delete()
        tempFileOf(episodeKey).delete()
    }

    /**
     * 进程重启后的状态自愈：
     * jobs 是内存态，重启即空；DB 里遗留的 WAITING/DOWNLOADING 会永远停在"下载中"。
     * 这里按磁盘实际情况修正：有完整文件 → SUCCESS；只有 .part → FAILED 但保留残片，
     * 用户点重试会用 Range 续传，不白下已经拿到的部分。
     */
    private suspend fun recoverStaleStates() {
        val all = runCatching { cacheDao.observeAll().first() }.getOrNull() ?: return
        all.forEach { entity ->
            val stale = entity.status == CacheState.Status.WAITING.name ||
                entity.status == CacheState.Status.DOWNLOADING.name
            if (!stale) return@forEach

            val target = File(cacheDir, entity.fileName)
            val temp = File(cacheDir, entity.fileName + TEMP_SUFFIX)
            val finalSize = target.takeIf { it.exists() }?.length() ?: 0L
            if (finalSize > 0L) {
                // 完整文件已经在，残片没有价值
                temp.delete()
                cacheDao.upsert(
                    entity.copy(
                        status = CacheState.Status.SUCCESS.name,
                        downloadedBytes = finalSize,
                        totalBytes = finalSize
                    )
                )
                updateState(
                    entity.episodeKey,
                    CacheState(CacheState.Status.SUCCESS, finalSize, finalSize)
                )
            } else {
                val tempSize = temp.takeIf { it.exists() }?.length() ?: 0L
                cacheDao.upsert(
                    entity.copy(
                        status = CacheState.Status.FAILED.name,
                        downloadedBytes = tempSize,
                        totalBytes = entity.totalBytes
                    )
                )
                updateState(
                    entity.episodeKey,
                    CacheState(
                        status = CacheState.Status.FAILED,
                        downloadedBytes = tempSize,
                        totalBytes = entity.totalBytes,
                        message = if (tempSize > 0L) "上次下载中断，点重试可续传" else "上次下载中断"
                    )
                )
            }
        }
    }

    private suspend fun downloadToTemp(
        episode: Episode,
        url: String,
        headers: Map<String, String>,
        temp: File
    ) {
        // 先腾出空间，避免下载到一半写满
        evictIfNeeded(0L)

        // 续传起点：上次中断留下的 .part 长度
        val resumeFrom = temp.takeIf { it.exists() }?.length()?.coerceAtLeast(0L) ?: 0L
        if (resumeFrom > 0L) Log.i(TAG, "续传 ${episode.key}，从 $resumeFrom 字节继续")

        val request = Request.Builder()
            .url(url)
            .get()
            .apply {
                headers.forEach { (k, v) -> header(k, v) }
                if (resumeFrom > 0L) header("Range", "bytes=$resumeFrom-")
            }
            .build()

        val call = apiClient.httpClient.newCall(request)
        calls[episode.key] = call

        coroutineScope {
            // 停顿看门狗：只要 STALL_TIMEOUT_MS 内没有新字节就主动掐断连接。
            // readTimeout(30s) 只能挡住"完全没数据"，限速档下数据一滴一滴回来时永远不触发，
            // 这条看门狗是按"有没有真实进展"判断的，用来兜住那种半死不活的连接。
            val lastByteAt = AtomicLong(System.currentTimeMillis())
            val stallReason = AtomicReference<String?>(null)
            val watchdog = launch {
                while (isActive) {
                    delay(WATCHDOG_TICK_MS)
                    val idle = System.currentTimeMillis() - lastByteAt.get()
                    if (idle >= STALL_TIMEOUT_MS) {
                        val reason = "下载停滞 ${idle / 1000} 秒，已自动中断"
                        stallReason.set(reason)
                        Log.w(TAG, "$reason key=${episode.key}")
                        call.cancel()
                        break
                    }
                }
            }

            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        // 416：残片不比服务端资源短才可能发生（上次数据写完、rename 之前进程被杀）。
                        // 不删掉它就会每次重试都 416，永远好不了，所以这里清掉残片、从头来。
                        if (response.code == 416) temp.delete()
                        // 403 一般是直链过期或 Referer 校验失败，交给上层提示重试
                        throw IOException("下载失败 HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IOException("响应体为空")
                    // 206 = 服务器接受了 Range，从残片末尾续写；200 = 不认 Range，只能整段重下
                    val resumed = response.code == 206 && resumeFrom > 0L
                    val contentLength = body.contentLength().coerceAtLeast(0L)
                    val totalBytes = if (resumed) resumeFrom + contentLength else contentLength
                    var downloaded = if (resumed) resumeFrom else 0L
                    if (!resumed && temp.exists()) temp.delete()
                    // 只知道"还差多少"才腾空间：残片占的那部分不能再算一次
                    evictIfNeeded((totalBytes - downloaded).coerceAtLeast(0L))
                    body.byteStream().use { input ->
                        FileOutputStream(temp, resumed).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = try {
                                    input.read(buffer)
                                } catch (io: IOException) {
                                    // 看门狗掐断时把原因换成可读的中文提示
                                    throw IOException(stallReason.get() ?: io.message, io)
                                }
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                lastByteAt.set(System.currentTimeMillis())
                                updateState(
                                    episode.key,
                                    CacheState(CacheState.Status.DOWNLOADING, downloaded, totalBytes)
                                )
                                // 落库不要太频繁，按 512KB 粒度更新
                                if (downloaded % PERSIST_STEP == 0L) {
                                    upsert(episode, CacheState.Status.DOWNLOADING, downloaded, totalBytes)
                                }
                            }
                        }
                    }
                }
            } finally {
                watchdog.cancel()
                calls.remove(episode.key)
            }
        }
    }

    /**
     * 容量淘汰：按最久未访问顺序删，直到能放下新内容。
     *
     * 注意：占用大小以磁盘真实文件长度为准，DB 里的 totalBytes 只作兜底。
     * 原因是 chunked 响应（contentLength = -1）时 DB 记的大小是 0，会导致容量永远算不准。
     */
    private suspend fun evictIfNeeded(incoming: Long) {
        val max = maxBytes
        var total = diskUsageBytes()
        if (total + incoming <= max) return

        cacheDao.listForEviction().forEach { entity ->
            if (total + incoming <= max) return@forEach
            val file = File(cacheDir, entity.fileName)
            // 文件可能已被系统清理，这时用 DB 记录兜底计算
            val size = if (file.exists()) file.length() else entity.totalBytes
            file.delete()
            cacheDao.delete(entity.episodeKey)
            _states.update { it - entity.episodeKey }
            total -= size
        }
    }

    /** 私有目录里真实占用（不含 .part 临时文件） */
    private fun diskUsageBytes(): Long {
        val files = cacheDir.listFiles() ?: return 0L
        return files.filter { it.isFile && !it.name.endsWith(TEMP_SUFFIX) }.sumOf { it.length() }
    }

    private suspend fun upsert(
        episode: Episode,
        status: CacheState.Status,
        downloaded: Long,
        total: Long
    ) {
        val now = System.currentTimeMillis()
        val old = cacheDao.find(episode.key)
        cacheDao.upsert(
            CacheEntity(
                episodeKey = episode.key,
                title = episode.title,
                cover = episode.cover,
                upName = episode.upName,
                bvid = episode.bvid,
                cid = episode.cid,
                sid = episode.sid,
                fileName = fileNameOf(episode.key),
                totalBytes = total,
                downloadedBytes = downloaded,
                status = status.name,
                createdAt = old?.createdAt ?: now,
                lastAccessAt = now
            )
        )
    }

    private fun updateState(key: String, state: CacheState) {
        _states.update { it + (key to state) }
    }

    /** 文件名用 key 转写，避免路径注入 */
    private fun fileNameOf(episodeKey: String): String =
        episodeKey.replace(Regex("[^A-Za-z0-9_]"), "_") + ".m4a"

    /** 临时文件名：下载中的残片，取消/失败时必须清理 */
    private fun tempFileOf(episodeKey: String): File = File(cacheDir, fileNameOf(episodeKey) + TEMP_SUFFIX)

    private companion object {
        const val TAG = "CacheManager"
        const val KEY_MAX_BYTES = "max_bytes"
        const val BUFFER_SIZE = 8 * 1024
        const val PERSIST_STEP = 512 * 1024L
        const val TEMP_SUFFIX = ".part"
        /** 同时最多 2 个下载任务 */
        const val DOWNLOAD_CONCURRENCY = 2
        /** 看门狗检查间隔 */
        const val WATCHDOG_TICK_MS = 2_000L
        /**
         * 无新字节的容忍上限。
         * 取 30s：比 OkHttp readTimeout(30s) 同一量级，但判断依据是"确实拿到字节"，
         * 能兜住"数据极慢地一滴一滴回来"这种 readTimeout 失效的情况。
         */
        const val STALL_TIMEOUT_MS = 30_000L
    }
}
