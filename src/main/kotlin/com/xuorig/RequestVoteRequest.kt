package com.xuorig

import kotlinx.serialization.Serializable

@Serializable
data class RequestVoteRequest(val term: Int, val candidateId: Int, val lastLogIndex: Int, val lastLogTerm: Int)