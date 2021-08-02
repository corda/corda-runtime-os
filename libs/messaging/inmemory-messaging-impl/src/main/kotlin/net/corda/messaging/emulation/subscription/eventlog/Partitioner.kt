package net.corda.messaging.emulation.subscription.eventlog

import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

internal class Partitioner(
    private val partitionAssignmentListener: PartitionAssignmentListener?) :
        (Record<*, *>) -> Int {
    companion object {
        @Suppress("ForbiddenComment")
        // TODO: Where do I get those?
        private const val PARTITIONS_COUNT = 10

    }
    private val assignedPartitions = ConcurrentHashMap<String, MutableCollection<Int>>()

    override fun invoke(record: Record<*, *>): Int {
        val partition = abs(record.key.hashCode() % PARTITIONS_COUNT)
        val newPartition = assignedPartitions.computeIfAbsent(record.topic) {
            ConcurrentHashMap.newKeySet()
        }.add(partition)
        if(newPartition) {
            partitionAssignmentListener?.onPartitionsAssigned(listOf(record.topic to partition))
        }

        return partition
    }
}