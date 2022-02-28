package net.corda.introspiciere.domain

data class TopicDefinition(
    val name: String,
    val partitions: Int? = null,
    val replicationFactor: Short? = null,
    val config: Map<String, String> = emptyMap(),
)