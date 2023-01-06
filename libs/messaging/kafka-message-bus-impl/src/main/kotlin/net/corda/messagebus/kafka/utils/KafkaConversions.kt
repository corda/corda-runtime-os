package net.corda.messagebus.kafka.utils

import net.corda.messagebus.api.CordaOffsetAndMetadata
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.TopicPartition

fun CordaOffsetResetStrategy.toKafka() = OffsetResetStrategy.valueOf(this.name)
fun CordaOffsetAndMetadata.toKafka() = OffsetAndMetadata(this.offset, this.metadata)

fun List<CordaConsumerRecord<*, *>>.toKafkaRecords():
        List<ConsumerRecord<*, *>> {
    return this.map { it.toKafkaRecord() }
}

fun CordaConsumerRecord<*, *>.toKafkaRecord():
        ConsumerRecord<*, *> {
    return ConsumerRecord(
        this.topic,
        this.partition,
        this.offset,
        this.key,
        this.value
    )
}

fun CordaTopicPartition.toTopicPartition(topicPrefix: String): TopicPartition {
    return if (topic.startsWith(topicPrefix)) {
        TopicPartition(topic, partition)
    } else {
        TopicPartition(topicPrefix + topic, partition)
    }
}

fun TopicPartition.toCordaTopicPartition(topicPrefix: String): CordaTopicPartition {
    return CordaTopicPartition(topic().removePrefix(topicPrefix), partition())
}

fun Collection<CordaTopicPartition>.toTopicPartitions(topicPrefix: String) = this.map { it.toTopicPartition(topicPrefix) }
fun Collection<TopicPartition>.toCordaTopicPartitions(topicPrefix: String ) = this.map { it.toCordaTopicPartition(topicPrefix) }
