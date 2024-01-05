package net.corda.messaging.mediator.factory

import net.corda.messaging.api.mediator.config.MessagingClientConfig
import net.corda.messaging.mediator.mocks.factory.MockRPCClientFactory
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MockRPCClientFactoryTest {
    private lateinit var mockRPCClientFactory: MockRPCClientFactory

    @BeforeEach
    fun beforeEach() {
        mockRPCClientFactory = MockRPCClientFactory(
            "MockRPCClient1",
            100L
        )
    }

    @Test
    fun testCreateMockRPCClient() {
        val config = MessagingClientConfig {}
        val mockRpcClient = mockRPCClientFactory.create(config)
        assertNotNull(mockRpcClient)
    }
}
