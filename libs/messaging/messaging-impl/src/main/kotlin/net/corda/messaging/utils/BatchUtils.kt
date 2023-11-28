package net.corda.messaging.utils

import net.corda.messagebus.api.consumer.CordaConsumerRecord

/**
 * Subdivide a batch of events for more efficient processing.
 *
 * In the current implementation, this does nothing. In the future, the subbatching logic may ensure that all events on
 * the same key are processed in order as part of the same sub-batch, to allow sub-batch processing to be parallelized.
 */
fun<K : Any, E : Any> getEventsByBatch(events: List<CordaConsumerRecord<K, E>>): List<List<CordaConsumerRecord<K, E>>> {
    if (events.isEmpty()) {
        return emptyList()
    }

    return listOf(events)
}
