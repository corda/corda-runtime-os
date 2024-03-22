package net.corda.messaging.api.mediator.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener

/**
 * Factory for creating multi-source event mediator consumer factories.
 */
interface MediatorConsumerFactoryFactory {
    /**
     * Creates a message bus consumer factory.
     *
     * @param topicName Topic name.
     * @param groupName Consumer group name.
     * @param clientId Consumer clientId
     * @param messageBusConfig Message bus related configuration.
     * @param rebalanceListener Message bus rebalance listener
     */
    fun createMessageBusConsumerFactory(
        topicName: String,
        groupName: String,
        clientId: String,
        messageBusConfig: SmartConfig,
        rebalanceListener: CordaConsumerRebalanceListener? = null
    ) : MediatorConsumerFactory
}