package net.corda.messaging.mediator.factory

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.mediator.factory.MessagingClientFactoryFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Factory for creating multi-source event mediator messaging clients.
 */
@Component(service = [MessagingClientFactoryFactory::class])
class MessagingClientFactoryFactoryImpl @Activate constructor(
    @Reference(service = CordaProducerBuilder::class)
    private val cordaProducerBuilder: CordaProducerBuilder,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaSerializationFactory: CordaAvroSerializationFactory
): MessagingClientFactoryFactory {
    override fun createMessageBusClientFactory(
        id: String,
        messageBusConfig: SmartConfig,
    ) = MessageBusClientFactory(
        id,
        messageBusConfig,
        cordaProducerBuilder,
    )

    override fun createRPCClientFactory(
        id: String
    ) = RPCClientFactory(
        id,
        cordaSerializationFactory
    )
}
