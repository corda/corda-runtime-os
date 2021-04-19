package net.corda.messaging.kafka.subscription.consumer

import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import org.apache.kafka.clients.consumer.Consumer

/**
 * Builder for creating Consumers.
 */
interface ConsumerBuilder<K, V> {

    /**
     * Generate a Kafka Consumer using the given [config] and applying any given [properties].
     */
    fun createConsumer(config: SubscriptionConfig, properties: Map<String, String>): Consumer<K, V>
}
