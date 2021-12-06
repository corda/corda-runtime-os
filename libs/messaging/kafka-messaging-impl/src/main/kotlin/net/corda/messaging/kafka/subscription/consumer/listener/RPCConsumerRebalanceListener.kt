package net.corda.messaging.kafka.subscription.consumer.listener

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.kafka.utils.FutureTracker
import net.corda.v5.base.util.contextLogger
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.common.TopicPartition
import org.slf4j.Logger
import java.util.*

class RPCConsumerRebalanceListener<RESPONSE>(
    private val topic: String,
    private val groupName: String,
    private var tracker:FutureTracker<RESPONSE>,
    private val lifecycleCoordinator: LifecycleCoordinator
) : ConsumerRebalanceListener {

    private val partitions = mutableListOf<TopicPartition>()

    companion object {
        private val log: Logger = contextLogger()
    }

    override fun onPartitionsRevoked(partitions: MutableCollection<TopicPartition>) {
        tracker.removePartitions(partitions.toList())
        this.partitions.removeAll(partitions)
        if (this.partitions.isEmpty()){
            lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        }
        val partitionIds = partitions.map { it.partition() }.joinToString(",")
        log.info("Consumer group name $groupName for topic $topic partition revoked: $partitionIds.")
    }

    override fun onPartitionsAssigned(partitions: MutableCollection<TopicPartition>) {
        if (this.partitions.isEmpty()){
            lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        }
        tracker.addPartitions(partitions.toList())
        this.partitions.addAll(partitions)
        val partitionIds = partitions.map { it.partition() }.joinToString(",")
        log.info("Consumer group name $groupName for topic $topic partition assigned: $partitionIds.")
    }

    fun getPartitions(): List<TopicPartition> {
        return Collections.unmodifiableList(partitions)
    }
}