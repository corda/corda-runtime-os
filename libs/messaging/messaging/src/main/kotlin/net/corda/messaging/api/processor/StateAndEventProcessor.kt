package net.corda.messaging.api.processor

import net.corda.messaging.api.records.Record

/**
 * A processor of events which also have a state from a durable subscription. Consumer processors
 * of state and event subscriptions should implement this interface.
 */
interface StateAndEventProcessor<K : Any, S : Any, E : Any> {

    interface Response<S: Any> {
        /**
         * The updated state in response to an incoming event from [onNext]
         */
        val updatedState: S?

        /**
         * A list of events to be published in response to an incoming event from [onNext]
         */
        val responseEvents: List<Record<*, *>>
    }

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
