package net.corda.messagebus.api.consumer.listener

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.TopicPartition
import net.corda.messagebus.api.utils.FutureTracker
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class RPCConsumerRebalanceListener<RESPONSE>(
    topic: String,
    groupName: String,
    private var tracker: FutureTracker<RESPONSE>,
    private val lifecycleCoordinator: LifecycleCoordinator
) : LoggingConsumerRebalanceListener(topic, groupName) {

    override val log: Logger = LoggerFactory.getLogger("${this.javaClass.name}-$topic-$groupName")

    private val partitions = mutableListOf<TopicPartition>()

    override fun onPartitionsRevoked(partitions: Collection<TopicPartition>) {
        tracker.removePartitions(partitions.toList())
        this.partitions.removeAll(partitions)
        if (this.partitions.isEmpty()){
            lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        }
        super.onPartitionsRevoked(partitions)
    }

    override fun onPartitionsAssigned(partitions: Collection<TopicPartition>) {
        if (this.partitions.isEmpty()){
            lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        }
        tracker.addPartitions(partitions.toList())
        this.partitions.addAll(partitions)
        super.onPartitionsAssigned(partitions)
    }

    fun getPartitions(): List<TopicPartition> {
        return Collections.unmodifiableList(partitions)
    }
}
