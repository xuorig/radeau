package com.xuorig

sealed class Command

data class AppendEntriesCommand(
    val term: Int,
    val leaderId: Int,
    val prevLogIndex: Int,
    val prevLogTerm: Int,
    val entries: List<Int>,
    val leaderCommit: Int
) : Command()

data class RequestVoteCommand(val term: Int, val candidateId: Int, val lastLogIndex: Int, val lastLogTerm: Int) : Command()