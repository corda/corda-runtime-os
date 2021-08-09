package net.corda.messaging.emulation.properties

data class TopicConfiguration(
    val partitionCount: Int,
    val pollSize: Int,
    val maxSize: Int,
    val threadStopTimeout: Long,
)
