package net.corda.messaging.mediator.slim

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.constants.ConsumerRoles
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messaging.api.mediator.config.MediatorConsumerConfig
import java.util.UUID

class SlimMessageBusConsumerFacFactory(
    private val topicName: String,
    private val groupName: String,
    private val messageBusConfig: SmartConfig,
    private val cordaConsumerBuilder: CordaConsumerBuilder,
    private val topicOffsetStateService: SlimTopicOffsetStateService,
) {
    fun <K : Any, V : Any> create(config: MediatorConsumerConfig<K, V>): SlimMessageBusConsumer<K, V> {
        val subscriptionType = "MultiSourceSubscription"
        val uniqueId = UUID.randomUUID().toString()
        val clientId = "$subscriptionType--$groupName--$topicName--$uniqueId"

        val eventConsumerConfig = ConsumerConfig(
            groupName,
            "$clientId-eventConsumer",
            ConsumerRoles.SAE_EVENT,
        )

        val eventConsumer = cordaConsumerBuilder.createConsumer(
            eventConsumerConfig,
            messageBusConfig,
            config.keyClass,
            config.valueClass,
            config.onSerializationError,
        )

        return SlimMessageBusConsumer(
            topicName,
            eventConsumer,
            topicOffsetStateService::getOffsetsOrDefaultFor,
        )
    }
}
