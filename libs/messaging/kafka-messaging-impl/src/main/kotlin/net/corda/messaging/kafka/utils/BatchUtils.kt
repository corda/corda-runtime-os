package net.corda.messaging.kafka.utils

import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition

/**
 * Divide a list of [events] into batches such that 1 key does not have more then one entry per batch
 */
fun<K: Any, E : Any> getEventsByBatch(events: List<ConsumerRecordAndMeta<K, E>>): List<List<ConsumerRecordAndMeta<K, E>>> {
    if (events.isEmpty()) {
        return emptyList()
    }

    val keysInBatch = mutableSetOf<K>()
    val eventBatches = mutableListOf<MutableList<ConsumerRecordAndMeta<K, E>>>(mutableListOf())
    events.forEach { event ->
        val eventKey = event.record.key()

        if (eventKey in keysInBatch) {
            keysInBatch.clear()
            eventBatches.add(mutableListOf())
        }

        keysInBatch.add(eventKey)
        eventBatches.last().add(event)
    }

    return eventBatches
}

/**
 * Generate the consumer offsets for a given list of [records]
 */
fun getRecordListOffsets(records: List<ConsumerRecord<*, *>>): Map<TopicPartition, OffsetAndMetadata> {
    if (records.isEmpty()) {
        return mutableMapOf()
    }

    val map = mutableMapOf<Pair<String, Int>, Long>()
    for (record in records) {
        val currentMapVal = map[Pair(record.topic(), record.partition())]
        if (currentMapVal == null || currentMapVal < record.offset()) {
            map[Pair(record.topic(), record.partition())] = record.offset()
        }
    }

    val offsets = mutableMapOf<TopicPartition, OffsetAndMetadata>()
    for (entry in map.entries) {
        val currentKey = entry.key
        val topicPartition = TopicPartition(currentKey.first, currentKey.second)
        offsets[topicPartition] = OffsetAndMetadata(entry.value + 1)
    }

    return offsets
}
