package com.bilipod.app.biliapi.model

import com.google.gson.annotations.SerializedName

/**
 * 播放地址（x/player/wbi/playurl，fnval=16 表示 DASH）。
 * 风险点：返回的音频直链带时效 token（约 2 小时），只能内存缓存，不能落库。
 */
data class PlayUrlData(
    @SerializedName("dash") val dash: Dash? = null,
    @SerializedName("durl") val durl: List<Durl>? = null,
    @SerializedName("timelength") val timeLength: Long = 0L
)

data class Dash(
    @SerializedName("duration") val duration: Long = 0L,
    @SerializedName("audio") val audio: List<DashAudio>? = null
)

data class DashAudio(
    @SerializedName(value = "base_url", alternate = ["baseUrl"]) val baseUrl: String? = null,
    @SerializedName("id") val id: Int = 0,
    @SerializedName("bandwidth") val bandwidth: Long = 0L,
    @SerializedName("mime_type") val mimeType: String? = null,
    @SerializedName("codecid") val codecId: Long = 0L
)

data class Durl(
    @SerializedName(value = "url", alternate = ["base_url"]) val url: String? = null,
    @SerializedName("length") val length: Long = 0L
)
