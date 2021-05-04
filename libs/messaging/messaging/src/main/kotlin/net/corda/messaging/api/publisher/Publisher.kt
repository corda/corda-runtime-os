package net.corda.messaging.api.publisher

import net.corda.v5.base.concurrent.CordaFuture
import net.corda.messaging.api.records.Record

/**
 * Interface for publishing records to topics. Consumer libraries will not implement this interface.
 * Publisher instances can be created via the [PublisherFactory].
 */
interface Publisher<K, V> : AutoCloseable {

    /**
     * Publish a list of [record].
     * @return A corda future indicating whether the publish to a topic was successful. If fatal error occurs
     * then exception will be of type [CordaMessageAPIFatalException] and publisher will be closed.
     * If error is temporary and can be retried then exception will be of type [CordaMessageAPIIntermittentException].
     */
    fun publish(record: Record<K, V>) : CordaFuture<Boolean>
}