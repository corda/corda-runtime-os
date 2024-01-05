package net.corda.messaging.mediator.mocks.factory

import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.config.MessagingClientConfig
import net.corda.messaging.api.mediator.factory.MessagingClientFactory
import net.corda.messaging.mediator.mocks.MockRPCClient

class MockRPCClientFactory(
    private val id: String,
    private val delayTime: Long
) : MessagingClientFactory {
    override fun create(config: MessagingClientConfig): MessagingClient {
        return MockRPCClient(
            id,
            delayTime
        )
    }
}
