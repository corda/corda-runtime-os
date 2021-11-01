package net.corda.messaging.kafka.utils

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition

/**
 * Divide a list of [events] into batches such that 1 key does not have more then one entry per batch
 */
fun<K: Any, E : Any> getEventsByBatch(events: List<ConsumerRecord<K, E>>): List<List<ConsumerRecord<K, E>>> {
    if (events.isEmpty()) {
        return emptyList()
    }

    val keysInBatch = mutableSetOf<K>()
    val eventBatches = mutableListOf<MutableList<ConsumerRecord<K, E>>>(mutableListOf())
    events.forEach { event ->
        val eventKey = event.key()

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

    return records.fold(mutableMapOf()) { offsets, record ->
        val key = TopicPartition(record.topic(), record.partition())
        val currentOffset = offsets[key]?.offset() ?: 0L
        if (currentOffset <= record.offset()) {
            offsets[key] = OffsetAndMetadata(record.offset() + 1)
        }
        offsets
    }
}
