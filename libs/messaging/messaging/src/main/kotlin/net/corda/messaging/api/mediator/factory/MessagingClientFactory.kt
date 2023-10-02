package net.corda.messaging.api.mediator.factory

import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.config.MessagingClientConfig

/**
 * Factory for creating multi-source event mediator messaging clients.
 */
interface MessagingClientFactory {

    /**
     * Creates a multi-source event mediator messaging client.
     *
     * @param config Multi-source event mediator messaging client configuration.
     */
    fun create(config: MessagingClientConfig): MessagingClient
}