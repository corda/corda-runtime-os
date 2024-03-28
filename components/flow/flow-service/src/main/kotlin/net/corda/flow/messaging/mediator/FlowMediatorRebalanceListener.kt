package net.corda.flow.messaging.mediator

import net.corda.flow.fiber.cache.FlowFiberCache
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Rebalance listener triggered when partitions are assigned or revoked from a flow mediator consumer.
 * Log the assignment changes and invalidate the flow fiber cache when assigned new partitions to clear out stale fibers.
 */
class FlowMediatorRebalanceListener(
    clientId: String,
    private val flowFiberCache: FlowFiberCache
): CordaConsumerRebalanceListener {


    val log: Logger = LoggerFactory.getLogger("${this.javaClass.name}-${clientId}")

    /**
     * When a [partitions] are revoked write to the log.
     */
    override fun onPartitionsRevoked(partitions: Collection<CordaTopicPartition>) {
        val partitionIds = partitions.map { it.partition }.joinToString(",")
        log.info("Partitions revoked: $partitionIds.")
    }

    /**
     * When a [partitions] are assigned write to the log and invalidate the flow fiber cache.
     * Cache is invalidated, to clear cache of fibers which may be stale.
     */
    override fun onPartitionsAssigned(partitions: Collection<CordaTopicPartition>) {
        flowFiberCache.removeAll()
        val partitionIds = partitions.map { it.partition }.joinToString(",")
        log.info("Partitions assigned: $partitionIds. Invalidated flow fiber cache.")
    }
}
