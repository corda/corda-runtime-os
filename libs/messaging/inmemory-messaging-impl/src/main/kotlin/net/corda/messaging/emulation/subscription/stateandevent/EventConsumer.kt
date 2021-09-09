package net.corda.messaging.emulation.subscription.stateandevent

import net.corda.messaging.emulation.topic.model.CommitStrategy
import net.corda.messaging.emulation.topic.model.Consumer
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.PartitionStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata

@Suppress("EqualsWithHashCodeExist")
internal class EventConsumer<K : Any, E : Any>(
    private val subscription: EventSubscription<K, *, E>
) : Consumer {
    override val groupName = subscription.subscription.subscriptionConfig.groupName
    override val topicName = subscription.subscription.subscriptionConfig.eventTopic
    override val offsetStrategy = OffsetStrategy.EARLIEST
    override val commitStrategy = CommitStrategy.COMMIT_AFTER_PROCESSING
    override val partitionStrategy = PartitionStrategy.DIVIDE_PARTITIONS
    override val partitionAssignmentListener = null

    override fun handleRecords(records: Collection<RecordMetadata>) {
        subscription.processEvents(records)
    }

    // Use the same hash code for bosh consumers to promise similar partitions
    override fun hashCode(): Int {
        return subscription.subscription.hashCode()
    }
}
