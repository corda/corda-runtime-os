package net.corda.messaging.emulation.properties

data class TopicConfiguration(
    val partitionCount: Int,
    val maxPartitionSize: Int,
)
