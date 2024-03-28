package net.corda.messaging.subscription.consumer.listener

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.subscription.listener.ConsumerOffsetProvider
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import org.slf4j.LoggerFactory

/**
 * This class is responsible for setting the starting offsets on a topic when a subscriptions offset are managed
 * by the client code.
 */
class OffsetProviderListener(
    clientId: String,
    private val partitionAssignmentListener: PartitionAssignmentListener?,
    private val offsetProvider: ConsumerOffsetProvider?,
    private val consumer: CordaConsumer<*, *>
) : CordaConsumerRebalanceListener {

    private val log = LoggerFactory.getLogger("${this.javaClass.name}-${clientId}")

    override fun onPartitionsRevoked(partitions: Collection<CordaTopicPartition>) {
        logAction("revoked", partitions)
        partitionAssignmentListener?.onPartitionsUnassigned(partitions.toTopicPartitionList())
    }

    override fun onPartitionsAssigned(partitions: Collection<CordaTopicPartition>) {
        logAction("assigned", partitions)

        partitionAssignmentListener?.onPartitionsAssigned(partitions.toTopicPartitionList())

        if (offsetProvider != null) {
            var inputTopicPartitions = partitions.toTopicPartitionList().toSet()
            var topicPartitionOffsets = offsetProvider.getStartingOffsets(inputTopicPartitions)
            if (topicPartitionOffsets.keys.toSet() != inputTopicPartitions) {
                throw CordaMessageAPIFatalException(
                    "The offset provide did not return offset for every requested topic/partition"
                )
            }

            topicPartitionOffsets.forEach {
                consumer.seek(CordaTopicPartition(it.key.first, it.key.second), it.value)
            }
        }
    }

    private fun logAction(action: String, partitions: Collection<CordaTopicPartition>) {
        partitions.groupBy { it.topic }.forEach { topicGrp ->
            val partitionIds = topicGrp.value.map { it.partition }.joinToString(",")
            log.info("Partitions $action for topic ${topicGrp.key}: $partitionIds.")
        }
    }

    private fun Collection<CordaTopicPartition>.toTopicPartitionList(): List<Pair<String, Int>> {
        return this.map { it.topic to it.partition }
    }
}
