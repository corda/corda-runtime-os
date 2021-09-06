package net.corda.messaging.emulation.subscription.eventlog

import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.emulation.topic.model.CommitStrategy
import net.corda.messaging.emulation.topic.model.Consumer
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.PartitionStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata

class EventLogConsumer<K : Any, V : Any>(
    private val subscription: EventLogSubscription<K, V>,
) : Consumer {
    override val offsetStrategy = OffsetStrategy.EARLIEST
    override val groupName: String = subscription.groupName
    override val topicName: String = subscription.topicName
    override val partitionAssignmentListener: PartitionAssignmentListener? = subscription.partitionAssignmentListener
    override val commitStrategy = CommitStrategy.COMMIT_AFTER_PROCESSING
    override val partitionStrategy = PartitionStrategy.DIVIDE_PARTITIONS

    override fun handleRecords(records: Collection<RecordMetadata>) {
        subscription.processor.onNext(
            records.mapNotNull { it.toRecord() }.toList()
        )
    }

    private fun RecordMetadata.toRecord(): EventLogRecord<K, V>? {
        val record = castToType(subscription.processor.keyClass, subscription.processor.valueClass)
        return if (record != null) {
            EventLogRecord(
                subscription.topicName,
                record.key,
                record.value,
                this.partition,
                this.offset
            )
        } else {
            null
        }
    }
}
