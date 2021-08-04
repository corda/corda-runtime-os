package net.corda.messaging.emulation.subscription.eventlog

import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * A utility to assign partition to record.
 *
 * @property partitionAssignmentListener - Optional listener to any new partition.
 * @property partitionCounts - the number of partitions.
 */
internal class Partitioner(
    private val partitionAssignmentListener: PartitionAssignmentListener?,
    private val partitionCounts: Int
) :
    (Record<*, *>) -> Int {
    private val assignedPartitions = ConcurrentHashMap<String, MutableCollection<Int>>()

    override fun invoke(record: Record<*, *>): Int {
        val partition = abs(record.key.hashCode() % partitionCounts)
        val newPartition = assignedPartitions.computeIfAbsent(record.topic) {
            ConcurrentHashMap.newKeySet()
        }.add(partition)
        if (newPartition) {
            partitionAssignmentListener?.onPartitionsAssigned(listOf(record.topic to partition))
        }

        return partition
    }
}
