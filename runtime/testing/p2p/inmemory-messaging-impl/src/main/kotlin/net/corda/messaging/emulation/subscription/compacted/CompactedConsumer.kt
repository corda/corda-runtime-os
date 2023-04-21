package net.corda.messaging.emulation.subscription.compacted

import net.corda.messaging.emulation.topic.model.CommitStrategy
import net.corda.messaging.emulation.topic.model.Consumer
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.PartitionStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata

internal class CompactedConsumer<K : Any, V : Any>(
    private val inMemoryCompactedSubscription: InMemoryCompactedSubscription<K, V>
) : Consumer {
    override val groupName = inMemoryCompactedSubscription.groupName
    override val topicName = inMemoryCompactedSubscription.topicName
    override val offsetStrategy = OffsetStrategy.EARLIEST
    override val commitStrategy = CommitStrategy.NO_COMMIT
    override val partitionStrategy = PartitionStrategy.SHARE_PARTITIONS
    override val partitionAssignmentListener = null

    override fun handleRecords(records: Collection<RecordMetadata>) {
        records.forEach {
            inMemoryCompactedSubscription.onNewRecord(it)
        }
    }
}
