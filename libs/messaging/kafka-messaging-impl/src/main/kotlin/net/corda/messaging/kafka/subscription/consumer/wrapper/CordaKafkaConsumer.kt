package net.corda.messaging.kafka.subscription.consumer.wrapper

import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
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

fun <K: Any, V: Any> ConsumerRecordAndMeta<K, V>.asEventLogRecord(): EventLogRecord<K, V> {
    val topic = record.topic().substringAfter(topicPrefix)
    return EventLogRecord(topic, record.key(), record.value(), record.partition(), record.offset())
}

/**
 * Wrapper for a Kafka Consumer.
 * Kafka consumers can have any key type [K] and will have values of type [ByteBuffer].
 * [ByteBuffer] values will be deserialized by avro into [V]
 */
@Suppress("TooManyFunctions")
interface CordaKafkaConsumer<K : Any, V : Any> : AutoCloseable {
    /**
     * Subscribe to given [topics] with partition [listener]
     */
    fun subscribe(topics: Collection<String>, listener: ConsumerRebalanceListener?)

    /**
     * Subscribe this consumer to the topic. Attach the rebalance [listener] to the [Consumer].
     * If [listener] is null the default listener created via the [ConsumerBuilder] is used.
     * If a recoverable error occurs retry. If max retries is exceeded or a fatal error occurs then throw a [CordaMessageAPIFatalException]
     * @throws CordaMessageAPIFatalException for fatal errors.
     */
    fun subscribeToTopic(listener: ConsumerRebalanceListener? = null)

    /**
     * Assign the given [partitions]
     */
    fun assign(partitions: Collection<TopicPartition>)

    /**
     * Get current assignments
     */
    fun assignment(): Set<TopicPartition>

    /**
     * Get current position for [partition]
     * @param partition
     */
    fun position(partition: TopicPartition): Long

    /**
     * Seek given [offset] for a given [partition]
     */
    fun seek(partition: TopicPartition, offset: Long)

    /**
     * Seek the first offset for the given [partitions]
     */
    fun seekToBeginning(partitions: Collection<TopicPartition>)

    /**
     * Get beginning offsets for [partitions]
     */
    fun beginningOffsets(partitions: Collection<TopicPartition>): Map<TopicPartition, Long>

    /**
     * Get end offsets for [partitions]
     * @param partitions
     */
    fun endOffsets(partitions: Collection<TopicPartition>): Map<TopicPartition, Long>

    /**
     * Resume the given [partitions]
     * @param partitions
     */
    fun resume(partitions: Collection<TopicPartition>)

    /**
     * Pause the given [partitions]
     * @param partitions
     */
    fun pause(partitions: Collection<TopicPartition>)

    /**
     * Get the paused partitions
     */
    fun paused(): Set<TopicPartition>

    /**
     * Poll records from the consumer and sort them by timestamp
     */
    fun poll(): List<ConsumerRecordAndMeta<K, V>>

    /**
     * Poll records from the consumer and sort them by timestamp with a [timeout]
     */
    fun poll(timeout: Duration): List<ConsumerRecordAndMeta<K, V>>

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
     * Similar to [KafkaConsumer.partitionsFor] but returning a [TopicPartition].
     */
    fun getPartitions(topic: String, duration: Duration): List<TopicPartition>

    /**
     * Manually assigns the specified partitions of the configured topic to this consumer.
     *
     * Note: manual assignment is an alternative to subscription, where Kafka does not execute any partition assignment logic and lets
     * the client assign partitions. So, do not use this in conjunction with [subscribeToTopic] as they satisfy different use-cases.
     */
    fun assignPartitionsManually(partitions: Set<Int>)

    /**
     * Get group metadata
     */
    fun groupMetadata(): ConsumerGroupMetadata

    /**
     * Close consumer with a [timeout]
     * @param timeout
     */
    fun close(timeout: Duration)
}
