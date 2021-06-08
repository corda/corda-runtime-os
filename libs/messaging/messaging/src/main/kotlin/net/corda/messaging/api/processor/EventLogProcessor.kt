package net.corda.messaging.api.processor

import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record

/**
 * Represents a processor of records from a (partitioned) event log.
 * It needs to be implemented and wired up with an event log subscription.
 */
interface EventLogProcessor<K : Any, V : Any> {

    /**
     * Processes a list of [EventLogRecord]s and return a list of new [Record]s to be produced, when needed.
     * The publication of the returned records is guaranteed to be atomic with regards to the consumption of the processed records.
     */
    fun onNext(events: List<EventLogRecord<K, V>>) : List<Record<*, *>>

    /**
     * [keyClass] and [valueClass] to easily get the class types the processor operates on.
     */
    val keyClass: Class<K>
    val valueClass: Class<V>
}