package net.corda.messaging.kafka.utils

import net.corda.messaging.api.records.Record
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import java.util.Properties

/**
 * Set [kafkaProperties] with the given key [configName] with values from [configurationProperties]. If no value found in
 * [configurationProperties] then set the value using [default].
 */
fun setKafkaProperties(kafkaProperties: Properties, configurationProperties: Map<String, String>, configName: String, default: Any) {
    if (configurationProperties[configName] != null) {
        kafkaProperties[configName] = configurationProperties[configName]
    } else {
        kafkaProperties[configName] = default
    }
}

/**
 * Convert a Kafka ConsumerRecord to a generic Record.
 */
fun <K, V> ConsumerRecord<K, V>.toRecord(): Record<K, V> {
    return Record(this.topic(), this.key(), this.value())
}

/**
 * Convert a generic Record to a Kafka ProducerRecord.
 */
fun <K, V> Record<K, V>.toProducerRecord(): ProducerRecord<K, V> {
    return ProducerRecord(this.topic, this.key, this.value)
}

/**
 * Reset the consumers position on a topic to the last committed position. Next poll from the topic will read from this position.
 * If no position is found for this consumer on the topic then apply the [offsetStrategy].
 */
fun <K, V> Consumer<K, V>.resetToLastCommittedPositions(offsetStrategy: OffsetResetStrategy = OffsetResetStrategy.NONE) {
    val consumer = this
    val committed = consumer.committed(consumer.assignment())
    for (assignment in consumer.assignment()) {
        val offsetAndMetadata = committed[assignment]
        when {
            offsetAndMetadata != null -> {
                consumer.seek(assignment, offsetAndMetadata.offset())
            }
            offsetStrategy == OffsetResetStrategy.LATEST -> {
                consumer.seekToEnd(setOf(assignment))
            }
            else -> {
                consumer.seekToBeginning(setOf(assignment))
            }
        }
    }
}

/**
 * Synchronously commit the the offset for the the given record back to its topic.
 * @param kafkaRecord record describes its topic, partition and offset.
 * @param metaData additional metdaData field to store about this record on the topic.
 */
fun <K, V> Consumer<K, V>.commitSyncOffsets(kafkaRecord: ConsumerRecord<K, V>, metaData: String? = null) {
    val offsets = mutableMapOf<TopicPartition, OffsetAndMetadata>()
    val topicPartition = TopicPartition(kafkaRecord.topic(), kafkaRecord.partition())
    offsets[topicPartition] = OffsetAndMetadata(kafkaRecord.offset() + 1, metaData)
    this.commitSync(offsets);
}