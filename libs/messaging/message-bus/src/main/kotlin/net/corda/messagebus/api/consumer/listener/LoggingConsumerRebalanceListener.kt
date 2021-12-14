package net.corda.messagebus.api.consumer.listener

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory

open class LoggingConsumerRebalanceListener(
    private val topic: String,
    private val groupName: String,
) : CordaConsumerRebalanceListener {

    /**
     * In derived classes, override the [log] with the more specific log name for that class.
     */
    open val log: Logger = LoggerFactory.getLogger("${this.javaClass.name}-$topic-$groupName")

    /**
     * When a [partitions] are revoked write to the log.
     */
    override fun onPartitionsRevoked(partitions: Collection<CordaTopicPartition>) {
        val partitionIds = partitions.map{ it.partition }.joinToString(",")
        log.info("Consumer group name $groupName for topic $topic partition revoked: $partitionIds.")
    }

    /**
     * When a [partitions] are assigned write to the log.
     */
    override fun onPartitionsAssigned(partitions: Collection<CordaTopicPartition>) {
        val partitionIds = partitions.map{ it.partition }.joinToString(",")
        log.info("Consumer group name $groupName for topic $topic partition assigned: $partitionIds.")
    }
}
