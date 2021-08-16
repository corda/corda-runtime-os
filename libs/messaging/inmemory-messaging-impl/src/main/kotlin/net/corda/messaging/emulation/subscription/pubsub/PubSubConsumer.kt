package net.corda.messaging.emulation.subscription.pubsub

import net.corda.messaging.emulation.topic.model.Consumer
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata

class PubSubConsumer<K : Any, V : Any>(
    private val subscription: PubSubSubscription<K, V>,
) : Consumer {
    override val groupName: String = subscription.groupName
    override val topicName: String = subscription.topic
    override val offsetStrategy = OffsetStrategy.LATEST
    override val partitionAssignmentListener = null

    override fun handleRecords(records: Collection<RecordMetadata>) {
        subscription.processRecords(records)
    }
}
