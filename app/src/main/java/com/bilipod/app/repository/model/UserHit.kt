package com.bilipod.app.repository.model

/** UP 主搜索结果（对应 search_type=bili_user 的一条记录） */
data class UserHit(
    val mid: Long,
    val name: String,
    val face: String? = null,
    val sign: String? = null,
    val fans: Int = 0,
    val videos: Int = 0
)
