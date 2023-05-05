package net.corda.messaging.subscription.consumer.listener

import java.util.Collections
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messaging.subscription.LifecycleStatusUpdater
import net.corda.messaging.utils.FutureTracker
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class RPCConsumerRebalanceListener<RESPONSE>(
    private val topic: String,
    private val groupName: String,
    private var tracker: FutureTracker<RESPONSE>,
    private val lifecycleStatusUpdater: LifecycleStatusUpdater
) : LoggingConsumerRebalanceListener(topic, groupName) {

    override val log: Logger = LoggerFactory.getLogger("${this.javaClass.name}-$topic-$groupName")

    private val partitions = mutableListOf<CordaTopicPartition>()

    override fun onPartitionsRevoked(partitions: Collection<CordaTopicPartition>) {
        super.onPartitionsRevoked(partitions)

        tracker.removePartitions(partitions.toList())
        this.partitions.removeAll(partitions)
        log.info("Partitions are revoked, topic: $topic, group: $groupName. partitions revoked: [$partitions].    partitions remaining " +
                "[${this.partitions}]")

        if (this.partitions.isEmpty()) {
            lifecycleStatusUpdater.updateLifecycleStatus(LifecycleStatus.DOWN)
        }
    }

    override fun onPartitionsAssigned(partitions: Collection<CordaTopicPartition>) {
        super.onPartitionsAssigned(partitions)

        if (this.partitions.isEmpty() && partitions.isNotEmpty()) {
            lifecycleStatusUpdater.updateLifecycleStatus(LifecycleStatus.UP)
        }
        tracker.addPartitions(partitions.toList())
        this.partitions.addAll(partitions)
        log.info("Assign partitions! topic: $topic, group: $groupName. " +
                "new partitions assigned [$partitions]. All partitions assigned [${this.partitions}]")
    }

    fun getPartitions(): List<CordaTopicPartition> {
        return Collections.unmodifiableList(partitions)
    }
}
