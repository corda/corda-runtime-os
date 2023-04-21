package net.corda.messaging.utils

import net.corda.messagebus.api.consumer.CordaConsumerRecord

/**
 * Divide a list of [events] into batches such that 1 key does not have more than one entry per batch
 */
fun<K: Any, E : Any> getEventsByBatch(events: List<CordaConsumerRecord<K, E>>): List<List<CordaConsumerRecord<K, E>>> {
    if (events.isEmpty()) {
        return emptyList()
    }

    val keysInBatch = mutableSetOf<K>()
    val eventBatches = mutableListOf<MutableList<CordaConsumerRecord<K, E>>>(mutableListOf())
    events.forEach { event ->
        val eventKey = event.key

        if (eventKey in keysInBatch) {
            keysInBatch.clear()
            eventBatches.add(mutableListOf())
        }

        keysInBatch.add(eventKey)
        eventBatches.last().add(event)
    }

    return eventBatches
}
