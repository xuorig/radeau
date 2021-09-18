package com.xuorig

data class NodeConfig(val id: Int, val host: String, val port: Int)

class EnvConfig {
    private val env: Map<String, String> = System.getenv()

    val id = env.getOrDefault("ID", "1").toInt()

    val port = env.getOrDefault("PORT", "8080").toInt()

    val cluster: List<NodeConfig> = env.getOrDefault("CLUSTER", "50:localhost:4040")
        .split(",")
        .map {
            val uri = it.split(":")
            NodeConfig(uri[0].toInt(), uri[1], uri[2].toInt())
        }.toList()
}