package net.corda.messaging.kafka.subscription.consumer.listener

import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.v5.base.util.contextLogger
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.common.TopicPartition
import org.slf4j.Logger

/**
 * A [ConsumerRebalanceListener] that logs any assignment events and forwards them to the underlying [partitionAssignmentListener].
 */
class ForwardingRebalanceListener(private val topic: String,
                                  private val consumerGroup: String,
                                  private val partitionAssignmentListener: PartitionAssignmentListener): ConsumerRebalanceListener {

    companion object {
        private val log: Logger = contextLogger()
    }

    override fun onPartitionsRevoked(partitions: MutableCollection<TopicPartition>) {
        val partitionIds = partitions.map{it.partition()}.joinToString(",")
        log.info("Consumer group name $consumerGroup for topic $topic partition revoked: $partitionIds.")
        partitionAssignmentListener.onPartitionsUnassigned(partitions.map { topic to it.partition() })
    }

    override fun onPartitionsAssigned(partitions: MutableCollection<TopicPartition>) {
        val partitionIds = partitions.map{it.partition()}.joinToString(",")
        log.info("Consumer group name $consumerGroup for topic $topic partition assigned: $partitionIds.")
        partitionAssignmentListener.onPartitionsAssigned(partitions.map { topic to it.partition() })
    }

}