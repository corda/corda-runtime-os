package net.corda.messaging.api.publisher.factory

import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig

/**
 * Interface for creating publishers of events. Only used for producers of events. Not used by consumers.
 * This can be injected as an OSGi Service
 */
interface PublisherFactory {

    /**
     * Create a publisher which publishes to a topic with a given [publisherConfig] and map of [properties].
     * @return A publisher of events.
     * @throws CordaMessageAPIException Exception in generating a Publisher.
     */
    fun <K : Any, V : Any> createPublisher(
        publisherConfig: PublisherConfig,
        properties: Map<String, String>,
        keyClass: Class<K>,
        valueClass: Class<V>,
    ): Publisher<K, V>
}

/**
 * Helper function to get key and value classes of the publisher.
 */
inline fun <reified K : Any, reified V : Any> PublisherFactory.createPublisher(
    publisherConfig: PublisherConfig,
    properties: Map<String, String>
): Publisher<K, V> =
    createPublisher(publisherConfig, properties, K::class.java, V::class.java)
