package net.corda.messaging.api.publisher

import net.corda.v5.base.concurrent.CordaFuture
import net.corda.messaging.api.records.Record

/**
 * Interface for publishing records to topics.
 */
interface Publisher<K, V> {

    /**
     * Publish a [record]
     * @return a corda future indicating where the publish to a topic was successful.
     */
    fun publish(record: Record<K, V>) : CordaFuture<Boolean>
}