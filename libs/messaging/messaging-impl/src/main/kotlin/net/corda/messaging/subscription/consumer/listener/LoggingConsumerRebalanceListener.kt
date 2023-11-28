package net.corda.messaging.subscription.consumer.listener

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory

open class LoggingConsumerRebalanceListener(clientId: String) : CordaConsumerRebalanceListener {

    /**
     * In derived classes, override the [log] with the more specific log name for that class.
     */
    open val log: Logger = LoggerFactory.getLogger("${this.javaClass.name}-$clientId")

    /**
     * When a [partitions] are revoked write to the log.
     */
    override fun onPartitionsRevoked(partitions: Collection<CordaTopicPartition>) {
        val partitionIds = partitions.map { it.partition }.joinToString(",")
        log.info("Partitions revoked: $partitionIds.")
    }

    /**
     * When a [partitions] are assigned write to the log.
     */
    override fun onPartitionsAssigned(partitions: Collection<CordaTopicPartition>) {
        val partitionIds = partitions.map { it.partition }.joinToString(",")
        log.info("Partitions assigned: $partitionIds.")
    }
}
