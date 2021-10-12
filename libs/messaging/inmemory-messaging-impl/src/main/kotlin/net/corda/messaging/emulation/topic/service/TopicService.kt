package net.corda.messaging.emulation.topic.service

import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.topic.model.Consumer
import net.corda.messaging.emulation.topic.model.Consumption

/**
 * Service to interact with the kafka topic emulator
 */
interface TopicService {
    /**
     * Add a list of records to each of their topics.
     * This operation is done atomically per partition.
     */
    fun addRecords(records: List<Record<*, *>>)

    /**
     * Add a list of records to each of their topics with specific partition number.
     * This operation is done atomically per partition..
     */
    fun addRecordsToPartition(records: List<Record<*, *>>, partition: Int)

    /**
     * Create a consumption for a given [consumer].
     * If the topic does not exist it is created.
     * To unsubscribe, close the returned lifecycle
     */
    fun createConsumption(consumer: Consumer): Consumption

    /**
     * Get the latest added offsets of a specific [topicName].
     *
     * Return a map from a partition ID to the offset of that partition (-1 if no record was added).
     */
    fun getLatestOffsets(topicName: String): Map<Int, Long>

    /**
     * assign partitions to a consumer manually.
     *
     * The consumer must have manual partition assignment set.
     */
    fun manualAssignPartitions(consumer: Consumer, partitionsIds: Collection<Int>)

    /**
     * un-assign partitions to a consumer manually.
     *
     * The consumer must have manual partition assignment set.
     */
    fun manualUnAssignPartitions(consumer: Consumer, partitionsIds: Collection<Int>)
}
