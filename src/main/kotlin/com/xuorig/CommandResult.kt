package com.xuorig

sealed class CommandResult

data class AppendEntriesCommandResult(val term: Int) : CommandResult()

data class RequestVoteCommandResult(val term: Int, val granted: Boolean) : CommandResult()