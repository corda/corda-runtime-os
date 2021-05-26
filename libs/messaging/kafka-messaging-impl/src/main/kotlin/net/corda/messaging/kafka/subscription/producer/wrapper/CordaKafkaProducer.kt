package net.corda.messaging.kafka.subscription.producer.wrapper

import net.corda.messaging.api.records.Record
import org.apache.kafka.clients.producer.Producer

interface CordaKafkaProducer : AutoCloseable, Producer<Any, Any> {

    /**
     * Send [records] of varying key and value types to their respective topics
     */
    fun sendRecords(records: List<Record<*, *>>)

    /**
     * Send the offsets of the records consumed back to kafka.
     * @throws CordaMessageAPIFatalException Fatal error
     * @throws CordaMessageAPIIntermittentException Retryable error
     */
    fun sendOffsetsToTransaction()

    /**
     * Try to commit a transaction. If the transaction fails. Abort it.
     * @throws CordaMessageAPIFatalException Fatal error
     * @throws CordaMessageAPIIntermittentException Retryable error
     */
    fun tryCommitTransaction()
}
