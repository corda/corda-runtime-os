package net.corda.messaging.emulation.subscription.pubsub

import net.corda.messaging.emulation.topic.model.Consumer
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata
import net.corda.v5.base.util.uncheckedCast

class PubSubConsumer<K : Any, V : Any>(
    private val subscription: PubSubSubscription<K, V>,
    override val groupName: String = subscription.groupName,
    override val topicName: String = subscription.topic,
) : Consumer {
    override val offsetStrategy = OffsetStrategy.LATEST
    override val partitionAssignmentListener = null

    override fun handleRecords(records: Collection<RecordMetadata>) {
        records.forEach { recordMetaData ->
            subscription.processRecord(uncheckedCast(recordMetaData.record))
        }
    }
}
