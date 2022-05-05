package net.corda.messaging.emulation.subscription.pubsub

import net.corda.messaging.emulation.topic.model.CommitStrategy
import net.corda.messaging.emulation.topic.model.Consumer
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.PartitionStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata

class PubSubConsumer<K : Any, V : Any>(
    private val subscription: PubSubSubscription<K, V>,
) : Consumer {
    override val groupName = subscription.subscriptionConfig.groupName
    override val topicName = subscription.subscriptionConfig.eventTopic
    override val offsetStrategy = OffsetStrategy.LATEST
    override val partitionAssignmentListener = null
    override val commitStrategy = CommitStrategy.NO_COMMIT
    override val partitionStrategy = PartitionStrategy.DIVIDE_PARTITIONS

    override fun handleRecords(records: Collection<RecordMetadata>) {
        subscription.processRecords(records)
    }
}
