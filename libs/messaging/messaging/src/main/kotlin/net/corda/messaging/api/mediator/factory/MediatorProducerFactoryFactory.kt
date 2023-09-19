package net.corda.messaging.api.mediator.factory

import net.corda.libs.configuration.SmartConfig

/**
 * Factory for creating multi-source event mediator producer factories.
 */
interface MediatorProducerFactoryFactory {
    /**
     * Creates a message bus producer factory.
     *
     *
     * @param id Producer ID.
     * @param messageBusConfig Message bus related configuration.
     */
    fun createMessageBusProducerFactory(
        id: String,
        messageBusConfig: SmartConfig,
    ) : MediatorProducerFactory
}