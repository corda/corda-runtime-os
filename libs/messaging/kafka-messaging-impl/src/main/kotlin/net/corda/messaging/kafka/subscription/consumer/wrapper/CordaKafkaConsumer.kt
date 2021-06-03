package net.corda.messaging.kafka.subscription.consumer.wrapper

import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.records.Record
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.TopicPartition
import java.nio.ByteBuffer
import java.time.Duration

data class ConsumerRecordAndMeta<K : Any, V : Any>(
    val topicPrefix: String,
    val record: ConsumerRecord<K, V>,
)

fun <K : Any, V : Any> ConsumerRecordAndMeta<K, V>.asRecord(): Record<K, V> {
    val topic = record.topic().substringAfter(topicPrefix)
    return Record(topic, record.key(), record.value())
}

/**
 * Wrapper for a Kafka Consumer.
 * Kafka consumers can have any key type [K] and will have values of type [ByteBuffer].
 * [ByteBuffer] values will be deserialized by avro into [V]
 */
interface CordaKafkaConsumer<K : Any, V : Any> : AutoCloseable, Consumer<K, V> {
    /**
     * Poll records from the consumer and sort them by timestamp
     */
    fun poll(): List<ConsumerRecordAndMeta<K, V>>

    /**
     * Reset the consumer position on a topic to the last committed position. Next poll from the topic will read from this position.
     * If no position is found for this consumer on the topic then apply the [offsetStrategy].
     */
    fun resetToLastCommittedPositions(offsetStrategy: OffsetResetStrategy)

    /**
     * Synchronously commit the consumer offset for this [event] back to the topic partition.
     * Record [metaData] about this commit back on the [event] topic.
     * @throws CordaMessageAPIFatalException fatal error occurred attempting to commit offsets.
     */
    fun commitSyncOffsets(event: ConsumerRecord<K, V>, metaData: String? = null)

    /**
     * Subscribe this consumer to the topic.
     * If a recoverable error occurs retry. If max retries is exceeded or a fatal error occurs then throw a [CordaMessageAPIFatalException]
     * @throws CordaMessageAPIFatalException for fatal errors.
     */
    fun subscribeToTopic()

    /**
     * Similar to [KafkaConsumer.partitionsFor] but returning a [TopicPartition].
     */
    fun getPartitions(topic: String, duration: Duration): List<TopicPartition>
}
