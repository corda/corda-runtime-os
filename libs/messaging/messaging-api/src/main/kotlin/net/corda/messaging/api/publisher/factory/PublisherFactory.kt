package net.corda.messaging.api.publisher.factory

import net.corda.messaging.api.publisher.Publisher

/**
 * Interface for creating publishers
 */
interface PublisherFactory {

    /**
     * Create a publisher which publishes to a [topic] with a given set of connection [properties].
     * Records published to the [topic] will contain the [clientId] as meta-data to identify the source.
     * @return a publisher
     */
    fun <K, V> createPublisher(
        clientId: String,
        topic: String,
        properties: Map<String, String>
    ): Publisher<K, V>
}