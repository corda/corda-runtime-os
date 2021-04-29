package net.corda.messaging.kafka.subscription.consumer.wrapper

import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.records.Record
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetResetStrategy

/**
 * Wrapper for a Kafka Consumer.
 */
interface CordaKafkaConsumer<K, V> : AutoCloseable {

    /**
     * Access the kafka consumer directly
     */
    val consumer: Consumer<K, V>

    /**
     * Poll records from the consumer and sort them by timestamp
     */
    fun poll(): List<ConsumerRecord<K, V>>

    /**
     * Reset the consumer position on a topic to the last committed position. Next poll from the topic will read from this position.
     * If no position is found for this consumer on the topic then apply the [offsetStrategy].
     */
    fun resetToLastCommittedPositions(offsetStrategy: OffsetResetStrategy)

    /**
     * Convert a [consumerRecord] to a [Record] and return it.
     * Remove the topicPrefix from the [consumerRecord]
     */
    fun getRecord(consumerRecord: ConsumerRecord<K, V>) : Record<K, V>

    /**
     * Synchronously commit the consumer offset for this [event] back to the topic partition.
     * Record [metaData] about this commit back on the [event] topic.
     */
    fun commitSyncOffsets(event: ConsumerRecord<K, V>, metaData: String? = null)

    /**
     * Subscribe this consumer to the topic.
     * If a recoverable error occurs retry. If max retries is exceeded or a fatal error occurs then throw a [CordaMessageAPIFatalException]
     * @throws CordaMessageAPIFatalException for fatal errors.
     */
    fun subscribeToTopic()
}