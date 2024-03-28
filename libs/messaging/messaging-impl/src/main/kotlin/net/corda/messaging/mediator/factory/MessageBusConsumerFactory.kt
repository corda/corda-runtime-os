package net.corda.messaging.mediator.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.constants.ConsumerRoles
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messaging.api.mediator.MediatorConsumer
import net.corda.messaging.api.mediator.config.MediatorConsumerConfig
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactory
import net.corda.messaging.mediator.MessageBusConsumer
import net.corda.messaging.subscription.consumer.listener.LoggingConsumerRebalanceListener

/**
 * Factory for creating multi-source event mediator message bus consumers.
 *
 * @param topicName Topic name.
 * @param groupName Consumer group name.
 * @param messageBusConfig Message bus related configuration.
 * @param cordaConsumerBuilder [CordaConsumer] builder.
 * @param clientId Consumer client Id
 * @param rebalanceListener Rebalance listener
 */
@Suppress("LongParameterList")
class MessageBusConsumerFactory(
    private val topicName: String,
    private val groupName: String,
    private val messageBusConfig: SmartConfig,
    private val cordaConsumerBuilder: CordaConsumerBuilder,
    private val clientId: String,
    private val rebalanceListener: CordaConsumerRebalanceListener?
): MediatorConsumerFactory {

    override fun <K : Any, V : Any> create(config: MediatorConsumerConfig<K, V>): MediatorConsumer<K, V> {
        val eventConsumerConfig = ConsumerConfig(
            groupName,
            "$clientId-eventConsumer",
            ConsumerRoles.SAE_EVENT
        )

        val eventConsumer = cordaConsumerBuilder.createConsumer(
            eventConsumerConfig,
            messageBusConfig,
            config.keyClass,
            config.valueClass,
            config.onSerializationError,
            rebalanceListener?: LoggingConsumerRebalanceListener(clientId)
        )

        return MessageBusConsumer(
            topicName,
            eventConsumer,
        )
    }
}