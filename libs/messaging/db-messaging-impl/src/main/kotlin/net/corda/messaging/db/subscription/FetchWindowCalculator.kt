package net.corda.messaging.db.subscription

import net.corda.messaging.db.persistence.FetchWindow
import net.corda.messaging.db.sync.OffsetTrackersManager
import kotlin.math.floor

class FetchWindowCalculator(private val offsetTrackersManager: OffsetTrackersManager) {

    fun calculateWindows(topic: String, totalBatchSize: Int, partitionsWithCommittedOffsets: Map<Int, Long>): List<FetchWindow> {
        val offsetsPerPartition = partitionsWithCommittedOffsets.map { (partition, maxCommittedOffset) ->
            val maxVisibleOffsetForPartition = offsetTrackersManager.maxVisibleOffset(topic, partition)
            partition to (maxCommittedOffset + 1 to maxVisibleOffsetForPartition)
        }.toMap()
        val totalRecords = offsetsPerPartition.map { (_, offsets) ->
            offsets.second - offsets.first + 1
        }.sum()
        if (totalRecords == 0L) {
            return emptyList()
        }

        // split records proportionally depending on the available records on each partitions.
        var remainingRecords = totalBatchSize
        val windows = offsetsPerPartition.map { (partition, offsets) ->
            val partitionRecords = offsets.second - offsets.first + 1
            val maxNumberOfRecordsForPartition = floor((partitionRecords.toDouble() / totalRecords.toDouble()) * totalBatchSize).toInt()
            remainingRecords -= maxNumberOfRecordsForPartition
            FetchWindow(partition, offsets.first, offsets.second, maxNumberOfRecordsForPartition)
        }

        // allocate remaining records
        return windows.map {
            if (remainingRecords > 0) {
                remainingRecords--
                it.copy(limit = it.limit + 1)
            } else {
                it
            }
        }.filter { it.limit > 0 }
    }

}