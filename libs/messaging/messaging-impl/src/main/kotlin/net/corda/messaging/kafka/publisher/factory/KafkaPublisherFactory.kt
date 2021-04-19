package net.corda.messaging.kafka.publisher.factory

import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.kafka.publisher.KafkaPublisher
import net.corda.messaging.kafka.publisher.builder.impl.KafkaPublisherBuilder

/**
 * Kafka implementation for Publisher Factory.
 */
class KafkaPublisherFactory : PublisherFactory {
    override fun <K, V> createPublisher(
        config: PublisherConfig,
        properties: Map<String, String>
    ): Publisher<K, V> {
        return KafkaPublisher(config.clientId, config.topic, config.instanceId, KafkaPublisherBuilder(), properties)
    }
}