package net.corda.messagebus.api.consumer.builder

import com.typesafe.config.Config
import net.corda.messagebus.api.consumer.ConsumerRebalanceListener
import net.corda.messagebus.api.consumer.CordaConsumer

/**
 * Builder for creating Consumers.
 */
interface ConsumerBuilder<K : Any, V : Any> {

    /**
     * Generate a Corda PubSub Consumer based on the [consumerConfig].
     * This function will handle all retry logic and kafka error handling
     * @param onError a callback to receive messages that fail to deserialize.  In the consumer feed these will
     * show up as records with a null value, which means they should be removed from any maps.
     * @return CordaConsumer
     * @throws CordaMessageAPIFatalException if fatal error occurs during construction of the consumer
     */
    fun createPubSubConsumer(
        consumerConfig: Config,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onError: (String, ByteArray) -> Unit = {_, _ ->},
    ) : CordaConsumer<K, V>

    /**
     * Generate a Corda Consumer based on the [consumerConfig] for a [DurableSubscription].
     * This function will handle all retry logic and kafka error handling
     * @param consumerRebalanceListener when not null, an override for the default rebalance handling for
     * the subscription.
     * @return CordaConsumer
     * @throws CordaMessageAPIFatalException if fatal error occurs during construction of the consumer
     */
    fun createDurableConsumer(
        consumerConfig: Config,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onError: (String, ByteArray) -> Unit = {_, _ ->},
        consumerRebalanceListener: ConsumerRebalanceListener? = null
    ) : CordaConsumer<K, V>

    /**
     * Generate a Corda Compacted topic Consumer based on the [consumerConfig].
     * This function will handle all retry logic and kafka error handling
     * @return CordaConsumer
     * @throws CordaMessageAPIFatalException if fatal error occurs during construction of the consumer
     */
    fun createCompactedConsumer(
        consumerConfig: Config,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onError: (String, ByteArray) -> Unit = {_, _ ->},
    ) : CordaConsumer<K, V>

    /**
     * Generate a Corda RPC topic Consumer based on the [consumerConfig].
     * This function will handle all retry logic and kafka error handling
     * @return CordaConsumer
     * @throws CordaMessageAPIFatalException if fatal error occurs during construction of the consumer
     */
    fun createRPCConsumer(
        consumerConfig: Config,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onError: (String, ByteArray) -> Unit = {_, _ ->},
    ) : CordaConsumer<K, V>

}
