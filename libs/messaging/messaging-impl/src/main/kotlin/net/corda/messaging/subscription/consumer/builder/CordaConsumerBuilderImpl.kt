package net.corda.messaging.subscription.consumer.builder

import com.typesafe.config.Config
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.CLIENT_ID
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.GROUP_ID
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.TOPIC_NAME
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.builder.MessageBusConsumerBuilder
import net.corda.messaging.subscription.consumer.listener.LoggingConsumerRebalanceListener
import net.corda.messaging.subscription.consumer.listener.PubSubConsumerRebalanceListener
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Generate a Corda Consumer.
 */
@Component(service = [CordaConsumerBuilder::class])
class CordaConsumerBuilderImpl @Activate constructor(
    @Reference(service = MessageBusConsumerBuilder::class)
    private val messageBusConsumerBuilder: MessageBusConsumerBuilder
) : CordaConsumerBuilder {

    override fun <K : Any, V : Any> createPubSubConsumer(
        consumerConfig: Config,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onError: (ByteArray) -> Unit
    ): CordaConsumer<K, V> {
        val cordaConsumer = messageBusConsumerBuilder.createConsumer(
            consumerConfig,
            kClazz,
            vClazz,
            onError
        )
        val listener = PubSubConsumerRebalanceListener(
            consumerConfig.getString(TOPIC_NAME),
            consumerConfig.getString(GROUP_ID),
            cordaConsumer,
        )
        return cordaConsumer.also { it.setDefaultRebalanceListener(listener) }
    }

    override fun <K : Any, V : Any> createDurableConsumer(
        consumerConfig: Config,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onError: (ByteArray) -> Unit,
        cordaConsumerRebalanceListener: CordaConsumerRebalanceListener?
    ): CordaConsumer<K, V> {
        val listener = cordaConsumerRebalanceListener ?: LoggingConsumerRebalanceListener(
            consumerConfig.getString(TOPIC_NAME),
            consumerConfig.getString(GROUP_ID),
            consumerConfig.getString(CLIENT_ID)
        )
        return messageBusConsumerBuilder.createConsumer(consumerConfig, kClazz, vClazz, onError, listener)
    }

    override fun <K : Any, V : Any> createCompactedConsumer(
        consumerConfig: Config,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onError: (ByteArray) -> Unit
    ): CordaConsumer<K, V> {
        return messageBusConsumerBuilder.createConsumer(consumerConfig, kClazz, vClazz, onError)
    }

    override fun <K : Any, V : Any> createRPCConsumer(
        consumerConfig: Config,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onError: (ByteArray) -> Unit
    ): CordaConsumer<K, V> {
        return messageBusConsumerBuilder.createConsumer(consumerConfig, kClazz, vClazz, onError)
    }

}
