package net.corda.messaging.subscription.consumer.listener

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messaging.kafka.utils.FutureTracker
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

    private val partitions = mutableListOf<CordaTopicPartition>()

    override fun onPartitionsRevoked(partitions: Collection<CordaTopicPartition>) {
        tracker.removePartitions(partitions.toList())
        this.partitions.removeAll(partitions)
        if (this.partitions.isEmpty()){
            lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        }
        super.onPartitionsRevoked(partitions)
    }

    override fun onPartitionsAssigned(partitions: Collection<CordaTopicPartition>) {
        if (this.partitions.isEmpty()){
            lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        }
        tracker.addPartitions(partitions.toList())
        this.partitions.addAll(partitions)
        super.onPartitionsAssigned(partitions)
    }

    fun getPartitions(): List<CordaTopicPartition> {
        return Collections.unmodifiableList(partitions)
    }
}
