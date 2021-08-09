package net.corda.messaging.emulation.subscription.eventlog

import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.emulation.topic.model.Consumer
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata
import net.corda.v5.base.util.uncheckedCast

class EventLogConsumer<K : Any, V : Any>(
    private val subscription: EventLogSubscription<K, V>,
    override val groupName: String = subscription.groupName,
    override val topicName: String = subscription.topicName,
    override val partitionAssignmentListener: PartitionAssignmentListener? = subscription.partitionAssignmentListener,
) : Consumer {
    override val offsetStrategy = OffsetStrategy.EARLIEST

    override fun handleRecords(records: Collection<RecordMetadata>) {
        records.forEach { recordMetaData ->
            subscription.processor.onNext(uncheckedCast(recordMetaData))
        }
    }
}
