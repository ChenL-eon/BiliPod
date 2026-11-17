package com.bilipod.app.biliapi.model

import com.google.gson.annotations.SerializedName

/** 稿件详情（x/web-interface/wbi/view） */
data class VideoViewData(
    @SerializedName("bvid") val bvid: String? = null,
    @SerializedName("aid") val aid: Long = 0L,
    @SerializedName("title") val title: String? = null,
    @SerializedName("pic") val pic: String? = null,
    @SerializedName("desc") val desc: String? = null,
    @SerializedName("duration") val duration: Long = 0L,
    @SerializedName("owner") val owner: VideoOwner? = null,
    @SerializedName("pages") val pages: List<VideoPage>? = null,
    @SerializedName("ugc_season") val ugcSeason: UgcSeason? = null,
    /** 互动数据；未登录也能拿到 */
    @SerializedName("stat") val stat: VideoStat? = null,
    /** 当前登录用户对这个稿件的操作状态；未登录时这一段不返回 */
    @SerializedName("req_user") val reqUser: ReqUser? = null,
    /** 权限位：付费稿件（课程 / 充电专属）靠这里的三个 pay 标记识别 */
    @SerializedName("rights") val rights: VideoRights? = null,
    /** 付费信息，付费稿件才有值（含价格）；免费稿件是 null */
    @SerializedName("payment") val payment: VideoPayment? = null
)

/**
 * 稿件付费标记。
 * pay：整稿付费（课程类）；arc_pay：单稿付费；ugc_pay：充电专属（要充电才能看）。
 * 三个任一为 1 就说明这条内容对当前用户不是"直接能听"的。
 */
data class VideoRights(
    @SerializedName("pay") val pay: Int = 0,
    @SerializedName("arc_pay") val arcPay: Int = 0,
    @SerializedName("ugc_pay") val ugcPay: Int = 0,
    @SerializedName("ugc_pay_preview") val ugcPayPreview: Int = 0
)

data class VideoPayment(
    @SerializedName("price") val price: String? = null,
    @SerializedName("bp") val bp: Int = 0
)

data class VideoStat(
    @SerializedName("like") val like: Int = 0
)

/** req_user.like 是 1 表示已点赞、0 或缺失表示没点过；coin 是这一集我已投的枚数 */
data class ReqUser(
    @SerializedName("like") val like: Int = 0,
    @SerializedName("coin") val coin: Int = 0
)

data class VideoOwner(
    @SerializedName("mid") val mid: Long = 0L,
    @SerializedName("name") val name: String? = null,
    @SerializedName("face") val face: String? = null
)

data class VideoPage(
    @SerializedName("cid") val cid: Long = 0L,
    @SerializedName("page") val page: Int = 1,
    @SerializedName("part") val part: String? = null,
    @SerializedName("duration") val duration: Long = 0L
)

/** 视频合集（UP 主把多个稿件组织成一个合集） */
data class UgcSeason(
    @SerializedName("sections") val sections: List<UgcSection>? = null
)

data class UgcSection(
    @SerializedName("episodes") val episodes: List<UgcEpisode>? = null
)

data class UgcEpisode(
    @SerializedName("bvid") val bvid: String? = null,
    @SerializedName("cid") val cid: Long = 0L,
    @SerializedName("title") val title: String? = null,
    @SerializedName("arc") val arc: UgcEpisodeArc? = null
)

data class UgcEpisodeArc(
    @SerializedName("title") val title: String? = null,
    @SerializedName("pic") val pic: String? = null,
    @SerializedName("duration") val duration: Long = 0L
)

/** UP 主空间投稿（x/space/wbi/arc/search） */
data class SpaceArcData(
    @SerializedName("list") val list: SpaceArcList? = null
)

data class SpaceArcList(
    @SerializedName("vlist") val vlist: List<SpaceArc>? = null
)

data class SpaceArc(
    @SerializedName("bvid") val bvid: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("pic") val pic: String? = null,
    @SerializedName("length") val length: String? = null,
    @SerializedName("author") val author: String? = null
)

/** 收藏夹列表（x/v3/fav/folder/created/list-all） */
data class FavFolderListData(
    @SerializedName("list") val list: List<FavFolderItem>? = null
)

data class FavFolderItem(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("fid") val fid: Long = 0L,
    @SerializedName("title") val title: String? = null,
    @SerializedName("media_count") val mediaCount: Int = 0,
    @SerializedName("mid") val mid: Long = 0L
)

/** 收藏夹内容（需要登录 Cookie 才能读自己的收藏夹） */
data class FavResourceData(
    @SerializedName("medias") val medias: List<FavMedia>? = null,
    /** 分页用：true 表示还能翻下一页 */
    @SerializedName("has_more") val hasMore: Boolean = false
)

data class FavMedia(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("bvid") val bvid: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("duration") val duration: Long = 0L,
    @SerializedName("type") val type: Int = 0,
    @SerializedName("upper") val upper: FavUpper? = null
)

data class FavUpper(
    @SerializedName("name") val name: String? = null
)

/** 播放历史（x/web-interface/history/cursor），cursor 用于翻页 */
data class HistoryData(
    @SerializedName("list") val list: List<HistoryItem>? = null,
    @SerializedName("cursor") val cursor: HistoryCursor? = null
)

data class HistoryItem(
    @SerializedName("title") val title: String? = null,
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("duration") val duration: Long = 0L,
    @SerializedName("history") val history: HistoryInner? = null,
    @SerializedName("author_name") val authorName: String? = null,
    /** 翻下一页时的游标：接口按 view_at 倒序，用它做下一页起点 */
    @SerializedName("view_at") val viewAt: Long = 0L
)

data class HistoryInner(
    @SerializedName("bvid") val bvid: String? = null,
    @SerializedName("cid") val cid: Long = 0L,
    @SerializedName("oid") val oid: Long = 0L,
    @SerializedName("business") val business: String? = null
)

/** 历史分页游标：max 为 0 或缺失表示已经没有下一页 */
data class HistoryCursor(
    @SerializedName("max") val max: Long = 0L,
    @SerializedName("view_at") val viewAt: Long = 0L
)

/**
 * 热门榜（x/web-interface/popular），首页"发现"流的数据源。
 * 路径不带 /wbi/，不参与 wbi 签名；未登录也能取，但需要 buvid3 才不容易 412。
 * no_more 是接口给的分页权威信号，比"本页是否拉满"可靠。
 */
data class PopularData(
    @SerializedName("list") val list: List<PopularItem>? = null,
    @SerializedName("no_more") val noMore: Boolean = false
)

data class PopularItem(
    @SerializedName("bvid") val bvid: String? = null,
    @SerializedName("cid") val cid: Long = 0L,
    @SerializedName("title") val title: String? = null,
    @SerializedName("pic") val pic: String? = null,
    @SerializedName("desc") val desc: String? = null,
    @SerializedName("duration") val duration: Long = 0L,
    @SerializedName("pubdate") val pubdate: Long = 0L,
    /** 分区名（短片 / 知识 / 音乐…），desc 为空时拿它当摘要位 */
    @SerializedName("tname") val tname: String? = null,
    @SerializedName("owner") val owner: VideoOwner? = null,
    @SerializedName("stat") val stat: PopularStat? = null
)

data class PopularStat(
    @SerializedName("view") val view: Long = 0L,
    @SerializedName("reply") val reply: Long = 0L
)

/**
 * 个性化推荐流（`x/web-interface/wbi/index/top/feed/rcmd`）。
 *
 * 和热门榜完全两套：它是无限流，没有 `no_more`、没有 `pn`，靠 `fresh_idx` 换批次；
 * 返回的是"视频 / 番剧 / 直播 / 广告"混排，每条用 `goto` 区分类型。
 * 这里只建模视频（goto=av）需要的字段。
 */
data class RcmdData(
    @SerializedName("item") val item: List<RcmdItem>? = null
)

/**
 * `goto` 是 Kotlin 关键字，属性名只能改叫 gotoType，
 * 序列化名仍然是 "goto"，否则 Gson 拿不到值。
 */
data class RcmdItem(
    @SerializedName("bvid") val bvid: String? = null,
    @SerializedName("cid") val cid: Long = 0L,
    @SerializedName("goto") val gotoType: String? = null,
    @SerializedName("pic") val pic: String? = null,
    @SerializedName("title") val title: String? = null,
    /** 秒。rcmd 里是整数秒，热门榜也是，同一个量 */
    @SerializedName("duration") val duration: Long = 0L,
    @SerializedName("pubdate") val pubdate: Long = 0L,
    @SerializedName("owner") val owner: VideoOwner? = null,
    @SerializedName("stat") val stat: RcmdStat? = null
)

data class RcmdStat(
    @SerializedName("view") val view: Long = 0L,
    @SerializedName("danmaku") val danmaku: Long = 0L,
    @SerializedName("like") val like: Long = 0L
)

/** 综合搜索（x/web-interface/wbi/search/type，search_type=video） */
data class SearchData(
    @SerializedName("result") val result: List<SearchItem>? = null,
    @SerializedName("numPages") val numPages: Int = 0,
    @SerializedName("page") val page: Int = 1,
    /**
     * 风控包特征字段：命中风控时 data 里只有 v_voucher、没有 result。
     * 只靠 result 是否为空判断会把"真的没搜到"也当成风控，所以必须认这个字段。
     */
    @SerializedName("v_voucher") val vVoucher: String? = null
)

data class SearchItem(
    @SerializedName("bvid") val bvid: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("pic") val pic: String? = null,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("author") val author: String? = null,
    @SerializedName("play") val play: Int = 0
)

/**
 * UP 主搜索（同一个接口，search_type=bili_user）。
 *
 * 实测（2026-11）：bilibili_user / user / all 三个取值都会被降级成 code=-1200「被降级过滤的请求」，
 * 只有 bili_user 能拿到正常结果，别按直觉改这个名字。
 */
data class UserSearchData(
    @SerializedName("result") val result: List<UserSearchItem>? = null,
    /** 风控包特征字段，和稿件搜索一致 */
    @SerializedName("v_voucher") val vVoucher: String? = null
)

data class UserSearchItem(
    @SerializedName("mid") val mid: Long = 0L,
    @SerializedName("uname") val uname: String? = null,
    @SerializedName("upic") val upic: String? = null,
    @SerializedName("usign") val usign: String? = null,
    @SerializedName("fans") val fans: Int = 0,
    @SerializedName("videos") val videos: Int = 0
)
