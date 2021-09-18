package com.xuorig

import kotlinx.serialization.Serializable

@Serializable
data class AppendEntriesRequest(
    val term: Int,
    val leaderId: Int,
    val prevLogIndex: Int,
    val prevLogTerm: Int,
    val entries: List<Int>,
    val leaderCommit: Int
)