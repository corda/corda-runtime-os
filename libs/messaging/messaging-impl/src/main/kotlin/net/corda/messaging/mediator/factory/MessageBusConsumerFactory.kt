package net.corda.messaging.mediator.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.constants.ConsumerRoles
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messaging.api.mediator.MediatorConsumer
import net.corda.messaging.api.mediator.config.MediatorConsumerConfig
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactory
import net.corda.messaging.mediator.MessageBusConsumer
import java.util.UUID

/**
 * Factory for creating multi-source event mediator message bus consumers.
 *
 * @param topicName Topic name.
 * @param groupName Consumer group name.
 * @param messageBusConfig Message bus related configuration.
 * @param cordaConsumerBuilder [CordaConsumer] builder.
 */
class MessageBusConsumerFactory(
    private val topicName: String,
    private val groupName: String,
    private val messageBusConfig: SmartConfig,
    private val cordaConsumerBuilder: CordaConsumerBuilder,
): MediatorConsumerFactory {

    override fun <K : Any, V : Any> create(config: MediatorConsumerConfig<K, V>): MediatorConsumer<K, V> {
        val subscriptionType = "MultiSourceSubscription"
        val uniqueId = UUID.randomUUID().toString()
        val clientId = "$subscriptionType--$groupName--$topicName--$uniqueId"

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
            config.onSerializationError
        )

        return MessageBusConsumer(
            topicName,
            eventConsumer,
        )
    }
}