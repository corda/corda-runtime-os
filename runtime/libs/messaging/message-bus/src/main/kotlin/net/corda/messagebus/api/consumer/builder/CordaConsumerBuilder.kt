package net.corda.messagebus.api.consumer.builder

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener

interface CordaConsumerBuilder {
    /**
     * Generate a Corda Consumer based on the [consumerConfig].
     * This function will handle all retry logic and error handling
     * @param consumerConfig Required configuration parameters for the consumer.
     * @param messageBusConfig Bus-specific configuration to connect correctly to the underlying message bus and control its
     *                  behaviour.
     * @param kClazz the class type of the key for this subscription
     * @param vClazz the class type of the value for this subscription
     * @param onSerializationError a callback to receive messages that fail to deserialize.  In the consumer feed
     * these will show up as records with a null value, which means they should be removed from any maps.
     * @return CordaConsumer
     * @throws CordaMessageAPIFatalException if fatal error occurs during construction of the consumer
     */
    @Suppress("LongParameterList")
    fun <K : Any, V : Any> createConsumer(
        consumerConfig: ConsumerConfig,
        messageBusConfig: SmartConfig,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onSerializationError: (ByteArray) -> Unit = {_ ->},
        listener: CordaConsumerRebalanceListener? = null
    ) : CordaConsumer<K, V>
}
