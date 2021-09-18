package com.xuorig

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.application.*
import io.ktor.serialization.*
import io.ktor.features.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() {
    val config = EnvConfig()
    val raft = Node(config.id, config.cluster)

    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json()
        }

        routing {
            get("/") {
                val command = RequestVoteCommand(1,1,1,1)
                raft.commands.send(command)
                val result = raft.commandResults.receive() as RequestVoteCommandResult
                call.respondText("Hello World!")
            }
            post("/requestVote") {
                println("Received requestVote request")
                val request = call.receive<RequestVoteRequest>()
                val command = RequestVoteCommand(request.term, request.candidateId, request.lastLogIndex, request.lastLogTerm)

                println("Sending requestVote in channel")
                raft.commands.send(command)
                println("Sent requestVote in channel")
                val result = raft.commandResults.receive() as RequestVoteCommandResult
                println("Got requestVote result in channel")
                call.respond(RequestVoteResponse(result.term, result.granted))
            }
            post("/appendEntries") {
                println("Received /appendEntries request")

                val request = call.receive<AppendEntriesRequest>()

                val command = AppendEntriesCommand(
                    request.term,
                    request.leaderId,
                    request.prevLogIndex,
                    request.prevLogTerm,
                    request.entries,
                    request.leaderCommit
                )

                raft.commands.send(command)
                call.respond(AppendEntriesResponse(1, true))
            }
        }
    }.start(wait = false)

    runBlocking {
        launch {
            raft.start()
        }
    }
}
