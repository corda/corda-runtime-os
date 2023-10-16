package net.corda.messaging.mediator.factory

import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.config.MessagingClientConfig
import net.corda.messaging.api.mediator.factory.MessagingClientFactory
import net.corda.messaging.mediator.RPCClient

class RPCClientFactory(
    private val id: String,
): MessagingClientFactory {

    override fun create(config: MessagingClientConfig): MessagingClient {
        val httpClient = HttpClientFactory.getClient()
        return RPCClient(
            id,
            httpClient
        )
    }
}
