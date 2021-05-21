package net.corda.messaging.kafka.subscription

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.TopicPartition
import java.nio.ByteBuffer

fun createMockConsumerAndAddRecords(topic: String,
                                    numberOfRecords: Long,
                                    offsetResetStrategy: OffsetResetStrategy):
        Pair<MockConsumer<String, ByteBuffer>, TopicPartition> {
    val topicPartition = TopicPartition(topic, 1)
    val partitions = mutableListOf(topicPartition)
    val partitionsBeginningMap = mutableMapOf<TopicPartition, Long>()
    val partitionsEndMap = mutableMapOf<TopicPartition, Long>()

    partitionsBeginningMap[topicPartition] = 0L
    partitionsEndMap[topicPartition] = numberOfRecords

    val consumer = MockConsumer<String, ByteBuffer>(offsetResetStrategy)
    consumer.subscribe(listOf(topic))
    consumer.rebalance(partitions)
    consumer.updateBeginningOffsets(partitionsBeginningMap)
    consumer.updateEndOffsets(partitionsEndMap)

    val records = generateMockConsumerRecordList(numberOfRecords, topic, 1)
    records.forEach{consumer.addRecord(it)}

    return Pair(consumer, topicPartition)
}

/**
 * Generate a list of size [numberOfRecords] ConsumerRecords.
 * Assigned to [partition] and [topic]
 * @return List of ConsumerRecord
 */
fun generateMockConsumerRecordList(numberOfRecords: Long, topic: String, partition: Int) : List<ConsumerRecord<String, ByteBuffer>> {
    val records = mutableListOf<ConsumerRecord<String, ByteBuffer>>()
    for (i in 0 until numberOfRecords) {
        val value = ByteBuffer.wrap("value$i".toByteArray())
        val record = ConsumerRecord(topic, partition, i, "key$i", value)
        records.add(record)
    }
    return records
}

/**
 * Generate a list of size [numberOfRecords] ConsumerRecords.
 * Assigned to [partition] and [topic]
 * @return ConsumerRecords
 */
fun generateMockConsumerRecordsList(numberOfRecords: Long, topic: String, partition: Int) : ConsumerRecords<String, ByteBuffer> {
    val recordList = generateMockConsumerRecordList(numberOfRecords, topic, partition)
    val topicPartition = TopicPartition(topic, partition)
    val records = ConsumerRecords(mutableMapOf(Pair(topicPartition, recordList)))
    return records
}
