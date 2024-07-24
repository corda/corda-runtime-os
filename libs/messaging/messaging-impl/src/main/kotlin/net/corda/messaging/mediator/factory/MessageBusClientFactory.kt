package net.corda.messaging.mediator.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.constants.ProducerRoles
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.config.MessagingClientConfig
import net.corda.messaging.api.mediator.factory.MessagingClientFactory
import net.corda.messaging.mediator.MessageBusClient
import net.corda.schema.configuration.BootConfig
import java.util.UUID

/**
 * Factory for creating multi-source event mediator message bus messaging clients.
 *
 * @param id Messaging client's unique ID.
 * @param messageBusConfig Message bus related configuration.
 * @param cordaProducerBuilder [CordaProducer] builder.
 */
class MessageBusClientFactory(
    private val id: String,
    private val messageBusConfig: SmartConfig,
    private val cordaProducerBuilder: CordaProducerBuilder,
): MessagingClientFactory {

    override fun create(config: MessagingClientConfig): MessagingClient {
        val uniqueId = UUID.randomUUID().toString()
        val clientId = "$id--$uniqueId"

        val eventProducerConfig = ProducerConfig(
            clientId,
            messageBusConfig.getInt(BootConfig.INSTANCE_ID),
            transactional = false,
            ProducerRoles.SAE_PRODUCER,
            throwOnSerializationError = false
        )

        return MessageBusClient(id) {
            cordaProducerBuilder.createProducer(
                eventProducerConfig,
                messageBusConfig,
                config.onSerializationError
            )
        }
    }
}