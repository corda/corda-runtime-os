package net.corda.messaging.emulation.subscription.durable

import net.corda.messaging.emulation.topic.model.CommitStrategy
import net.corda.messaging.emulation.topic.model.Consumer
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.PartitionStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata

internal class DurableConsumer<K : Any, V : Any>(
    private val subscription: DurableSubscription<K, V>
) : Consumer {
    override val groupName = subscription.subscriptionConfig.groupName
    override val topicName = subscription.subscriptionConfig.eventTopic
    override val offsetStrategy = OffsetStrategy.EARLIEST
    override val commitStrategy = CommitStrategy.COMMIT_AFTER_PROCESSING
    override val partitionStrategy = PartitionStrategy.DIVIDE_PARTITIONS
    override val partitionAssignmentListener = subscription.partitionAssignmentListener

    override fun handleRecords(records: Collection<RecordMetadata>) {
        subscription.processRecords(records)
    }
}
