package net.corda.messaging.emulation.topic.service

import net.corda.lifecycle.LifeCycle
import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.topic.model.Consumer

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
     * Subscribe to with the given [consumer].
     * If the topic does not exist it is created.
     * To unsubscribe, close the returned lifecycle
     */
    fun subscribe(consumer: Consumer): LifeCycle
}
