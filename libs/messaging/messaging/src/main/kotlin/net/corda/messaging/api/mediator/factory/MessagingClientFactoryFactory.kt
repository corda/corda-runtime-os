package net.corda.messaging.api.mediator.factory

import net.corda.libs.configuration.SmartConfig
import java.util.concurrent.Executor

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
        id: String,
        executor: Executor,
    ) : MessagingClientFactory
}
