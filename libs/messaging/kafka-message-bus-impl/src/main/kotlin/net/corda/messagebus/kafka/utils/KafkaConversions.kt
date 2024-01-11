package net.corda.messagebus.kafka.utils

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messagebus.api.producer.CordaProducerRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer

private val stringDeserializer = StringDeserializer()
private val stringSerializer = StringSerializer()

fun CordaOffsetResetStrategy.toKafka() = OffsetResetStrategy.valueOf(this.name)

fun <K : Any, V : Any> CordaProducerRecord<K, V>.toKafkaRecord(
    topicPrefix: String,
    partition: Int? = null
): ProducerRecord<Any, Any> {
    return ProducerRecord(
        topicPrefix + this.topic,
        partition,
        this.key,
        this.value,
        this.headers.map {
            RecordHeader(it.first, stringSerializer.serialize(null, it.second))
        })
}

@Suppress("UNCHECKED_CAST")
fun <K : Any, V : Any> ConsumerRecord<Any, Any>.toCordaConsumerRecord(
    topicPrefix: String
): CordaConsumerRecord<K, V> {
    return this.toCordaConsumerRecord(topicPrefix, this.key() as K,this.value() as V?)
}

fun <K : Any, V : Any> ConsumerRecord<Any, Any>.toCordaConsumerRecord(
    topicPrefix: String,
    key:K,
    value:V?
): CordaConsumerRecord<K, V> {
    val polledTime = System.currentTimeMillis()
    val headers = this.headers().map { it.key() to stringDeserializer.deserialize(null, it.value()) }
    return CordaConsumerRecord(
        this.topic().removePrefix(topicPrefix),
        this.partition(),
        this.offset(),
        key,
        value,
        this.timestamp(),
        headers + Pair("polledTime", polledTime.toString())
    )
}

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

fun Collection<CordaTopicPartition>.toTopicPartitions(topicPrefix: String) =
    this.map { it.toTopicPartition(topicPrefix) }

fun Collection<TopicPartition>.toCordaTopicPartitions(topicPrefix: String) =
    this.map { it.toCordaTopicPartition(topicPrefix) }
