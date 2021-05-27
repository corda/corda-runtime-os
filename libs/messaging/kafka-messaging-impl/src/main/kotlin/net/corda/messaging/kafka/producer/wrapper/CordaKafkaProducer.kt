package net.corda.messaging.kafka.producer.wrapper

import net.corda.messaging.api.records.Record
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer

interface CordaKafkaProducer : AutoCloseable, Producer<Any, Any> {

    /**
     * Send [records] of varying key and value types to their respective topics
     */
    fun sendRecords(records: List<Record<*, *>>)

    /**
     * Send the [consumer] offsets of the records consumed back to kafka.
     * @throws CordaMessageAPIFatalException Fatal error
     * @throws CordaMessageAPIIntermittentException Retryable error
     */
    fun sendOffsetsToTransaction(consumer: Consumer<*, *>)

    /**
     * Try to commit a transaction. If the transaction fails. Abort it.
     * @throws CordaMessageAPIFatalException Fatal error
     * @throws CordaMessageAPIIntermittentException Retryable error
     */
    fun tryCommitTransaction()
}
