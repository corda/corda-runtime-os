package net.corda.messagebus.api.producer

import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import java.time.Duration

/**
 * A Corda client that publishes messages to the underlying message bus.
 */
interface CordaProducer : AutoCloseable {

    /**
     * Defines the callback for post-send events.  If there was an exception it will be provided on this callback.
     */
    fun interface Callback {
        fun onCompletion(exception: Exception?)
    }

    /**
     * Asynchronously send a record to a topic and invoke the provided callback when the record has been acknowledged.
     *
     * @param record The record to send
     * @param callback A user-supplied callback to execute when the record has been successfully published or if an
     * error has occurred.
     */
    fun send(record: CordaProducerRecord<*, *>, callback: Callback?)


    /**
     * Send a [record] to a specific [partition] on the bus with a [callback]
     *
     * @param record The record to send
     * @param partition The partition on which the record will be published
     * @param callback A user-supplied callback to execute when the record has been successfully published or if an
     * error has occurred
     */
    fun send(record: CordaProducerRecord<*, *>, partition: Int, callback: Callback?)

    /**
     * Send [records] of varying key and value types to their respective topics
     *
     * @param records the list of records to send to be published
     */
    fun sendRecords(records: List<CordaProducerRecord<*, *>>)

    /**
     * Send the records to the specified partitions.
     *
     * @param recordsWithPartitions a list of pairs, where the first element is the partition and the second
     * is the record.
     */
    fun sendRecordsToPartitions(recordsWithPartitions: List<Pair<Int, CordaProducerRecord<*, *>>>)

    /**
     * Add the [consumer] offsets for a list of given [records] to the current transaction.
     * These offsets will be considered committed only if the transaction is committed successfully.
     * The committed offset should be the next message your application will consume,
     * i.e. lastProcessedMessageOffset + 1.
     *
     * @param consumer the consumer which received the records (and whose offsets will be committed).
     * @param records the records which are part of the current transaction
     * @throws CordaMessageAPIFatalException Fatal error
     * @throws CordaMessageAPIIntermittentException Retryable error
     */
    fun sendRecordOffsetsToTransaction(consumer: CordaConsumer<*, *>, records: List<CordaConsumerRecord<*, *>>)

    /**
     * Should be called before the start of each new transaction.
     *
     * All messages sent between the beginTransaction() and commitTransaction() calls will be part of a single
     * transaction. When the transactional.id is specified, all messages sent by the producer must
     * be part of a transaction.
     *
     * The transactional producer uses exceptions to communicate error states. In particular, it is not
     * required to specify callbacks for producer.send() or to call .get() on the returned
     * Future: an exception will be thrown if any of the producer.send() or transactional calls hit
     * an irrecoverable error during a transaction.
     *
     * By calling producer.abortTransaction() upon receiving a KafkaException we can ensure that any successful
     * writes are marked as aborted, hence keeping the transactional guarantees.
     */
    fun beginTransaction()

    /**
     * Marks a list of offsets from the consumer as part of the current transaction. These offsets will
     * be considered committed only if the transaction is committed successfully. The committed offset should
     * be the next message your application will consume, i.e. lastProcessedMessageOffset + 1.
     *
     * @param consumer the consumer whose offsets will be marked
     * @throws CordaMessageAPIFatalException Fatal error
     * @throws CordaMessageAPIIntermittentException Retryable error
     */
    fun sendAllOffsetsToTransaction(consumer: CordaConsumer<*, *>)

    /**
     * Commits the ongoing transaction. This method will flush any unsent records before actually committing
     * the transaction.
     *
     * Further, if any of the [send] calls which were part of the transaction hit irrecoverable errors, this method
     * will throw the last received exception immediately and the transaction will not be committed.
     * So all [send] calls in a transaction must succeed in order for this method to succeed.
     *
     * In certain cases this method will raise a [CordaMesssageAPIIntermittentException]. It is safe to retry in
     * this case, but it is not possible to attempt a different operation (such as abortTransaction) since the commit
     * may already be in the progress of completing. If not retrying, the only option is to close the producer.
     *
     * @throws CordaMessageAPIFatalException Fatal error. You must close this producer
     * @throws CordaMessageAPIIntermittentException Retryable error. You can try to commit again
     */
    fun commitTransaction()

    /**
     * Aborts the ongoing transaction. Any unflushed produce messages will be aborted when this call is made.
     * This call may throw an exception immediately if any prior [send] calls failed.
     */
    fun abortTransaction()

    /**
     * Close this producer.
     *
     * If the producer is unable to complete all requests before the timeout expires, this method will fail
     * any unsent and unacknowledged records immediately. It will also abort the ongoing transaction if it's not
     * already completing.
     *
     * @param timeout The maximum time to wait for producer to complete any pending requests. The value should be
     *                non-negative. Specifying a timeout of zero means do not wait for pending send requests to complete.
     */
    fun close(timeout: Duration)
}
