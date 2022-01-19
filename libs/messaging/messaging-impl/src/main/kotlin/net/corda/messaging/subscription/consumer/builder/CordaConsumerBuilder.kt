package net.corda.messaging.subscription.consumer.builder

import com.typesafe.config.Config
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener

/**
 * Builder for creating Consumers.
 */
interface CordaConsumerBuilder {

    /**
     * Generate a Corda PubSub Consumer based on the [consumerConfig].
     * This function will handle all retry logic and error handling
     * @param consumerConfig configuration parameters for the PubSub consumer
     * @param kClazz the class type of the key for this subscription
     * @param vClazz the class type of the value for this subscription
     * @param onError a callback to receive messages that fail to deserialize.  In the consumer feed these will
     * show up as records with a null value, which means they should be removed from any maps.
     * @return CordaConsumer
     * @throws CordaMessageAPIFatalException if fatal error occurs during construction of the consumer
     */
    fun <K : Any, V : Any> createPubSubConsumer(
        consumerConfig: Config,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onError: (ByteArray) -> Unit = {_ ->},
    ) : CordaConsumer<K, V>

    /**
     * Generate a Corda Consumer based on the [consumerConfig] for a [DurableSubscription].
     * This function will handle all retry logic and error handling
     * @param consumerConfig configuration parameters for the PubSub consumer
     * @param kClazz the class type of the key for this subscription
     * @param vClazz the class type of the value for this subscription
     * @param onError a callback to receive messages that fail to deserialize.  In the consumer feed these will
     * show up as records with a null value, which means they should be removed from any maps.
     * @param cordaConsumerRebalanceListener when not null, an override for the default rebalance handling for
     * the subscription.
     * @return CordaConsumer
     * @throws CordaMessageAPIFatalException if fatal error occurs during construction of the consumer
     */
    fun <K : Any, V : Any> createDurableConsumer(
        consumerConfig: Config,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onError: (ByteArray) -> Unit = {_ ->},
        cordaConsumerRebalanceListener: CordaConsumerRebalanceListener? = null
    ) : CordaConsumer<K, V>

    /**
     * Generate a Corda Compacted topic Consumer based on the [consumerConfig].
     * This function will handle all retry logic and error handling
     * @param consumerConfig configuration parameters for the PubSub consumer
     * @param kClazz the class type of the key for this subscription
     * @param vClazz the class type of the value for this subscription
     * @param onError a callback to receive messages that fail to deserialize.  In the consumer feed these will
     * show up as records with a null value, which means they should be removed from any maps.
     * @return CordaConsumer
     * @throws CordaMessageAPIFatalException if fatal error occurs during construction of the consumer
     */
    fun <K : Any, V : Any> createCompactedConsumer(
        consumerConfig: Config,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onError: (ByteArray) -> Unit = {_ ->},
    ) : CordaConsumer<K, V>

    /**
     * Generate a Corda RPC topic Consumer based on the [consumerConfig].
     * This function will handle all retry logic and error handling
     * @param consumerConfig configuration parameters for the PubSub consumer
     * @param kClazz the class type of the key for this subscription
     * @param vClazz the class type of the value for this subscription
     * @param onError a callback to receive messages that fail to deserialize.  In the consumer feed these will
     * show up as records with a null value, which means they should be removed from any maps.
     * @return CordaConsumer
     * @throws CordaMessageAPIFatalException if fatal error occurs during construction of the consumer
     */
    fun <K : Any, V : Any> createRPCConsumer(
        consumerConfig: Config,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onError: (ByteArray) -> Unit = {_ ->},
    ) : CordaConsumer<K, V>
}
