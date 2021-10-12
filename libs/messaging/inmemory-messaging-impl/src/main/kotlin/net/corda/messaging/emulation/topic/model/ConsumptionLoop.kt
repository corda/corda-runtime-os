package net.corda.messaging.emulation.topic.model

import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.read

internal class ConsumptionLoop(
    private val consumer: Consumer,
    private val group: ConsumerGroup,
) : Runnable {

    companion object {
        private val logger: Logger = contextLogger()
    }
    private val partitionToLastReadOffset = ConcurrentHashMap<Partition, Long>()

    private fun readRecords(): Map<Partition, Collection<RecordMetadata>> {
        return group.lock.read {
            partitionToLastReadOffset.asSequence()
                .map { (partition, offset) ->
                    partition to partition.getRecordsFrom(offset, group.pollSizePerPartition)
                }.filter {
                    it.second.isNotEmpty()
                }.toMap()
        }
    }

    private fun commitRecords(records: Map<Partition, Collection<RecordMetadata>>) {
        val commits = records.mapValues { record ->
            record.value.maxOf { it.offset } + 1
        }
        if (consumer.commitStrategy == CommitStrategy.COMMIT_AFTER_PROCESSING) {
            group.commit(commits)
        }
        partitionToLastReadOffset.replaceAll { partition, offset ->
            commits[partition] ?: offset
        }
    }

    private fun processRecords(records: Map<Partition, Collection<RecordMetadata>>) {
        if (records.isNotEmpty()) {
            @Suppress("TooGenericExceptionCaught")
            try {
                consumer.handleRecords(
                    records
                        .values
                        .flatten()
                )
                commitRecords(records)
            } catch (e: Exception) {
                val recordsAsString = records.values
                    .flatten()
                    .joinToString {
                        "${it.partition}/${it.offset}"
                    }
                logger.warn(
                    "Error processing records for consumer ${consumer.groupName}, topic ${consumer.groupName}. " +
                        "Will try again records ($recordsAsString)",
                    e
                )
            }
        } else {
            group.waitForData()
        }
    }

    override fun run() {
        while (group.isConsuming(consumer)) {
            val records = readRecords()
            processRecords(records)
        }
    }

    fun addPartitions(partitionToOffset: Map<Partition, Long>) {
        partitionToLastReadOffset += partitionToOffset
        consumer.partitionAssignmentListener?.onPartitionsAssigned(
            partitionToOffset.keys.map { consumer.topicName to it.partitionId }
        )
    }

    fun removePartitions(partitionsToRemove: Collection<Partition>) {
        if (partitionsToRemove.isNotEmpty()) {
            consumer.partitionAssignmentListener?.onPartitionsUnassigned(
                partitionsToRemove.map { consumer.topicName to it.partitionId }
            )
            partitionToLastReadOffset -= partitionsToRemove
        }
    }

    val partitions
        get() = partitionToLastReadOffset.keys
}
