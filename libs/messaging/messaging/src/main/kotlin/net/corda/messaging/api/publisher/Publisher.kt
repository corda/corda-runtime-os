package net.corda.messaging.api.publisher

import net.corda.messaging.api.records.Record
import net.corda.v5.base.concurrent.CordaFuture

/**
 * Interface for publishing records to topics. Consumer libraries will not implement this interface.
 * Publisher instances can be created via the [PublisherFactory].
 */
interface Publisher<K : Any, V : Any> : AutoCloseable {

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
     * Publish the specified [record].
     * @return A corda future which will be completed once the record has been published successfully.
     *   If a fatal error occurs, then an exception of type [CordaMessageAPIFatalException] will be thrown and the publisher will be closed.
     *   If a temporary error occurs and can be retried, then an exception of type [CordaMessageAPIIntermittentException] will be thrown.
     */
    fun publish(record: Record<K, V>) : CordaFuture<Unit>

    /**
     * Publish the specified [record] to the specified [partition].
     * @return A corda future which will be completed once the record has been published successfully.
     *   If a fatal error occurs, then an exception of type [CordaMessageAPIFatalException] will be thrown and the publisher will be closed.
     *   If a temporary error occurs and can be retried, then an exception of type [CordaMessageAPIIntermittentException] will be thrown.
     */
    fun publishToPartition(record: Record<K, V>, partition: Int): CordaFuture<Unit>
}