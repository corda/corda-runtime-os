package net.corda.messaging.api.processor

import net.corda.messaging.api.records.Record

/**
 * This interface defines a processor of events from a [StateAndEventSubscription] on a feed with keys of type [K],
 * states of type [S], and Events of type [E].
 *
 * If you want to receive updates from a [StateAndEventSubscription] you should implement this interface.
 *
 * If the processor is slow and exceeds the timeout defined for the processor in the config, the event and state will be placed on dead
 * letter queues. The state will be set to null for the given key.
 * Note: The first state for any event will always be null for a key. If the state is null for any subsequent event records with the same
 * key, then this key has been added to the state and event dead letter queues and should be handled by the processor as appropriate.
 *
 * NOTE: Any exception thrown by the processor which isn't [CordaIntermittentException] will result in a
 * [CordaFatalException] and will cause the subscription to close.
 */
interface StateAndEventProcessor<K : Any, S : Any, E : Any> {

    /**
     * This class encapsulates the responses that will be returned (from [onNext]) to the subscription for
     * further publishing.
     */
    data class Response<S : Any>(
        /**
         * The updated state in response to an incoming event from [onNext].
         */
        val updatedState: S?,

        /**
         * A list of events to be published in response to an incoming event from [onNext].
         *
         * NOTE: The [responseEvents] can be of any type and are not required to match [K] or [E] on input.
         */
        val responseEvents: List<Record<*, *>>,

        /**
         * Flag to indicate whether processing succeeded or failed.
         */
        val processingStatus: ProcessingStatus = ProcessingStatus.SUCCESS
    ) {
        enum class ProcessingStatus {
            SUCCESS,

            /**
             * The processing failed in such a way the Flow targeted by the event should be sent to the DLQ. This means
             * the Flow is in an internally invalid state and could no longer operate correctly.
             */
            SEND_TO_DLQ,

            /**
             * The processing failed in such a way the event was malformed or unable to be matched to an existing state.
             * In this case there is no Flow to send to the DLQ, and no states should be written as this would involve
             * 'inventing' a state.
             */
            STRAY_EVENT
        }
    }

    /**
     * Called to signal an incoming [event] relating to a given [state].  Implementers are expected to
     * process the event and, if necessary, update the state and return any new events as a result.
     *
     * @param state the current state (if any) for the [event] key.  If no state exists then [null].
     * @param event the event which triggered this update.
     * @return a [Response] which contains the updated state and any subsequent events, both of which will
     * be published.
     *
     * Output events can be of different key and value types intended to be put on different topics.
     *
     * NOTE: The returned events will be published and the processed events will be consumed atomically as a
     * single transaction.
     */
    fun onNext(state: S?, event: Record<K, E>): Response<S>

    /**
     * [keyClass], [stateValueClass] and [eventValueClass] to easily get the class types the processor operates upon.
     *
     * Override these values with the classes for [K], [S], and [E] for your specific subscription.
     */
    val keyClass: Class<K>
    val stateValueClass: Class<S>
    val eventValueClass: Class<E>
}
