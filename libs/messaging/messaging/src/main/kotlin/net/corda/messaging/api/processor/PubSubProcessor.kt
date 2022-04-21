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
     * The processor will process a batch of events, calling [onNext] for each event.
     * The next batch, will only be processed after the futures returned by [onNext] have completed.
     * If blocking the next batch, is unnecessary then a CompletableFuture.completedFuture(Unit) should be returned.
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

