package net.corda.messaging.api.publisher.factory

import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig

/**
 * Interface for creating publishers
 */
interface PublisherFactory {

    /**
     * Create a publisher which publishes to a topic with a given [config] and map of [properties].
     * @return a publisher
     */
    fun <K, V> createPublisher(
        config: PublisherConfig,
        properties: Map<String, String>
    ): Publisher<K, V>
}