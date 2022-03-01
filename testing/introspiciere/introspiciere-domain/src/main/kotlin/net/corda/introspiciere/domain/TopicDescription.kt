package net.corda.introspiciere.domain

data class TopicDescription(
    val id: String,
    val name: String,
    val partitions: Int,
    val replicas: Short,
)