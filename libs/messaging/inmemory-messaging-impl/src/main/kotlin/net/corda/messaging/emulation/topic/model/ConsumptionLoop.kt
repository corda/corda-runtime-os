package net.corda.messaging.emulation.topic.model

import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger

internal class ConsumptionLoop(
    private val consumerDefinitions: ConsumerDefinitions,
    private val group: ConsumerGroup,
) : Runnable {
    companion object {
        private val logger: Logger = contextLogger()
    }
    private fun readRecords(): Map<Partition, Collection<RecordMetadata>> {
        return group.getPartitions(consumerDefinitions)
            .map { (partition, offset) ->
                partition to partition.getRecordsFrom(offset, group.subscriptionConfig.partitionPollSize)
            }.filter {
                it.second.isNotEmpty()
            }.toMap()
    }

    private fun processRecords(records: Map<Partition, Collection<RecordMetadata>>) {
        if (records.isNotEmpty()) {
            @Suppress("TooGenericExceptionCaught")
            try {
                consumerDefinitions.handleRecords(
                    records
                        .values
                        .flatten()
                )
                records.forEach { (partition, records) ->
                    group.commitRecord(partition, records)
                }
            } catch (e: Exception) {
                val recordsAsString = records.values
                    .flatten()
                    .joinToString {
                        "${it.partition}/${it.offset}"
                    }
                logger.warn(
                    "Error processing records for consumer ${consumerDefinitions.groupName}, topic ${consumerDefinitions.groupName}. " +
                        "Will try again records ($recordsAsString)",
                    e
                )
            }
        } else {
            group.waitForData()
        }
    }

    override fun run() {
        while (group.isConsuming(consumerDefinitions)) {
            processRecords(readRecords())
        }
    }
}
