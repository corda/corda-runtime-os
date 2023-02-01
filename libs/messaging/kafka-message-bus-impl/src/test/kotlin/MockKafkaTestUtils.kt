package net.corda.messaging.kafka.subscription

import java.nio.ByteBuffer
import java.util.UUID
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkKey
import net.corda.data.crypto.SecureHash
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.TopicPartition

fun createMockConsumerAndAddRecords(
    topic: String,
    numberOfRecords: Long,
    offsetResetStrategy: OffsetResetStrategy,
):
        Pair<MockConsumer<Any, Any>, TopicPartition> {
    val topicPartition = TopicPartition(topic, 1)
    val partitions = mutableListOf(topicPartition)
    val partitionsBeginningMap = mutableMapOf<TopicPartition, Long>()
    val partitionsEndMap = mutableMapOf<TopicPartition, Long>()

    partitionsBeginningMap[topicPartition] = 0L
    partitionsEndMap[topicPartition] = numberOfRecords

    val consumer = MockConsumer<Any, Any>(offsetResetStrategy)
    consumer.subscribe(listOf(topic))
    consumer.rebalance(partitions)
    consumer.updateBeginningOffsets(partitionsBeginningMap)
    consumer.updateEndOffsets(partitionsEndMap)

    val records = generateMockConsumerRecordList(numberOfRecords, topic, 1)
    records.forEach { consumer.addRecord(it) }

    return Pair(consumer, topicPartition)
}

/**
 * Generate a list of size [numberOfRecords] ConsumerRecords.
 * Assigned to [partition] and [topic]
 * @return List of ConsumerRecord
 */
@Suppress("UNCHECKED_CAST")
fun generateMockConsumerRecordList(
    numberOfRecords: Long,
    topic: String,
    partition: Int,
    startOffset: Long = 0,
): List<ConsumerRecord<Any, Any>> {
    val records = mutableListOf<ConsumerRecord<Any, Any>>()
    for (i in 0 until numberOfRecords) {
        val record = ConsumerRecord(topic, partition, i + startOffset, "key$i", "value$i")
        records.add(record as ConsumerRecord<Any, Any>)
    }
    return records
}

/**
 * Generate a list of size [numberOfRecords] ConsumerRecords.
 * Assigned to [partition] and [topic]
 * @return ConsumerRecords
 */
fun generateMockConsumerRecords(numberOfRecords: Long, topic: String, partition: Int, startOffset: Long = 0): ConsumerRecords<Any, Any> {
    val recordList = generateMockConsumerRecordList(numberOfRecords, topic, partition, startOffset)
    val topicPartition = TopicPartition(topic, partition)
    return ConsumerRecords(mutableMapOf(Pair(topicPartition, recordList)))
}

/**
 * Generate ConsumerRecords for a list of records for a given [topic] and [partition]
 * @return ConsumerRecords
 */
fun generateConsumerRecords(records: List<ConsumerRecord<Any, Any>>, topic: String, partition: Int): ConsumerRecords<Any,
        Any> {
    val topicPartition = TopicPartition(topic, partition)
    return ConsumerRecords(mutableMapOf(Pair(topicPartition, records)))
}

/**
 * Generate a list of chunked records. Total chunk count including final chunk will be equal to [numberOfRecords].
 * Chunked Records are assigned to a specific [topic] and [partition].
 * Record offset begins at [startOffset].
 * Exclude the final chunk via [buildFinalChunk]
 * @return list of chunked records
 */
@Suppress("UNCHECKED_CAST")
fun generateMockChunkedConsumerRecordsList(
    numberOfRecords: Long, topic: String, partition: Int, startOffset: Long = 0,
    buildFinalChunk:
    Boolean = true,
):
        List<ConsumerRecord<Any, Any>> {
    val records = mutableListOf<ConsumerRecord<Any, Any>>()
    val id = UUID.randomUUID().toString()
    for (i in 1 until numberOfRecords - 1) {
        val record = ConsumerRecord(topic, partition, startOffset + i, buildChunkKey(id, i), buildChunk(id, "value$i", i))
        records.add(record as ConsumerRecord<Any, Any>)
    }
    if (buildFinalChunk) {
        records.add(ConsumerRecord(topic, partition, numberOfRecords, buildChunkKey(id, numberOfRecords), buildFinalChunk(id,
            numberOfRecords)) as ConsumerRecord<Any, Any>)
    }
    return records
}

fun buildChunk(id: String, data: String, partNumber: Long): Chunk {
    return Chunk.newBuilder()
        .setProperties(null)
        .setFileName(null)
        .setChecksum(null)
        .setRequestId(id)
        .setPartNumber(partNumber.toInt())
        .setOffset(partNumber)
        .setData(ByteBuffer.wrap(data.toByteArray()))
        .build()
}

fun buildFinalChunk(id: String, partNumber: Long): Chunk {
    return Chunk.newBuilder()
        .setProperties(null)
        .setFileName(null)
        .setChecksum(SecureHash())
        .setRequestId(id)
        .setPartNumber(partNumber.toInt())
        .setOffset(partNumber)
        .setData(ByteBuffer.wrap(ByteArray(0)))
        .build()
}

fun buildChunkKey(id: String, partNumber: Long): ChunkKey {
    return ChunkKey.newBuilder()
        .setRequestId(id)
        .setRealKey(ByteBuffer.wrap(id.toByteArray()))
        .setPartNumber(partNumber.toInt())
        .build()
}

