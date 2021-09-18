package com.xuorig

import kotlinx.serialization.Serializable

@Serializable
data class AppendEntriesResponse(val term: Int, val success: Boolean)
