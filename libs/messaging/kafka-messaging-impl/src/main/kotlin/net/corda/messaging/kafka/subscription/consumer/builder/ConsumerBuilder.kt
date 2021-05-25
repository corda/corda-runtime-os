package net.corda.messaging.kafka.subscription.consumer.builder

import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer

/**
 * Builder for creating Consumers.
 */
interface ConsumerBuilder<K : Any, V : Any> {

    /**
     * Generate a Corda Kafka Consumer based on the [subscriptionConfig] for a [PubsubSubscription].
     * This function will handle all retry logic and kafka error handling
     * @return CordaKafkaConsumer
     * @throws CordaMessageAPIFatalException if fatal error occurs during construction of the consumer
     */
    fun createPubSubConsumer(
        subscriptionConfig: SubscriptionConfig
    ) : CordaKafkaConsumer<K, V>

    /**
     * Generate a Corda Kafka Consumer based on the [subscriptionConfig] for a [DurableSubscription].
     * This function will handle all retry logic and kafka error handling
     * @return CordaKafkaConsumer
     * @throws CordaMessageAPIFatalException if fatal error occurs during construction of the consumer
     */
    fun createDurableConsumer(
        subscriptionConfig: SubscriptionConfig
    ) : CordaKafkaConsumer<K, V>
}
