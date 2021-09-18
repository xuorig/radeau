package com.xuorig

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.concurrent.timer

class ElectionTimer(private val durationMs: Long) {
    private lateinit var timer: Timer

    val channel: Channel<Unit> = Channel<Unit>()

    suspend fun schedule() {
        val random = Random()
        val min = durationMs - 500
        val rand = random.nextInt(1000)
        val interval = min + rand

        timer = timer("election", false, interval, interval) {
            runBlocking {
                println("BING")
                channel.send(Unit)
            }
        }
    }

    suspend fun reset() {
        timer.cancel()
        schedule()
    }
}
