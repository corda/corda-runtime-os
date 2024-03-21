package net.corda.messaging.mediator.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactoryFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Factory for creating multi-source event mediator consumers.
 */
@Component(service = [MediatorConsumerFactoryFactory::class])
class MediatorConsumerFactoryFactoryImpl @Activate constructor(
    @Reference(service = CordaConsumerBuilder::class)
    private val cordaConsumerBuilder: CordaConsumerBuilder,
): MediatorConsumerFactoryFactory {
    override fun createMessageBusConsumerFactory(
        topicName: String,
        groupName: String,
        clientId: String,
        messageBusConfig: SmartConfig,
        rebalanceListener: CordaConsumerRebalanceListener?
    ) = MessageBusConsumerFactory(
        topicName,
        groupName,
        messageBusConfig,
        cordaConsumerBuilder,
        clientId,
        rebalanceListener
    )
}