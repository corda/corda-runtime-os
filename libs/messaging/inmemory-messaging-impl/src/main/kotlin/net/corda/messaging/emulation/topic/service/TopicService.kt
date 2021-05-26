package net.corda.messaging.emulation.topic.service

import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata

/**
 * Service to interact with the kafka topic emulator
 */
interface TopicService {
    /**
     * Add a list of records to each of their topics.
     * This operation is done atomically.
     * Topics are locked while writing to them.
     */
    fun addRecords(records: List<Record<*, *>>)

    /**
     * Get list of records of max size [numberOfRecords] for the given [topicName] and [consumerGroup].
     * Set [autoCommitOffset] to true to update [consumerGroup] position in the topic automatically.
     * Otherwise set to false and commit back offsets using [commitOffset]
     */
    fun getRecords(topicName: String, consumerGroup: String, numberOfRecords: Int, autoCommitOffset: Boolean = true) : List<RecordMetadata>

    /**
     * Commit the [offset] to a topic for the given [topicName], [consumerGroup]
     */
    fun commitOffset(topicName: String, consumerGroup: String, offset: Long)

    /**
     * Subscribe to a [topicName] with the given [consumerGroup] and [offsetStrategy]
     * If the topic does not exist it is created.
     */
    fun subscribe(topicName: String, consumerGroup: String, offsetStrategy: OffsetStrategy)
}
