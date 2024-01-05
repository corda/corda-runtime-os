package net.corda.messaging.api.mediator.factory

import net.corda.libs.configuration.SmartConfig

/**
 * Factory for creating multi-source event mediator messaging client factories.
 */
interface MessagingClientFactoryFactory {
    /**
     * Creates a message bus messaging client factory.
     *
     * @param id Messaging client ID.
     * @param messageBusConfig Message bus related configuration.
     */
    fun createMessageBusClientFactory(
        id: String,
        messageBusConfig: SmartConfig,
    ) : MessagingClientFactory

    /**
     * Creates an RPC messaging client factory.
     *
     * @param id RPC client ID.
     */
    fun createRPCClientFactory(
        id: String
    ) : MessagingClientFactory

    /**
     * Creates a mock RPC messaging client factory
     *
     * @param id The identifier of this client.
     * @param delayTime The simulated delay time (in milliseconds) for the clients send() method.
     */
    fun createMockRPCClientFactory(
        id: String,
        delayTime: Long
    ) : MessagingClientFactory
}
