package net.corda.messaging.kafka.subscription.consumer.builder

import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer

/**
 * Builder for creating Consumers.
 */
interface ConsumerBuilder<K : Any, V : Any> {

    /**
     * Generate a Corda Kafka PubSub Consumer based on the [subscriptionConfig].
     * This function will handle all retry logic and kafka error handling
     * @param onError a callback to receive messages that fail to deserialize.  In the consumer feed these will
     * show up as records with a null value, which means they should be removed from any maps.
     * @return CordaKafkaConsumer
     * @throws CordaMessageAPIFatalException if fatal error occurs during construction of the consumer
     */
    fun createPubSubConsumer(
        subscriptionConfig: SubscriptionConfig,
        onError: (String, ByteArray) -> Unit = {_, _ ->}
    ) : CordaKafkaConsumer<K, V>

    /**
     * Generate a Corda Kafka Consumer based on the [subscriptionConfig] for a [DurableSubscription].
     * This function will handle all retry logic and kafka error handling
     * @return CordaKafkaConsumer
     * @throws CordaMessageAPIFatalException if fatal error occurs during construction of the consumer
     */
    fun createDurableConsumer(
        subscriptionConfig: SubscriptionConfig,
        onError: (String, ByteArray) -> Unit = {_, _ ->}
    ) : CordaKafkaConsumer<K, V>


    /**
     * Generate a Corda Kafka Compacted topic Consumer based on the [subscriptionConfig].
     * This function will handle all retry logic and kafka error handling
     * @return CordaKafkaConsumer
     * @throws CordaMessageAPIFatalException if fatal error occurs during construction of the consumer
     */
    fun createCompactedConsumer(
        subscriptionConfig : SubscriptionConfig,
        onError: (String, ByteArray) -> Unit = {_, _ ->},
    ) : CordaKafkaConsumer<K, V>
}
