package net.corda.messaging.api.processor

import net.corda.messaging.api.records.Record
import java.util.concurrent.Future

/**
 * This interface defines a processor of events from a non durable [PubSubSubscription] with a feed key
 * of type [K] and value of type [V].
 *
 * If you want to receive events from a from [PubSubSubscription] you should implement this interface.
 *
 * NOTE: Any exception thrown by the processor which isn't [CordaIntermittentException] will result in a
 * [CordaFatalException] and will cause the subscription to close
 */
interface PubSubProcessor<K : Any, V : Any> {

    /**
     * Implement this method to receive the next [event] record from the subscription feed.
     * The subscription will invoke [onNext] on the processor for the current batch of records, wait for the completion of the returned
     * futures and then continue with the next batch of records. The processor is responsible for imposing any timeouts required on the
     * returned futures. Any errors thrown from the returned futures will be logged, but the subscription will continue processing records.
     * The subscription waiting on the completion of the current batch creates some degree of back-pressure. If this blocking behaviour is
     * not necessary, the processor can return a completed future (e.g. `CompletableFuture.completedFuture(Unit)`).
     */
    fun onNext(event: Record<K, V>): Future<Unit>

    /**
     * [keyClass] and [valueClass] to easily get the class types the processor operates on.
     *
     * Override these values with the classes for [K] and [V] for your specific subscription.
     */
    val keyClass: Class<K>
    val valueClass: Class<V>
}

