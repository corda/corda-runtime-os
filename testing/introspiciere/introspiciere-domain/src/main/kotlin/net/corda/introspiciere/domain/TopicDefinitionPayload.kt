package net.corda.introspiciere.domain

data class TopicDefinitionPayload(
    val partitions: Int?,
    val replicationFactor: Short?,
    val config: Map<String, String>
)
