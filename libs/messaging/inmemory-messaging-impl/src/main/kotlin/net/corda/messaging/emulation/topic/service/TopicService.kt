package net.corda.messaging.emulation.topic.service

import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.topic.model.Consumer
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
     * Add a list of records to each of their topics with specific partition number.
     * This operation is done atomically.
     * Topics are locked while writing to them.
     */
    fun addRecordsToPartition(records: List<Record<*, *>>, partition: Int)

    /**
     * Subscribe to with the given [consumer].
     * If the topic does not exist it is created.
     * To unsubscribe, close the returned lifecycle
     */
    fun subscribe(consumer: Consumer): Lifecycle

    fun handleAllRecords(topicName: String, handler: (Sequence<RecordMetadata>) -> Unit)
}
