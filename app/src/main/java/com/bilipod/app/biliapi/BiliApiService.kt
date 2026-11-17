package com.bilipod.app.biliapi

import com.bilipod.app.biliapi.model.BuvidData
import com.bilipod.app.biliapi.model.FavFolderListData
import com.bilipod.app.biliapi.model.FavResourceData
import com.bilipod.app.biliapi.model.HistoryData
import com.bilipod.app.biliapi.model.NavData
import com.bilipod.app.biliapi.model.PlayUrlData
import com.bilipod.app.biliapi.model.PopularData
import com.bilipod.app.biliapi.model.RcmdData
import com.bilipod.app.biliapi.model.SearchData
import com.bilipod.app.biliapi.model.SpaceArcData
import com.bilipod.app.biliapi.model.UserSearchData
import com.bilipod.app.biliapi.model.VideoViewData
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 主站接口。
 * 注意：路径里带 /wbi/ 的接口会被 WbiSignInterceptor 自动补 wts / w_rid，
 * 这里只声明业务参数，签名逻辑不进业务层。
 */
interface BiliApiService {

    /** 导航信息：校验登录态 + 取 wbi 密钥（同时 WbiKeyStore 也会用它） */
    @GET("x/web-interface/nav")
    suspend fun nav(): ApiResponse<NavData>

    /** 设备指纹，未登录也建议先取一次 */
    @GET("x/frontend/finger/spi")
    suspend fun buvid(): ApiResponse<BuvidData>

    /** 稿件详情，含分 P / 合集信息 */
    @GET("x/web-interface/wbi/view")
    suspend fun videoDetail(@Query("bvid") bvid: String): ApiResponse<VideoViewData>

    /**
     * 取 DASH 播放地址。
     * fnval=16 只要 DASH，避免拿到 FLV；fourk 与音频无关，保持默认 1。
     */
    @GET("x/player/wbi/playurl")
    suspend fun playUrl(
        @Query("bvid") bvid: String,
        @Query("cid") cid: Long,
        @Query("fnval") fnval: Int = 16,
        @Query("fnver") fnver: Int = 0,
        @Query("fourk") fourk: Int = 1,
        @Query("platform") platform: String = "pc"
    ): ApiResponse<PlayUrlData>

    /** UP 主空间投稿（播客类 UP 主的音频稿件一般也在这里） */
    @GET("x/space/wbi/arc/search")
    suspend fun spaceVideos(
        @Query("mid") mid: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 30,
        @Query("order") order: String = "pubdate",
        @Query("platform") platform: String = "web"
    ): ApiResponse<SpaceArcData>

    /** 我的收藏夹列表（需要登录 Cookie） */
    @GET("x/v3/fav/folder/created/list-all")
    suspend fun favFolders(@Query("up_mid") mid: Long): ApiResponse<FavFolderListData>

    /** 收藏夹内容 */
    @GET("x/v3/fav/resource/list")
    suspend fun favResources(
        @Query("media_id") mediaId: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20,
        @Query("platform") platform: String = "web"
    ): ApiResponse<FavResourceData>

    /** 播放历史。business 为空表示全部类型 */
    @GET("x/web-interface/history/cursor")
    suspend fun history(
        @Query("max") max: Long = 0,
        @Query("view_at") viewAt: Long = 0,
        @Query("business") business: String = ""
    ): ApiResponse<HistoryData>

    /** 稿件搜索（wbi 保护接口） */
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchVideo(
        @Query("keyword") keyword: String,
        @Query("search_type") searchType: String = "video",
        @Query("page") page: Int = 1
    ): ApiResponse<SearchData>

    /** UP 主搜索：search_type 只能传 bili_user */
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchUser(
        @Query("keyword") keyword: String,
        @Query("search_type") searchType: String = "bili_user",
        @Query("page") page: Int = 1
    ): ApiResponse<UserSearchData>

    /**
     * 热门榜：首页"发现"流。
     * 路径不带 /wbi/，所以不会进 WbiSignInterceptor，未登录也能取。
     */
    @GET("x/web-interface/popular")
    suspend fun popular(
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20,
        @Query("platform") platform: String = "web"
    ): ApiResponse<PopularData>

    /**
     * 个性化推荐流：首页"发现"里"为你推荐"的真身。
     *
     * 路径带 /wbi/，WbiSignInterceptor 会自动补 wts + w_rid，这个方法不用管签名。
     * 没有 pn，翻页/换批次靠 fresh_idx 递增；实测 1/2/3 三批内容各不相同。
     * 未登录也能取到泛化推荐（跟着 buvid 走），登录后按账号画像出，所以两者内容不一样。
     */
    @GET("x/web-interface/wbi/index/top/feed/rcmd")
    suspend fun recommendFeed(
        @Query("web_location") webLocation: String = "1430650",
        @Query("y_num") yNum: Int = 4,
        @Query("fresh_type") freshType: Int = 3,
        @Query("feed_version") feedVersion: String = "V8",
        @Query("ps") ps: Int = 12,
        @Query("fresh_idx") freshIdx: Int = 1,
        @Query("fresh_idx_1h") freshIdx1h: Int = 1,
        @Query("brush") brush: Int = 0
    ): ApiResponse<RcmdData>

    /**
     * 点赞 / 取消点赞。
     * like：1 点赞、2 取消。
     * csrf 必须带 Cookie 里的 bili_jct，不然一律返回 -111（csrf 校验失败）。
     */
    @FormUrlEncoded
    @POST("x/web-interface/archive/like")
    suspend fun likeVideo(
        @Field("bvid") bvid: String,
        @Field("like") like: Int,
        @Field("csrf") csrf: String
    ): ApiResponse<Any>

    /**
     * 投币。
     * multiply：投几枚（1~2）；csrf 同点赞，必须带 bili_jct。
     * 常见返回：0 成功、-101 未登录、-111 csrf 失败、-104 硬币不足、-103 超过投币上限。
     */
    @FormUrlEncoded
    @POST("x/web-interface/coin/add")
    suspend fun coinAdd(
        @Field("bvid") bvid: String,
        @Field("multiply") multiply: Int,
        @Field("csrf") csrf: String
    ): ApiResponse<Any>

    /**
     * 分区排行榜：发现页的分类 chips 用它。
     *
     * 和热门榜同一套 list 字段（bvid / cid / pic / title / desc / duration / pubdate / tname /
     * owner / stat），所以复用 PopularData。它一次给整榜（约 100 条）、没有分页参数、
     * 也没有 no_more 字段，调用方要自己按"不分页"处理。
     */
    @GET("x/web-interface/ranking/v2")
    suspend fun ranking(
        @Query("rid") rid: Int,
        @Query("type") type: String = "all"
    ): ApiResponse<PopularData>
}
