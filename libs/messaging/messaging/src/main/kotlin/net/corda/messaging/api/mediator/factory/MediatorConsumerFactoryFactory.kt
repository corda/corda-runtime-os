package net.corda.messaging.api.mediator.factory

import net.corda.libs.configuration.SmartConfig

/**
 * Factory for creating multi-source event mediator consumer factories.
 */
interface MediatorConsumerFactoryFactory {
    /**
     * Creates a message bus consumer factory.
     *
     * @param topicNames Topic names.
     * @param groupName Consumer group name.
     * @param messageBusConfig Message bus related configuration.
     */
    fun createMessageBusConsumerFactory(
        topicNames: List<String>,
        groupName: String,
        messageBusConfig: SmartConfig,
    ) : MediatorConsumerFactory
}