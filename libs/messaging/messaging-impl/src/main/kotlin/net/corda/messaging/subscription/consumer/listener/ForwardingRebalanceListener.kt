package net.corda.messaging.subscription.consumer.listener

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A [CordaConsumerRebalanceListener] that logs any assignment events and forwards them to the underlying [partitionAssignmentListener].
 */
class ForwardingRebalanceListener(private val topic: String,
                                  groupName: String,
                                  clientId: String,
                                  private val partitionAssignmentListener: PartitionAssignmentListener
):
    LoggingConsumerRebalanceListener(topic, groupName, clientId) {

    override val log: Logger = LoggerFactory.getLogger("${this.javaClass.name}-$topic-$groupName")

    override fun onPartitionsRevoked(partitions: Collection<CordaTopicPartition>) {
        super.onPartitionsRevoked(partitions)
        partitionAssignmentListener.onPartitionsUnassigned(partitions.map { topic to it.partition })
    }

    override fun onPartitionsAssigned(partitions: Collection<CordaTopicPartition>) {
        super.onPartitionsAssigned(partitions)
        partitionAssignmentListener.onPartitionsAssigned(partitions.map { topic to it.partition })
    }

}
