package net.corda.messagebus.api

/**
 * Topic/partition info for specific message queues on the message bus.
 */
data class TopicPartition(val topic:String, val partition: Int)
