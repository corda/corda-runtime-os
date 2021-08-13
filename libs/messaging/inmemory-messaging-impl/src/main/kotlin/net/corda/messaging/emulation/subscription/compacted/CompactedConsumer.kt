package net.corda.messaging.emulation.subscription.compacted

import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.emulation.topic.model.Consumer
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata
import net.corda.v5.base.util.uncheckedCast

internal class CompactedConsumer<K : Any, V : Any>(
    private val inMemoryCompactedSubscription: InMemoryCompactedSubscription<K, V>
) : Consumer {
    override val groupName = inMemoryCompactedSubscription.groupName
    override val topicName = inMemoryCompactedSubscription.topicName
    override val offsetStrategy = OffsetStrategy.LATEST
    override val partitionAssignmentListener = object : PartitionAssignmentListener {
        override fun onPartitionsUnassigned(topicPartitions: List<Pair<String, Int>>) {
            inMemoryCompactedSubscription.updateSnapshots()
        }

        override fun onPartitionsAssigned(topicPartitions: List<Pair<String, Int>>) {
            inMemoryCompactedSubscription.updateSnapshots()
        }
    }

    override fun handleRecords(records: Collection<RecordMetadata>) {
        records.map {
            it.record
        }.filter {
            inMemoryCompactedSubscription.processor.keyClass.isInstance(it.key)
        }.filter {
            inMemoryCompactedSubscription.processor.valueClass.isInstance(it.value)
        }.forEach {
            inMemoryCompactedSubscription.gotRecord(uncheckedCast(it))
        }
    }
}
