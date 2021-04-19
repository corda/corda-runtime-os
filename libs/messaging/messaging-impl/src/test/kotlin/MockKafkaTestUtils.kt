package net.corda.messaging.kafka.subscription

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.TopicPartition


fun createMockConsumerAndAddRecords(topic: String, numberOfRecords: Long, offsetResetStrategy: OffsetResetStrategy): Pair<MockConsumer<String, ByteArray>, TopicPartition> {
    val topicPartition = TopicPartition(topic, 1)
    val partitions = mutableListOf(topicPartition)
    val partitionsBeginningMap = mutableMapOf<TopicPartition, Long>()
    val partitionsEndMap = mutableMapOf<TopicPartition, Long>()

    partitionsBeginningMap[topicPartition] = 0L
    partitionsEndMap[topicPartition] = numberOfRecords

    val consumer = MockConsumer<String, ByteArray>(offsetResetStrategy)
    consumer.subscribe(listOf(topic))
    consumer.rebalance(partitions)
    consumer.updateBeginningOffsets(partitionsBeginningMap)
    consumer.updateEndOffsets(partitionsEndMap)

    for (i in 0 until numberOfRecords) {
        val value = "value$i".toByteArray()
        val record = ConsumerRecord<String, ByteArray>(topic, 1, i, "key$i", value)
        consumer.addRecord(record)
    }

    return Pair(consumer, topicPartition)
}
