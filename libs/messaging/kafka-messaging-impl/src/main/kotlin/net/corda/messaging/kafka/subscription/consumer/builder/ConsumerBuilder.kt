package net.corda.messaging.kafka.subscription.consumer.builder

import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer

/**
 * Builder for creating Consumers.
 */
interface ConsumerBuilder<K, V> {

    /**
     * Generate a Corda Kafka Consumer based on the [subscriptionConfig].
     * This function will handle all retry logic and kafka error handling
     * @return CordaKafkaConsumer
     * @throws CordaMessageAPIFatalException if fatal error occurs during construction of the consumer
     */
    fun createConsumer(subscriptionConfig : SubscriptionConfig) : CordaKafkaConsumer<K, V>
}
