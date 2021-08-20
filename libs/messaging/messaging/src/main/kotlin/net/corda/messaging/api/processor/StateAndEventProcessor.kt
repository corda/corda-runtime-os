package net.corda.messaging.api.processor

import net.corda.messaging.api.records.Record

/**
 * A processor of events from a durable queue which also have a state. Consumer processors
 * of state and event subscriptions should implement this interface.
 * If the processor is slow and exceeds the timeout defined for the processor in the config, the event and state will be placed on dead
 * letter queues.
 * The state in the state topic will be set to null for the given key.
 * The first state for any event will always be null for a key. If the state is null for any subsequent event records with the same key,
 * then this key has been added to the state and event dead letter queues and should be handled by the processor as appropriate.
 */
interface StateAndEventProcessor<K : Any, S : Any, E : Any> {

    data class Response<S: Any> (
        /**
         * The updated state in response to an incoming event from [onNext]
         */
        val updatedState: S?,

        /**
         * A list of events to be published in response to an incoming event from [onNext]
         */
        val responseEvents: List<Record<*, *>>
    )

    /**
     * Called to signal an incoming [event] relating to a given [state].  Implementers are expected to
     * process the event and, if necessary, update the state and return any new events as a result.
     * @return a [Response] which contains the updated state and any subsequent events, both of which will
     * be published.
     *
     * Events can be of different key and value types intended to be put on different topics.
     */
    fun onNext(state: S?, event: Record<K, E>): Response<S>

    /**
     * [keyClass], [stateValueClass] and [eventValueClass] to easily get the class types the processor operates upon.
     */
    val keyClass: Class<K>
    val stateValueClass: Class<S>
    val eventValueClass: Class<E>
}
