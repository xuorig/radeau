package com.xuorig

import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

enum class RaftState {
    FOLLOWER, CANDIDATE, LEADER
}

class Node(private val id: Int, private val cluster: List<NodeConfig>) {
    private var currentTerm: Int = 1

    private val logger = KotlinLogging.logger {}

    private var state: RaftState = RaftState.FOLLOWER

    private val httpClient: HttpClient = HttpClient.newBuilder().build()

    val commands: Channel<Command> = Channel<Command>()
    val commandResults: Channel<CommandResult> = Channel<CommandResult>()

    suspend fun start() {
        while (true) {
            when (state) {
                RaftState.FOLLOWER -> runFollower()
                RaftState.CANDIDATE -> runCandidate()
                RaftState.LEADER -> runLeader()
            }
        }
    }

    private suspend fun runFollower() {
        logger.info { "Entering Follower State" }

        val timer = ElectionTimer(2500)
        timer.schedule()

        while (state == RaftState.FOLLOWER) {
            select<Unit> {
                timer.channel.onReceive {
                    raftLog("Election Timeout Received")
                    state = RaftState.CANDIDATE
                }
                commands.onReceive { command ->
                    when (command) {
                        is RequestVoteCommand -> {
                            raftLog("Got RequestVoteCommand")

                            if (command.term >= currentTerm) {
                                raftLog("Granting Vote")
                                commandResults.send(RequestVoteCommandResult(currentTerm, true))
                            } else {
                                raftLog("Denying Vote")
                                commandResults.send(RequestVoteCommandResult(currentTerm, false))
                            }
                        }
                        is AppendEntriesCommand -> {
                            raftLog("Got AppendEntriesCommand")
                            timer.reset()
                        }
                        else -> {
                            raftLog("Unhandled command: $command")
                        }
                    }
                }
            }
        }
    }

    private fun raftLog(log: String) {
        logger.info { "[state=$state term=$currentTerm] $log" }
    }

    private suspend fun runCandidate() {
        logger.info { "Entering Candidate State" }

        val timer = ElectionTimer(2000)
        timer.schedule()

        currentTerm++

        // Vote for yourself
        var receivedVotes = 1
        val voteChannel = Channel<RequestVoteResponse>(cluster.size)

        raftLog("Sending request votes")

        coroutineScope {
            val votesJob = launch {
                for (node in cluster) {
                    launch {
                        val uri = "http://${node.host}:${node.port}/requestVote"

                        raftLog("requestVote sent: $uri")

                        val requestVoteRequest = RequestVoteRequest(currentTerm, id, 1, 1)
                        val requestBody = Json.encodeToString(requestVoteRequest)

                        val request = HttpRequest.newBuilder()
                            .uri(URI.create("http://${node.host}:${node.port}/requestVote"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                            .build();

                        val response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        val resp = response.await()

                        if (resp.statusCode() == 200) {
                            raftLog("requestVote success")
                            val voteResponse = Json.decodeFromString<RequestVoteResponse>(resp.body())
                            voteChannel.send(voteResponse)
                        } else {
                            raftLog("requestVote failed ${resp.statusCode()}")
                        }
                    }
                }
            }

            launch {
                var shouldExit = false

                logger.info { "Waiting for Responses" }

                while (!shouldExit) {
                    select<Unit> {
                        timer.channel.onReceive {
                            raftLog("Received Election Timeout")
                            shouldExit = true
                        }
                        commands.onReceive { command ->
                            when (command) {
                                is RequestVoteCommand -> {
                                    raftLog("Got RequestVoteCommand")
                                    commandResults.send(RequestVoteCommandResult(1, true))
                                }
                                is AppendEntriesCommand -> {
                                    raftLog("Got AppendEntriesCommand")

                                    if (command.term >= currentTerm) {
                                        state = RaftState.FOLLOWER
                                        shouldExit = true
                                    }
                                }
                                else -> {
                                    raftLog("Unhandled command: $command")
                                }
                            }
                        }
                        voteChannel.onReceive { requestVoteResponse ->
                            logger.info { "Received a vote | granted:${requestVoteResponse.voteGranted} | term:${requestVoteResponse.term}" }

                            if (requestVoteResponse.term > currentTerm) {
                                raftLog("Term from vote response is higher than ours, becoming FOLLOWER")
                                currentTerm = requestVoteResponse.term
                                state = RaftState.FOLLOWER
                                shouldExit = true
                            } else if (requestVoteResponse.voteGranted) {
                                raftLog("Got a granted vote")
                                receivedVotes++

                                if (receivedVotes >= getQuorum()) {
                                    raftLog("Got Quorum, transitioning to LEADER")
                                    state = RaftState.LEADER
                                    shouldExit = true
                                }
                            }
                        }
                    }
                }

                votesJob.cancel()
            }
        }
    }

    private fun getQuorum(): Int {
        return cluster.size / 2
    }

    private suspend fun runLeader() {
        logger.info { "Entering Leader State" }

        val heartbeat = ElectionTimer(1000)
        heartbeat.schedule()

        while (state == RaftState.LEADER) {
            select<Unit> {
                heartbeat.channel.onReceive {
                    coroutineScope {
                        for (node in cluster) {
                            launch {
                                val uri = "http://${node.host}:${node.port}/requestVote"

                                raftLog("heartbeat sent: $uri")

                                val appendEntriesRequest = AppendEntriesRequest(currentTerm, id, 1, 1, listOf(), 1)
                                val requestBody = Json.encodeToString(appendEntriesRequest)

                                val request = HttpRequest.newBuilder()
                                    .uri(URI.create("http://${node.host}:${node.port}/appendEntries"))
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                                    .build();

                                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                            }
                        }
                    }
                }
                commands.onReceive { command ->
                    when (command) {
                        is RequestVoteCommand -> {
                            raftLog("Got RequestVoteCommand")
                            commandResults.send(RequestVoteCommandResult(1, true))
                        }
                        is AppendEntriesCommand -> {
                            raftLog("Got AppendEntriesCommand")

                            if (command.term >= currentTerm) {
                                state = RaftState.FOLLOWER
                            }
                        }
                        else -> {
                            logger.info { "Unhandled command: $command" }
                        }
                    }
                }
            }
        }
    }
}

