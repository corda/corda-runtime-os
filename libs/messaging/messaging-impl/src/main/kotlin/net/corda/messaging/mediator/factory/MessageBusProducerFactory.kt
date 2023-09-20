package net.corda.messaging.mediator.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.constants.ProducerRoles
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.mediator.MediatorProducer
import net.corda.messaging.api.mediator.config.MediatorProducerConfig
import net.corda.messaging.api.mediator.factory.MediatorProducerFactory
import net.corda.messaging.mediator.MessageBusProducer
import net.corda.schema.configuration.BootConfig
import java.util.UUID

/**
 * Factory for creating multi-source event mediator message bus producers.
 *
 * @param id Producer's unique ID.
 * @param messageBusConfig Message bus related configuration.
 * @param cordaProducerBuilder [CordaProducer] builder.
 */
class MessageBusProducerFactory(
    private val id: String,
    private val messageBusConfig: SmartConfig,
    private val cordaProducerBuilder: CordaProducerBuilder,
): MediatorProducerFactory {

    override fun create(config: MediatorProducerConfig): MediatorProducer {
        val uniqueId = UUID.randomUUID().toString()
        val clientId = "$id--$uniqueId"

        val eventProducerConfig = ProducerConfig(
            clientId,
            messageBusConfig.getInt(BootConfig.INSTANCE_ID),
            transactional = false,
            ProducerRoles.SAE_PRODUCER,
            throwOnSerializationError = false
        )

        val eventProducer = cordaProducerBuilder.createProducer(
            eventProducerConfig,
            messageBusConfig,
            config.onSerializationError
        )

        return MessageBusProducer(
            id,
            eventProducer,
        )
    }
}