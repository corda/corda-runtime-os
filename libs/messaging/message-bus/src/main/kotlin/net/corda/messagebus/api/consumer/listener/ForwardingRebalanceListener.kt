package net.corda.messagebus.api.consumer.listener

import net.corda.messagebus.api.TopicPartition
import net.corda.messagebus.api.consumer.ConsumerRebalanceListener
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A [ConsumerRebalanceListener] that logs any assignment events and forwards them to the underlying [partitionAssignmentListener].
 */
class ForwardingRebalanceListener(private val topic: String,
                                  groupName: String,
                                  private val partitionAssignmentListener: PartitionAssignmentListener):
    LoggingConsumerRebalanceListener(topic, groupName) {

    override val log: Logger = LoggerFactory.getLogger("${this.javaClass.name}-$topic-$groupName")

    override fun onPartitionsRevoked(partitions: Collection<TopicPartition>) {
        super.onPartitionsRevoked(partitions)
        partitionAssignmentListener.onPartitionsUnassigned(partitions.map { topic to it.partition })
    }

    override fun onPartitionsAssigned(partitions: Collection<TopicPartition>) {
        super.onPartitionsAssigned(partitions)
        partitionAssignmentListener.onPartitionsAssigned(partitions.map { topic to it.partition })
    }

}
