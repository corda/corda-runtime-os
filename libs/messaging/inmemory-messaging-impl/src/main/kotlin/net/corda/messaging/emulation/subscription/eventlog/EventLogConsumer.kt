package net.corda.messaging.emulation.subscription.eventlog

import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.emulation.topic.model.Consumer
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata
import net.corda.v5.base.util.uncheckedCast

class EventLogConsumer<K : Any, V : Any>(
    private val subscription: EventLogSubscription<K, V>,
) : Consumer {
    override val offsetStrategy = OffsetStrategy.EARLIEST
    override val groupName: String = subscription.groupName
    override val topicName: String = subscription.topicName
    override val partitionAssignmentListener: PartitionAssignmentListener? = subscription.partitionAssignmentListener

    override fun handleRecords(records: Collection<RecordMetadata>) {
        subscription.processor.onNext(
            records.filter {
                subscription.processor.keyClass.isInstance(it.record.key)
            }.filter {
                subscription.processor.valueClass.isInstance(it.record.value)
            }.map {
                it.toRecord()
            }.toList()
        )
    }

    private fun RecordMetadata.toRecord(): EventLogRecord<K, V> {
        return EventLogRecord(
            subscription.topicName,
            uncheckedCast(this.record.key),
            uncheckedCast(this.record.value),
            this.partition,
            this.offset
        )
    }
}
