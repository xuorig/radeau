package com.xuorig

import kotlinx.serialization.Serializable

@Serializable
data class RequestVoteResponse(val term: Int, val voteGranted: Boolean)