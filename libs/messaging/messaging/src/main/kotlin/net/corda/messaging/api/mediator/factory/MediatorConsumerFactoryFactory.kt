package net.corda.messaging.api.mediator.factory

import net.corda.libs.configuration.SmartConfig

/**
 * Factory for creating multi-source event mediator consumer factories.
 */
interface MediatorConsumerFactoryFactory {
    /**
     * Creates a message bus consumer factory.
     *
     * @param topicName Topic name.
     * @param groupName Consumer group name.
     * @param messageBusConfig Message bus related configuration.
     */
    fun createMessageBusConsumerFactory(
        topicName: String,
        groupName: String,
        messageBusConfig: SmartConfig,
    ) : MediatorConsumerFactory
}