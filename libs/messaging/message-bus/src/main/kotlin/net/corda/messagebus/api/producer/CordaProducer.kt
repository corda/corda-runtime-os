package net.corda.messagebus.api.producer

import net.corda.messagebus.api.consumer.ConsumerRecord
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messaging.api.records.Record
import java.time.Duration
import javax.security.auth.callback.Callback

/**
 * A Corda client that publishes messages to the underlying message bus.
 */
interface CordaProducer : AutoCloseable {

    /**
     * Send a [record] to the bus with a [callback]
     */
    fun send(record: Record<*, *>, callback: Callback?)


    /**
     * Send a [record] to a specific [partition] on the bus with a [callback]
     */
    fun send(record: Record<*, *>, partition: Int, callback: Callback?)

    /**
     * Send [records] of varying key and value types to their respective topics
     */
    fun sendRecords(records: List<Record<*, *>>)

    /**
     * Send the records to the specified partitions.
     *
     * @param recordsWithPartitions a list of pairs, where the first element is the partition and the second is the record.
     */
    fun sendRecordsToPartitions(recordsWithPartitions: List<Pair<Int, Record<*, *>>>)

    /**
     * Send the [consumer] offsets back to the bus for a list of given [records].
     * @throws CordaMessageAPIFatalException Fatal error
     * @throws CordaMessageAPIIntermittentException Retryable error
     */
    fun sendRecordOffsetsToTransaction(consumer: CordaConsumer<*, *>, records: List<ConsumerRecord<*, *>>)


    /**
     * Send the [consumer] offsets back to the bus for the last consumer poll position.
     * @throws CordaMessageAPIFatalException Fatal error
     * @throws CordaMessageAPIIntermittentException Retryable error
     */
    fun sendAllOffsetsToTransaction(consumer: CordaConsumer<*, *>)

    /**
     * Try to commit a transaction. If the transaction fails. Abort it.
     * @throws CordaMessageAPIFatalException Fatal error
     * @throws CordaMessageAPIIntermittentException Retryable error
     */
    fun commitTransaction()

    /**
     * Starts up the transaction
     */
    fun beginTransaction()

    /**
     * Aborts the transaction
     */
    fun abortTransaction()

    /**
     * Close the Producer with a [timeout]
     * @param timeout
     */
    fun close(timeout: Duration)
}
