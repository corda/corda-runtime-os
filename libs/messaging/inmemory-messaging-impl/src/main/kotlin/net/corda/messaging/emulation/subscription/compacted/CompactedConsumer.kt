package net.corda.messaging.emulation.subscription.compacted

import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.emulation.topic.model.ConsumerDefinitions
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.PartitionStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata

internal class CompactedConsumer<K : Any, V : Any>(
    private val inMemoryCompactedSubscription: InMemoryCompactedSubscription<K, V>
) : ConsumerDefinitions {
    override val groupName = inMemoryCompactedSubscription.groupName
    override val topicName = inMemoryCompactedSubscription.topicName
    override val offsetStrategy = OffsetStrategy.LATEST
    override val partitionStrategy = PartitionStrategy.allInFirst
    override val partitionAssignmentListener = object : PartitionAssignmentListener {
        override fun onPartitionsUnassigned(topicPartitions: List<Pair<String, Int>>) {
        }

        override fun onPartitionsAssigned(topicPartitions: List<Pair<String, Int>>) {
            if (topicPartitions.isNotEmpty()) {
                inMemoryCompactedSubscription.updateSnapshots()
            }
        }
    }

    override fun handleRecords(records: Collection<RecordMetadata>) {
        records.mapNotNull {
            it.castToType(
                inMemoryCompactedSubscription.processor.keyClass,
                inMemoryCompactedSubscription.processor.valueClass
            )
        }.forEach {
            inMemoryCompactedSubscription.onNewRecord(it)
        }
    }
}
