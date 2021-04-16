package net.corda.messaging.api.publisher

import net.corda.v5.base.concurrent.CordaFuture
import net.corda.messaging.api.records.Record

/**
 * Interface for publishing records to topics. Consumer libraries will not implement this interface.
 * Publisher instances can be created via the [PublisherFactory].
 */
interface Publisher<K, V> {

    /**
     * Publish a [record].
     * @return A corda future indicating whether the publish to a topic was successful.
     */
    fun publish(record: Record<K, V>) : CordaFuture<Boolean>
}