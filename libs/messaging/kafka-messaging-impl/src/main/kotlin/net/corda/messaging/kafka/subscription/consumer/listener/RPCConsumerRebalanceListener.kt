package net.corda.messaging.kafka.subscription.consumer.listener

import net.corda.v5.base.util.contextLogger
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.common.TopicPartition
import org.slf4j.Logger

//TODOs: Error any futures affected by repartitioning
class RPCConsumerRebalanceListener(
    private val topic: String,
    private val groupName: String
) : ConsumerRebalanceListener {

    var partitions = mutableListOf<TopicPartition>()

    companion object {
        private val log: Logger = contextLogger()
    }

    override fun onPartitionsRevoked(partitions: MutableCollection<TopicPartition>) {
        this.partitions.removeAll(partitions)
        val partitionIds = partitions.map{it.partition()}.joinToString(",")
        log.info("Consumer group name $groupName for topic $topic partition revoked: $partitionIds.")
    }

    override fun onPartitionsAssigned(partitions: MutableCollection<TopicPartition>) {
        this.partitions.addAll(partitions)
        val partitionIds = partitions.map{it.partition()}.joinToString(",")
        log.info("Consumer group name $groupName for topic $topic partition assigned: $partitionIds.")
    }
}