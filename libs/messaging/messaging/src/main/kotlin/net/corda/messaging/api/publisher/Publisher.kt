package net.corda.messaging.api.publisher

import net.corda.messaging.api.records.Record
import net.corda.v5.base.concurrent.CordaFuture

/**
 * Interface for publishing records to topics. Consumer libraries will not implement this interface.
 * Publisher instances can be created via the [PublisherFactory].
 */
interface Publisher : AutoCloseable {

    /**
     * Start the publisher.
     * This needs to be called before the publisher is fully operational.
     * It will initialise any resources needed (e.g. connections to underlying datastores).
     *
     * By default, nothing is performed during initialisation.
     * Implementations that need to perform initialisation should override this method.
     */
    fun start() {}

    /**
     * Publish a list of [records].
     * @return A list of corda futures returning true or an exception for each message. Never returns false. If fatal error occurs
     * then exception will be thrown of type [CordaMessageAPIFatalException] and publisher will be closed.
     * If error is temporary and can be retried then exception will be of type [CordaMessageAPIIntermittentException].
     * If publisher is configured for transactions (instanceId is set on publisherConfig) publish is
     * executed synchronously and committed atomically.
     * Transactions will return a future of size 1 indicating success or failure of the transaction.
     * @throws CordaMessageAPIFatalException if record is of the wrong type for this Publisher
     */
    fun publish(records: List<Record<*, *>>): List<CordaFuture<Unit>>

    /**
     * Publish the specified list of records, each one to the specified partition (they key of the map).
     * @param records a list of pairs, where the each pair contains the partition and the record to be written to this partition.
     * @return A list of corda futures which will be completed once the record has been published successfully.
     *   If a fatal error occurs, then an exception of type [CordaMessageAPIFatalException] will be thrown and the publisher will be closed.
     *   If a temporary error occurs and can be retried, then an exception of type [CordaMessageAPIIntermittentException] will be thrown.
     */
    fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CordaFuture<Unit>>
}