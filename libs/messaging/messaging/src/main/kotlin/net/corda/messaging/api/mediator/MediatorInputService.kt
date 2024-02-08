package net.corda.messaging.api.mediator

import net.corda.messaging.api.records.Record

interface MediatorInputService {

    companion object {
        const val INPUT_HASH_HEADER = "InputHash"
        const val SYNC_RESPONSE_HEADER = "SyncResponse"
    }

    /**
     * Generate a unique SHA256 hash, converted to a UUID, for the consumer input record.
     * @param inputEvent The consumer input event polled from the bus
     * @return A hash of the input event as bytes, converted to UUID String
     */
    fun <K : Any, E : Any> getHash(inputEvent: Record<K, E>): String
}
