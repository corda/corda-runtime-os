package net.corda.messaging.kafka.subscription

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.TopicPartition

fun createMockConsumerAndAddRecords(topic: String,
                                    numberOfRecords: Long,
                                    offsetResetStrategy: OffsetResetStrategy):
        Pair<MockConsumer<String, String>, TopicPartition> {
    val topicPartition = TopicPartition(topic, 1)
    val partitions = mutableListOf(topicPartition)
    val partitionsBeginningMap = mutableMapOf<TopicPartition, Long>()
    val partitionsEndMap = mutableMapOf<TopicPartition, Long>()

    partitionsBeginningMap[topicPartition] = 0L
    partitionsEndMap[topicPartition] = numberOfRecords

    val consumer = MockConsumer<String, String>(offsetResetStrategy)
    consumer.subscribe(listOf(topic))
    consumer.rebalance(partitions)
    consumer.updateBeginningOffsets(partitionsBeginningMap)
    consumer.updateEndOffsets(partitionsEndMap)

    val records = generateMockConsumerRecordList(numberOfRecords, topic, 1)
    records.forEach{ consumer.addRecord(it) }

    return Pair(consumer, topicPartition)
}

/**
 * Generate a list of size [numberOfRecords] ConsumerRecords.
 * Assigned to [partition] and [topic]
 * @return List of ConsumerRecord
 */
fun generateMockConsumerRecordList(numberOfRecords: Long, topic: String, partition: Int) : List<ConsumerRecord<String, String>> {
    val records = mutableListOf<ConsumerRecord<String, String>>()
    for (i in 0 until numberOfRecords) {
        val record = ConsumerRecord(topic, partition, i, "key$i", "value$i")
        records.add(record)
    }
    return records
}

/**
 * Generate a list of size [numberOfRecords] ConsumerRecords.
 * Assigned to [partition] and [topic]
 * @return ConsumerRecords
 */
fun generateMockConsumerRecords(numberOfRecords: Long, topic: String, partition: Int): ConsumerRecords<String, String> {
    val recordList = generateMockConsumerRecordList(numberOfRecords, topic, partition)
    val topicPartition = TopicPartition(topic, partition)
    return ConsumerRecords(mutableMapOf(Pair(topicPartition, recordList)))
}

