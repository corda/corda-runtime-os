package net.corda.messaging.api.processor

import net.corda.messaging.api.records.Record
import net.corda.messaging.api.records.StateAndEventRecord

/**
 * A processor of events which also have a state from a durable subscription. Consumer processors
 * of state and event subscriptions should implement this interface.
 */
interface StateAndEventProcessor<K, S, E> {

    /**
     * Process a [stateAndEvent] pair of records and produce a list of new records.
     * @return a pair of state and event records. State will be of the same type as the input state.
     * Events can be of different key and value types intended to be put on different topics.
     */
    fun onNext(stateAndEvent: StateAndEventRecord<K, S, E>): Pair<Record<K, S>,List<Record<*, *>>>

    /**
     * [keyClass], [stateValueClass] and [eventValueClass] to easily get the class types the processor operates upon.
     */
    val keyClass: Class<K>
    val stateValueClass: Class<S>
    val eventValueClass: Class<E>
}