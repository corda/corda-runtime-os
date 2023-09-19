package net.corda.messaging.mediator.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.mediator.factory.MediatorProducerFactoryFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Factory for creating multi-source event mediator producers.
 */
@Component(service = [MediatorProducerFactoryFactory::class])
class MediatorProducerFactoryFactoryImpl @Activate constructor(
    @Reference(service = CordaProducerBuilder::class)
    private val cordaProducerBuilder: CordaProducerBuilder,
): MediatorProducerFactoryFactory {
    override fun createMessageBusProducerFactory(
        id: String,
        messageBusConfig: SmartConfig,
    ) = MessageBusProducerFactory(
        id,
        messageBusConfig,
        cordaProducerBuilder,
    )
}