package net.corda.messaging.kafka.producer.wrapper

import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import java.time.Duration
import java.util.concurrent.Future

interface CordaKafkaProducer : AutoCloseable {

    /**
     * Send a [record] to kafka with a [callback]
     */
    fun send(record: ProducerRecord<Any, Any>, callback: Callback?): Future<RecordMetadata>

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
     * Send the [consumer] offsets back to kafka for a list of given [records].
     * @throws CordaMessageAPIFatalException Fatal error
     * @throws CordaMessageAPIIntermittentException Retryable error
     */
    fun sendRecordOffsetsToTransaction(consumer: CordaKafkaConsumer<*, *>, records: List<ConsumerRecord<*, *>>)


    /**
     * Send the [consumer] offsets back to kafka for the last consumer poll position.
     * @throws CordaMessageAPIFatalException Fatal error
     * @throws CordaMessageAPIIntermittentException Retryable error
     */
    fun sendAllOffsetsToTransaction(consumer: CordaKafkaConsumer<*, *>)

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
