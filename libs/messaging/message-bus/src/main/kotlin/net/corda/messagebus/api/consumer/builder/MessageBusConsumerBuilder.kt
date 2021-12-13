package net.corda.messagebus.api.consumer.builder

import com.typesafe.config.Config
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener

interface MessageBusConsumerBuilder {
    /**
     * Generate a Corda Consumer based on the [consumerConfig].
     * This function will handle all retry logic and error handling
     * @param consumerConfig configuration parameters for the consumer
     * @param kClazz the class type of the key for this subscription
     * @param vClazz the class type of the value for this subscription
     * @param onSerializationError a callback to receive messages that fail to deserialize.  In the consumer feed
     * these will show up as records with a null value, which means they should be removed from any maps.
     * @return CordaConsumer
     * @throws CordaMessageAPIFatalException if fatal error occurs during construction of the consumer
     */
    fun <K : Any, V : Any> createConsumer(
        consumerConfig: Config,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onSerializationError: (ByteArray) -> Unit = {_ ->},
        listener: CordaConsumerRebalanceListener? = null
    ) : CordaConsumer<K, V>
}
