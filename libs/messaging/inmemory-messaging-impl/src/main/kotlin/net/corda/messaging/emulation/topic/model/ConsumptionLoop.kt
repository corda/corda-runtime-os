package net.corda.messaging.emulation.topic.model

import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

internal class ConsumptionLoop(
    private val consumer: Consumer,
    private val group: ConsumerGroup,
) : Runnable {
    companion object {
        private val logger: Logger = contextLogger()
    }
    private val myOffsets = ConcurrentHashMap<Partition, Long>()
    private fun readRecords(): Map<Partition, Collection<RecordMetadata>> {
        return group.getPartitions(consumer)
            .map { (partition, commitedOffset) ->
                val offset = if (consumer.commitStrategy == CommitStrategy.COMMIT_AFTER_PROCESSING)
                    commitedOffset
                else
                    myOffsets[partition] ?: 0
                partition to partition.getRecordsFrom(offset, group.pollSizePerPartition)
            }.filter {
                it.second.isNotEmpty()
            }.toMap()
    }

    private fun commitRecords(records: Map<Partition, Collection<RecordMetadata>>) {
        val commits = records.mapValues { it.value.maxOf { it.offset } + 1 }

        if (consumer.commitStrategy == CommitStrategy.COMMIT_AFTER_PROCESSING) {
            group.commit(commits)
        } else {
            myOffsets += commits
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
            processRecords(readRecords())
        }
    }
}
