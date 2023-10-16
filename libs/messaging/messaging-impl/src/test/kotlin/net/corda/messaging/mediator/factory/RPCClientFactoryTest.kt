package net.corda.messaging.mediator.factory

import net.corda.messaging.api.mediator.config.MessagingClientConfig
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RPCClientFactoryTest {
    private lateinit var rpcClientFactory: RPCClientFactory

    @BeforeEach
    fun beforeEach() {
        rpcClientFactory = RPCClientFactory(
            "RPCClient1"
        )
    }

    @Test
    fun testCreateRPCClient() {
        val config = MessagingClientConfig {}
        val rpcClient = rpcClientFactory.create(config)
        Assertions.assertNotNull(rpcClient)
    }
}
