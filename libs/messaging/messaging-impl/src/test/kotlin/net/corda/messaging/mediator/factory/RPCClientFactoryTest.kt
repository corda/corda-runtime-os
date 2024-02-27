package net.corda.messaging.mediator.factory

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.messaging.api.mediator.config.MessagingClientConfig
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class RPCClientFactoryTest {
    private lateinit var cordaSerializationFactory: CordaAvroSerializationFactory
    private lateinit var rpcClientFactory: RPCClientFactory

    @BeforeEach
    fun beforeEach() {
        cordaSerializationFactory = mock(CordaAvroSerializationFactory::class.java)
        rpcClientFactory = RPCClientFactory(
            "RPCClient1",
            mock(),
            mock(),
            mock()
        )
    }

    @Test
    fun testCreateRPCClient() {
        val config = MessagingClientConfig {}
        val rpcClient = rpcClientFactory.create(config)
        Assertions.assertNotNull(rpcClient)
    }
}
