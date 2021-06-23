package net.corda.messaging.kafka.subscription.consumer.builder

import com.typesafe.config.Config
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener

/**
 * Builder for creating Consumers.
 */
interface ConsumerBuilder<K : Any, V : Any> {

    /**
     * Generate a Corda Kafka PubSub Consumer based on the [consumerConfig].
     * This function will handle all retry logic and kafka error handling
     * @param onError a callback to receive messages that fail to deserialize.  In the consumer feed these will
     * show up as records with a null value, which means they should be removed from any maps.
     * @return CordaKafkaConsumer
     * @throws CordaMessageAPIFatalException if fatal error occurs during construction of the consumer
     */
    fun createPubSubConsumer(
        consumerConfig: Config,
        onError: (String, ByteArray) -> Unit = {_, _ ->}
    ) : CordaKafkaConsumer<K, V>

    /**
     * Generate a Corda Kafka Consumer based on the [consumerConfig] for a [DurableSubscription].
     * This function will handle all retry logic and kafka error handling
     * @param consumerRebalanceListener when not null, an override for the default rebalance handling for
     * the subscription.
     * @return CordaKafkaConsumer
     * @throws CordaMessageAPIFatalException if fatal error occurs during construction of the consumer
     */
    fun createDurableConsumer(
        consumerConfig: Config,
        onError: (String, ByteArray) -> Unit = {_, _ ->},
        consumerRebalanceListener: ConsumerRebalanceListener? = null,
    ) : CordaKafkaConsumer<K, V>

    /**
     * Generate a Corda Kafka Compacted topic Consumer based on the [consumerConfig].
     * This function will handle all retry logic and kafka error handling
     * @return CordaKafkaConsumer
     * @throws CordaMessageAPIFatalException if fatal error occurs during construction of the consumer
     */
    fun createCompactedConsumer(
        consumerConfig: Config,
        onError: (String, ByteArray) -> Unit = {_, _ ->},
    ) : CordaKafkaConsumer<K, V>

}
