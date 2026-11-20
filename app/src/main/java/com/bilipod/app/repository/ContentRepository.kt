package com.bilipod.app.repository

import com.bilipod.app.biliapi.BiliApiClient
import com.bilipod.app.common.AppResult
import com.bilipod.app.common.Formatters
import com.bilipod.app.repository.model.Episode
import com.bilipod.app.repository.model.FavFolder
import com.bilipod.app.biliapi.model.PopularItem
import com.bilipod.app.biliapi.model.RcmdItem
import com.bilipod.app.repository.model.PodcastDetail
import com.bilipod.app.repository.model.UserHit
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * 一页结果：条目 + 是否还有下一页。
 * 搜索/收藏/历史三个列表共用，避免每个接口各自发明一套分页返回值。
 */
data class PageResult<T>(val items: List<T>, val hasMore: Boolean)

/** 历史一页：条目 + 下一页游标（max 为 0 表示到底） */
data class HistoryPage(
    val items: List<Episode>,
    val hasMore: Boolean,
    val nextMax: Long,
    val nextViewAt: Long
)

/**
 * 发现流的一条。
 * Episode 只够播放，不够"发现"卡片展示：日期分组要 pubdate，卡片要分区名、简介和互动数，
 * 所以在这里多带一层，不去污染通用 Episode 模型。
 */
data class DiscoveryItem(
    val episode: Episode,
    val pubdate: Long = 0L,
    val channel: String? = null,
    val summary: String? = null,
    val reply: Long = 0L,
    val view: Long = 0L
)

/**
 * 内容仓库：搜索、历史、收藏、UP 主投稿、合集选集。
 * 只做"接口 DTO -> 领域模型"的转换和错误归一化，不碰 UI 状态。
 *
 * 风险点：带 /wbi/ 的接口失败时，多为密钥过期或签名参数缺失，
 * 可调用 BiliApiClient.refreshWbiKeys() 后重试一次，不要高频重试。
 */
class ContentRepository(private val client: BiliApiClient) {

    suspend fun search(keyword: String, page: Int = 1): AppResult<PageResult<Episode>> = try {
        var resp = client.api.searchVideo(keyword = keyword, page = page)

        // 风控包特征：code=0、result=null，data 里只有 v_voucher。
        // 实测（2026-11）未登录高频搜索时会命中，去掉 buvid3 再查一次往往能拿到正常结果；
        // 只重试一次，不做暴力轮询，仍失败就把错误交给 UI 明确提示。
        // 注意：只认 v_voucher，"result 为空"可能是真的没搜到，不能当风控处理。
        var riskControlled = false
        if (resp.isSuccess && resp.data?.vVoucher != null) {
            delay(600)
            client.cookieStore.skipBuvid3 = true
            try {
                resp = client.api.searchVideo(keyword = keyword, page = page)
            } finally {
                client.cookieStore.skipBuvid3 = false
            }
            riskControlled = resp.isSuccess && resp.data?.vVoucher != null
        }

        when {
            riskControlled -> AppResult.Failure(CODE_RISK_CONTROL, "搜索被风控，请稍后再试")
            !resp.isSuccess -> AppResult.Failure(resp.code, resp.errorText)
            else -> {
                val mapped = resp.data?.result.orEmpty().mapNotNull { item ->
                    val bvid = item.bvid ?: return@mapNotNull null
                    Episode.video(
                        bvid = bvid,
                        // 搜索接口不给 cid，播放前由 PlayUrlRepository 通过 view 接口补齐
                        cid = 0L,
                        title = item.title.stripHtml(),
                        cover = item.pic,
                        upName = item.author,
                        durationSec = Formatters.parseClock(item.duration)
                    )
                }
                // 和首页推荐流、榜单一共用同一条时长下限：搜索是最容易被短视频刷屏的入口，
                // 十分钟以下的听感太碎。durationSec=0 表示接口没给时长或解析失败，这种放行，
                // 宁可漏进来一条短视频，也不要让想找的稿子整条搜不到。
                val tooShort = mapped.count { it.durationSec in 1 until PODCAST_MIN_SECONDS }
                val list = mapped.filter {
                    it.durationSec == 0L || it.durationSec >= PODCAST_MIN_SECONDS
                }
                if (tooShort > 0) {
                    android.util.Log.i(
                        "ContentRepository",
                        "search 「$keyword」 p$page 原始=${mapped.size} 时长不足=$tooShort 保留=${list.size}"
                    )
                }
                // numPages 缺失（0）时退回"本页是否拉满"推断，和 space 接口同一套逻辑
                val hasMore = (resp.data?.numPages ?: 0) > page || mapped.size >= SEARCH_PAGE_SIZE
                AppResult.Success(PageResult(list, hasMore))
            }
        }
    } catch (c: kotlinx.coroutines.CancellationException) {
        // 协程被取消（页面销毁、切夹子放弃上一次请求）不是"失败"，必须原样抛出，
        // 否则会被当作网络错误回填状态。
        throw c
    } catch (t: Throwable) {
        AppResult.Error(t)
    }

    /**
     * 热门榜一页，首页"发现"流用。
     *
     * ps 固定 20（接口单页上限），no_more=true 即到底；
     * 未登录也能取，真被 412 前置风控拦了就交给 UI 显示"重新加载"，
     * 这里不硬刷（热门是浏览型数据，重试一次拿不到就换用户手动刷）。
     */
    suspend fun popular(page: Int = 1): AppResult<PageResult<DiscoveryItem>> = try {
        val resp = client.api.popular(pn = page)
        if (!resp.isSuccess) {
            AppResult.Failure(resp.code, resp.errorText)
        } else {
            val list = resp.data?.list.orEmpty().mapNotNull { item -> toDiscovery(item) }
            val hasMore = resp.data?.noMore != true && list.isNotEmpty()
            AppResult.Success(PageResult(list, hasMore))
        }
    } catch (c: kotlinx.coroutines.CancellationException) {
        // 协程被取消（页面销毁、切 tab 放弃上一次请求）不是"失败"，必须原样抛出
        throw c
    } catch (t: Throwable) {
        AppResult.Error(t)
    }

    /**
     * 个性化推荐流，首页"为你推荐"用它而不是热门榜。
     *
     * 和 [popular] 的区别：热门榜是"近三天热度"的服务端固定榜，几秒内重复请求内容一模一样
     * （实测连续两次首条 bvid 相同），所以刷新按钮看着像没反应；
     * 这个接口按 buvid / 账号画像每次给不同的一批（实测 fresh_idx 1/2/3 三批首条各不相同）。
     *
     * 无限流：没有 no_more，也谈不上"到底了"，hasMore 只在整批都被过滤掉时才为 false。
     * 混排内容里只收 goto=av 的稿件，直播 / 番剧 / 广告位全部跳过。
     *
     * 播客化过滤（见 [PODCAST_MIN_SECONDS] 与 [VISUAL_ONLY_WORDS]）：
     * 十分钟以下的短视频听感太碎、纯视觉分区（音乐 MV、鬼畜、游戏实况、舞蹈、美妆穿搭、
     * 萌宠、吃播）闭眼听等于噪音，都在这一层砍掉。代价是一批 12 条常常只剩两三条，
     * 所以 ViewModel 那边会连续拉几批来凑一页。
     */
    suspend fun recommend(freshIdx: Int): AppResult<PageResult<DiscoveryItem>> = try {
        val resp = client.api.recommendFeed(freshIdx = freshIdx, freshIdx1h = freshIdx)
        if (!resp.isSuccess) {
            AppResult.Failure(resp.code, resp.errorText)
        } else {
            val raw = resp.data?.item.orEmpty()
                .filter { it.gotoType == "av" && !it.bvid.isNullOrBlank() }
            val tooShort = raw.count { (it.duration ?: 0) < PODCAST_MIN_SECONDS }
            val visual = raw.filter { looksVisualOnly(it) }
            val visualBvids = visual.mapNotNull { it.bvid }.toSet()
            val candidates = raw
                .filter { (it.duration ?: 0) >= PODCAST_MIN_SECONDS }
                .filterNot { it.bvid in visualBvids }

            // 付费稿：标题命中"付费 / 课程 / 充电专属"才去核实 rights，一批通常查 0~2 次
            var checked = 0
            var paywalled = 0
            val kept = ArrayList<RcmdItem>()
            for (item in candidates) {
                val bvid = item.bvid ?: continue
                if (isHardPaywalledTitle(item.title)) {
                    paywalled++
                    continue
                }
                if (checked < PAYWALL_CHECK_LIMIT && looksPaywalledTitle(item.title)) {
                    checked++
                    if (isPaywalledSilently(bvid)) {
                        paywalled++
                        continue
                    }
                }
                kept.add(item)
            }
            val list = kept.mapNotNull { toDiscovery(it) }
            android.util.Log.i(
                "ContentRepository",
                "rcmd fresh_idx=$freshIdx 原始=${raw.size} 时长不足=$tooShort 视觉词=${visual.size} " +
                    "付费=$paywalled 保留=${list.size} 命中=" +
                    visual.take(3).joinToString(" | ") { (it.title ?: "").take(16) } +
                    " 留下=" +
                    list.take(3).joinToString(" | ") {
                        "${(it.episode.title).take(12)}(${it.episode.durationSec / 60}min)"
                    }
            )
            AppResult.Success(PageResult(list, hasMore = list.isNotEmpty()))
        }
    } catch (c: kotlinx.coroutines.CancellationException) {
        throw c
    } catch (t: Throwable) {
        AppResult.Error(t)
    }

    /**
     * 纯视觉驱动的分区关键词。
     *
     * rcmd 的 item 里没有 tid / tname（实测字段只有 bvid/cid/goto/pic/title/duration/
     * pubdate/owner/stat），拿不到真实分区，只能对标题做粗筛；宁可漏放几条，
     * 也别让首页混进歌单、鬼畜和游戏实况。
     */
    private fun looksVisualOnly(item: RcmdItem): Boolean {
        val text = (item.title ?: "").lowercase()
        return VISUAL_ONLY_WORDS.any { text.contains(it) }
    }

    /**
     * 付费内容的第一道筛子：标题先过一遍"付费 / 课程 / 充电专属"这类信号词。
     * 只有命中了才值得花一次 view 请求去核实——推荐流每批十条全查一遍太慢，
     * 而绝大多数免费内容标题里根本不会出现这些词。
     */
    private fun looksPaywalledTitle(title: String?): Boolean {
        val text = (title ?: "").lowercase()
        return PAYWALL_HINTS.any { text.contains(it) }
    }

    /**
     * 强信号付费词：命中就直接丢，不必再查 rights。
     *
     * 原因是实测发现充电专属稿件在 `view` 的 `rights` 里毫无标记（pay / arc_pay / ugc_pay 全 0，
     * payment 为 null，连 is_upower_exclusive 都是 false），接口层根本认不出来；
     * 这类内容只能靠标题里作者自己写的"充电专属"来识别，宁可错杀也不能让它进列表。
     */
    private fun isHardPaywalledTitle(title: String?): Boolean {
        val text = (title ?: "").lowercase()
        return HARD_PAYWALL_WORDS.any { text.contains(it) }
    }

    /**
     * 拿到 rights / payment 才能真正判定是不是付费稿。
     * 这里只对标题命中信号词的条目调用，一批里通常 0~2 次请求。
     *
     * 判定为付费：rights.pay / arc_pay / ugc_pay 任一为 1，或者 payment 有值。
     * 请求失败一律当"不是付费"处理——网络抖动不该把正常内容误杀。
     */
    private suspend fun isPaywalledSilently(bvid: String): Boolean {
        return try {
            val resp = client.api.videoDetail(bvid)
            if (!resp.isSuccess) return false
            val rights = resp.data?.rights
            val flagged = rights?.pay == 1 || rights?.arcPay == 1 || rights?.ugcPay == 1
            flagged || resp.data?.payment != null
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * 分区排行榜（发现页分类用）。一次整榜、不分页，所以 hasMore 由调用方按 false 处理。
     * 未登录可用，字段与热门榜一致，映射复用 [toDiscovery]。
     *
     * 榜里有不少十分钟以下的短片，和首页推荐流用同一条时长下限，避免"切个分类又能看到短视频"。
     */
    suspend fun ranking(rid: Int): AppResult<List<DiscoveryItem>> = try {
        val resp = client.api.ranking(rid = rid)
        if (!resp.isSuccess) {
            AppResult.Failure(resp.code, resp.errorText)
        } else {
            val raw = resp.data?.list.orEmpty()
                .filter { (it.duration ?: 0) >= PODCAST_MIN_SECONDS }
            // 分区榜也可能混进付费课程，同样只对标题命中的前几条做 rights 核实
            var checked = 0
            var paywalled = 0
            val list = ArrayList<DiscoveryItem>()
            for (item in raw) {
                val bvid = item.bvid
                if (isHardPaywalledTitle(item.title)) {
                    paywalled++
                    continue
                }
                if (bvid != null && checked < PAYWALL_CHECK_LIMIT && looksPaywalledTitle(item.title)) {
                    checked++
                    if (isPaywalledSilently(bvid)) {
                        paywalled++
                        continue
                    }
                }
                toDiscovery(item)?.let { list.add(it) }
            }
            if (paywalled > 0) {
                android.util.Log.i("ContentRepository", "ranking rid=$rid 付费=$paywalled 保留=${list.size}")
            }
            AppResult.Success(list)
        }
    } catch (c: kotlinx.coroutines.CancellationException) {
        throw c
    } catch (t: Throwable) {
        AppResult.Error(t)
    }

    /**
     * 热门榜单条 -> 发现流条目。
     * desc 经常是空串、偶尔只有一个 "-" 占位，这类都当没有摘要，
     * 交给卡片回落到分区名，别在界面上显示一个孤零零的横杠。
     */
    private fun toDiscovery(item: PopularItem): DiscoveryItem? {
        val bvid = item.bvid ?: return null
        return DiscoveryItem(
            episode = Episode.video(
                bvid = bvid,
                // 接口直接给 cid，key 落到 v_{bvid}_{cid}，播放前不用再补 view
                cid = item.cid,
                title = item.title.stripHtml(),
                cover = item.pic,
                upName = item.owner?.name,
                durationSec = item.duration
            ),
            pubdate = item.pubdate,
            channel = item.tname,
            summary = item.desc?.stripHtml()?.trim()?.takeIf { it.length >= 2 },
            reply = item.stat?.reply ?: 0L,
            view = item.stat?.view ?: 0L
        )
    }

    /**
     * 推荐流条目 -> 发现流条目。
     * rcmd 不返回分区名和简介，这两个字段留空，卡片那边会回落到 UP 名；
     * 它也没有评论数，只有弹幕数和播放量，所以 reply 记 0、view 照实带出来。
     */
    private fun toDiscovery(item: RcmdItem): DiscoveryItem? {
        val bvid = item.bvid ?: return null
        return DiscoveryItem(
            episode = Episode.video(
                bvid = bvid,
                cid = item.cid,
                title = item.title.stripHtml(),
                cover = item.pic,
                upName = item.owner?.name,
                durationSec = item.duration
            ),
            pubdate = item.pubdate,
            channel = null,
            summary = null,
            reply = 0L,
            view = item.stat?.view ?: 0L
        )
    }

    /**
     * UP 主搜索，和稿件搜索同一个 wbi 接口，只有 search_type=bili_user 这一个取值可用
     * （bilibili_user / user / all 都会被降级成 code=-1200）。
     * 该接口不返回可靠的总页数，这里只取首页，不做无限滚动。
     */
    suspend fun searchUsers(keyword: String, page: Int = 1): AppResult<List<UserHit>> = try {
        var resp = client.api.searchUser(keyword = keyword, page = page)

        // 风控包与稿件搜索一致：code=0 但只有 v_voucher，去掉 buvid3 再查一次
        var riskControlled = false
        if (resp.isSuccess && resp.data?.vVoucher != null) {
            delay(600)
            client.cookieStore.skipBuvid3 = true
            try {
                resp = client.api.searchUser(keyword = keyword, page = page)
            } finally {
                client.cookieStore.skipBuvid3 = false
            }
            riskControlled = resp.isSuccess && resp.data?.vVoucher != null
        }

        when {
            riskControlled -> AppResult.Failure(CODE_RISK_CONTROL, "搜索被风控，请稍后再试")
            !resp.isSuccess -> AppResult.Failure(resp.code, resp.errorText)
            else -> AppResult.Success(
                resp.data?.result.orEmpty().mapNotNull { item ->
                    if (item.mid <= 0L) return@mapNotNull null
                    UserHit(
                        mid = item.mid,
                        name = item.uname.stripHtml().ifBlank { item.mid.toString() },
                        face = item.upic,
                        sign = item.usign.stripHtml().takeIf { it.isNotBlank() },
                        fans = item.fans,
                        videos = item.videos
                    )
                }
            )
        }
    } catch (c: kotlinx.coroutines.CancellationException) {
        // 协程被取消（页面销毁、切夹子放弃上一次请求）不是"失败"，必须原样抛出，
        // 否则会被当作网络错误回填状态。
        throw c
    } catch (t: Throwable) {
        AppResult.Error(t)
    }

    /**
     * 播放历史（需要登录）。max / viewAt 传上一页的游标，首页都传 0。
     * 返回里的 nextMax 为 0 表示没有下一页。
     */
    suspend fun history(max: Long = 0L, viewAt: Long = 0L): AppResult<HistoryPage> = try {
        val resp = client.api.history(max = max, viewAt = viewAt)
        if (!resp.isSuccess) {
            AppResult.Failure(resp.code, resp.errorText)
        } else {
            val raw = resp.data?.list.orEmpty()
            val list = raw.mapNotNull { item ->
                val inner = item.history ?: return@mapNotNull null
                val bvid = inner.bvid ?: return@mapNotNull null
                val cid = inner.cid
                if (cid <= 0L) return@mapNotNull null
                Episode.video(
                    bvid = bvid,
                    cid = cid,
                    title = item.title.orEmpty(),
                    cover = item.cover,
                    upName = item.authorName,
                    durationSec = item.duration
                )
            }
            // cursor 存在就以它为权威（max=0 表示到底）；只有接口根本没给 cursor 时，
            // 才用最后一条记录的 view_at 兜底，否则第二页会从头再来一遍。
            // 别把 max=0 当成"缺失"再回退 lastViewAt：那样末页会一直说"还有下一页"，
            // 用户每点一次"加载更多"就把最后一页重复追加一遍。
            val cursor = resp.data?.cursor
            val lastViewAt = raw.lastOrNull()?.viewAt ?: 0L
            val nextMax = if (cursor != null) cursor.max else lastViewAt
            val nextViewAt = cursor?.viewAt?.takeIf { it > 0L } ?: lastViewAt
            AppResult.Success(HistoryPage(list, list.isNotEmpty() && nextMax > 0L, nextMax, nextViewAt))
        }
    } catch (c: kotlinx.coroutines.CancellationException) {
        // 协程被取消（页面销毁、切夹子放弃上一次请求）不是"失败"，必须原样抛出，
        // 否则会被当作网络错误回填状态。
        throw c
    } catch (t: Throwable) {
        AppResult.Error(t)
    }

    /** 收藏夹列表 */
    suspend fun favFolders(mid: Long): AppResult<List<FavFolder>> = try {
        val resp = client.api.favFolders(mid)
        if (!resp.isSuccess) {
            AppResult.Failure(resp.code, resp.errorText)
        } else {
            AppResult.Success(
                resp.data?.list.orEmpty().map {
                    FavFolder(it.id, it.title.orEmpty(), it.mediaCount)
                }
            )
        }
    } catch (c: kotlinx.coroutines.CancellationException) {
        // 协程被取消（页面销毁、切夹子放弃上一次请求）不是"失败"，必须原样抛出，
        // 否则会被当作网络错误回填状态。
        throw c
    } catch (t: Throwable) {
        AppResult.Error(t)
    }

    /**
     * 某个收藏夹里的单集（默认按稿件处理，音频区内容后续再扩展）。
     *
     * 实测（2026-11，真机）：x/v3/fav/resource/list 偶发挂住到 30 秒 readTimeout 才失败，
     * 同一夹子立刻再点一次就能正常返回，所以这里对 SocketTimeoutException 退避 500ms 重试一次。
     */
    suspend fun favEpisodes(
        mediaId: Long,
        page: Int = 1,
        allowTimeoutRetry: Boolean = true
    ): AppResult<PageResult<Episode>> = try {
        val resp = client.api.favResources(mediaId, pn = page)
        if (!resp.isSuccess) {
            AppResult.Failure(resp.code, resp.errorText)
        } else {
            val list = resp.data?.medias.orEmpty().mapNotNull { media ->
                val bvid = media.bvid ?: return@mapNotNull null
                Episode.video(
                    bvid = bvid,
                    cid = 0L,
                    title = media.title.orEmpty(),
                    cover = media.cover,
                    upName = media.upper?.name,
                    durationSec = media.duration
                )
            }
            // has_more 是接口给的权威信号；缺失时退回"是否拉满一页"
            val hasMore = resp.data?.hasMore == true || list.size >= FAV_PAGE_SIZE
            AppResult.Success(PageResult(list, hasMore))
        }
    } catch (c: kotlinx.coroutines.CancellationException) {
        // 协程被取消（页面销毁、切夹子放弃上一次请求）不是"失败"，必须原样抛出，
        // 否则会被当作网络错误回填状态。
        throw c
    } catch (t: Throwable) {
        if (allowTimeoutRetry && t is SocketTimeoutException) {
            delay(500)
            favEpisodes(mediaId, page, allowTimeoutRetry = false)
        } else {
            AppResult.Error(t)
        }
    }

    /**
     * UP 主空间投稿，播客 UP 主的稿件基本都在这里。
     *
     * 实测风险：/x/space/wbi/arc/search 会偶发 HTTP 412（前置风控），
     * 同一 mid 短时间连续打开几次就可能命中，刷新 wbi 密钥 + 退避 1 秒再试一次通常能过。
     * 只重试一次，仍失败就把可读文案交给 UI，由用户点"重新加载"。
     */
    suspend fun spaceVideos(mid: Long, page: Int = 1): AppResult<List<Episode>> = try {
        var result = runCatching { client.api.spaceVideos(mid = mid, pn = page) }
        if (isPreconditionError(result.exceptionOrNull())) {
            // 和搜索同一个套路：先刷 wbi 密钥，再临时去掉 buvid3 试一次。
            // 实测 space 接口对未登录设备的 buvid3 比搜索更敏感，单刷密钥往往仍然 412。
            client.refreshWbiKeys()
            delay(800)
            client.cookieStore.skipBuvid3 = true
            result = try {
                runCatching { client.api.spaceVideos(mid = mid, pn = page) }
            } finally {
                client.cookieStore.skipBuvid3 = false
            }
        }
        val resp = result.getOrElse { throw it }
        if (!resp.isSuccess) {
            AppResult.Failure(resp.code, resp.errorText)
        } else {
            val list = resp.data?.list?.vlist.orEmpty().mapNotNull { arc ->
                val bvid = arc.bvid ?: return@mapNotNull null
                Episode.video(
                    bvid = bvid,
                    cid = 0L,
                    title = arc.title.orEmpty(),
                    cover = arc.pic,
                    upName = arc.author,
                    durationSec = Formatters.parseClock(arc.length)
                )
            }
            AppResult.Success(list)
        }
    } catch (c: kotlinx.coroutines.CancellationException) {
        // 协程被取消（页面销毁、切夹子放弃上一次请求）不是"失败"，必须原样抛出，
        // 否则会被当作网络错误回填状态。
        throw c
    } catch (t: Throwable) {
        val message = if (isPreconditionError(t)) {
            "该 UP 主主页被风控拦下了（HTTP 412），稍等几秒再点重新加载"
        } else {
            t.message
        }
        AppResult.Error(if (message == null) t else IOException(message, t))
    }

    /** 412 一般是 wbi 签名过期或前置风控，值得退避重试一次 */
    private fun isPreconditionError(error: Throwable?): Boolean =
        error?.message?.contains("412") == true

    /**
     * 稿件详情 + 选集。
     * 优先用 ugc_season（合集），没有合集时退回 pages（分 P）。
     */
    suspend fun videoDetail(bvid: String): AppResult<PodcastDetail> = try {
        val resp = client.api.videoDetail(bvid)
        val data = resp.data
        if (!resp.isSuccess || data == null) {
            AppResult.Failure(resp.code, resp.errorText)
        } else {
            val upName = data.owner?.name
            val seasonEpisodes = data.ugcSeason?.sections.orEmpty()
                .flatMap { it.episodes.orEmpty() }
                .mapNotNull { ep ->
                    val epBvid = ep.bvid ?: return@mapNotNull null
                    val cid = ep.cid
                    if (cid <= 0L) return@mapNotNull null
                    Episode.video(
                        bvid = epBvid,
                        cid = cid,
                        title = ep.title ?: ep.arc?.title.orEmpty(),
                        cover = ep.arc?.pic ?: data.pic,
                        upName = upName,
                        durationSec = ep.arc?.duration ?: 0L
                    )
                }
            val episodes = seasonEpisodes.ifEmpty {
                data.pages.orEmpty().map { page ->
                    Episode.video(
                        bvid = data.bvid ?: bvid,
                        cid = page.cid,
                        title = page.part ?: data.title.orEmpty(),
                        cover = data.pic,
                        upName = upName,
                        durationSec = page.duration
                    )
                }
            }
            AppResult.Success(
                PodcastDetail(
                    title = data.title.orEmpty(),
                    cover = data.pic,
                    upName = upName,
                    ownerMid = data.owner?.mid ?: 0L,
                    desc = data.desc,
                    episodes = episodes,
                    // 合集和分 P 在 UI 上是两种东西：合集要标"合集"，分 P 稿件不标
                    isSeason = seasonEpisodes.isNotEmpty()
                )
            )
        }
    } catch (c: kotlinx.coroutines.CancellationException) {
        // 协程被取消（页面销毁、切夹子放弃上一次请求）不是"失败"，必须原样抛出，
        // 否则会被当作网络错误回填状态。
        throw c
    } catch (t: Throwable) {
        AppResult.Error(t)
    }

    private fun String?.stripHtml(): String =
        this.orEmpty()
            .replace(Regex("<[^>]+>"), "")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .trim()

    private companion object {
        /** 自定义错误码：接口返回风控包（code=0 但无 result），不是"没有结果" */
        const val CODE_RISK_CONTROL = -1001

        /** 搜索接口未声明 page_size，B 站默认 20 条一页 */
        const val SEARCH_PAGE_SIZE = 20

        /** 与 BiliApiService.favResources 的 ps 一致（20） */
        const val FAV_PAGE_SIZE = 20

        /**
         * 播客的时长下限：十分钟。
         * 低于它的内容结构是"3 秒钩子 + 高潮 + 结尾求三连"，语音化之后几乎没有信息，
         * 也撑不起一次通勤/做饭时长的收听。
         */
        const val PODCAST_MIN_SECONDS = 600

        /**
         * 纯视觉驱动的分区关键词（音乐 MV、鬼畜、游戏实况、舞蹈、美妆穿搭、萌宠、吃播）。
         * rcmd 的 item 里没有 tid / tname，拿不到真实分区，只能对标题做粗筛；
         * 统一小写比较，"MV" 和 "mv" 都能命中。
         */
        val VISUAL_ONLY_WORDS = listOf(
            "鬼畜", "歌单", "mv", "翻唱", "纯音乐", "bgm",
            "舞蹈", "宅舞", "翻跳", "练舞",
            "穿搭", "美妆", "化妆", "试色", "ootd",
            "萌宠", "撸猫", "撸狗", "猫咖", "吸猫",
            "吃播", "试吃", "探店", "大胃王",
            "游戏实况", "实况", "电竞", "手游", "端游", "抽卡", "开箱", "通关攻略"
        )

        /**
         * 付费内容标题里的信号词。
         * 命中才值得花一次 view 请求核实 rights；不命中的一律当免费，保持零额外开销。
         */
        val PAYWALL_HINTS = listOf(
            "付费", "课程", "充电专属", "会员", "购买", "解锁",
            "网课", "训练营", "精品课", "专栏", "完整版"
        )

        /** 一次请求里最多核实几条付费嫌疑，防止整页都被课程标题塞满时拖慢刷新 */
        const val PAYWALL_CHECK_LIMIT = 3

        /**
         * 强信号付费词，命中直接丢不核实。
         * 充电专属稿件在 view 的 rights 里没有任何标记（实测三项全 0、payment 为 null），
         * 所以这类只能靠标题认——作者几乎都会在标题里写明"充电专属"。
         */
        val HARD_PAYWALL_WORDS = listOf(
            "充电专属", "付费课程", "需购买", "购买后可见", "会员专享"
        )
    }
}
