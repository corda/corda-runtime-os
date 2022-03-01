package net.corda.introspiciere.domain

data class TopicDefinitionPayload(
    val name: String,
    val partitions: Int?,
    val replicationFactor: Short?,
    val config: Map<String, String>
)
