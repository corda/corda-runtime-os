package net.corda.messaging.emulation.subscription.compacted

import net.corda.messaging.emulation.topic.model.ConsumerDefinitions
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata
import java.util.concurrent.atomic.AtomicInteger

internal class CompactedConsumer<K : Any, V : Any>(
    private val inMemoryCompactedSubscription: InMemoryCompactedSubscription<K, V>
) : ConsumerDefinitions {
    companion object {
        private val groupNamePrefix = AtomicInteger(0)
    }
    private val myIndex = groupNamePrefix.incrementAndGet()
    override val groupName = "${inMemoryCompactedSubscription.groupName}.compacted.$myIndex"
    override val topicName = inMemoryCompactedSubscription.topicName
    override val offsetStrategy = OffsetStrategy.EARLIEST
    override val partitionAssignmentListener = null

    override fun handleRecords(records: Collection<RecordMetadata>) {
        records.forEach {
            inMemoryCompactedSubscription.onNewRecord(it)
        }
    }
}
