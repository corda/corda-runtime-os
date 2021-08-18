package net.corda.messaging.emulation.subscription.pubsub

import net.corda.messaging.emulation.topic.model.ConsumerDefinitions
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.PartitionStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata

class PubSubConsumer<K : Any, V : Any>(
    private val subscription: PubSubSubscription<K, V>,
) : ConsumerDefinitions {
    override val groupName: String = subscription.groupName
    override val topicName: String = subscription.topic
    override val offsetStrategy = OffsetStrategy.LATEST
    override val partitionAssignmentListener = null
    override val partitionStrategy = PartitionStrategy.modulo

    override fun handleRecords(records: Collection<RecordMetadata>) {
        subscription.processRecords(records)
    }
}
