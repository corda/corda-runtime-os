package net.corda.messaging.api.publisher

import net.corda.messaging.api.records.Record
import net.corda.v5.base.concurrent.CordaFuture

/**
 * Interface for publishing records to topics. Consumer libraries will not implement this interface.
 * Publisher instances can be created via the [PublisherFactory].
 */
interface Publisher : AutoCloseable {

    /**
     * Publish a list of [record].
     * @return A list of corda futures returning true or an exception for each message. Never returns false. If fatal error occurs
     * then exception will be thrown of type [CordaMessageAPIFatalException] and publisher will be closed.
     * If error is temporary and can be retried then exception will be of type [CordaMessageAPIIntermittentException].
     * If publisher is configured for transactions (instanceId is set on publisherConfig) publish is
     * executed synchronously and committed atomically.
     * Transactions will return a future of size 1 indicating success or failure of the transaction.
     * @throws CordaMessageAPIFatalException if record is of the wrong type for this Publisher
     */
    fun publish(records: List<Record<*, *>>): List<CordaFuture<Boolean>>
}