package net.corda.messaging.mediator.factory

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.messaging.api.mediator.config.MessagingClientConfig
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class SyncHttpClientFactoryTest {
    private lateinit var cordaSerializationFactory: CordaAvroSerializationFactory
    private lateinit var syncHttpClientFactory: SyncHttpClientFactory

    @BeforeEach
    fun beforeEach() {
        cordaSerializationFactory = mock(CordaAvroSerializationFactory::class.java)
        syncHttpClientFactory = SyncHttpClientFactory(
            "RPCClient1",
            mock(),
            mock()
        )
    }

    @Test
    fun testCreateRPCClient() {
        val config = MessagingClientConfig {}
        val rpcClient = syncHttpClientFactory.create(config)
        Assertions.assertNotNull(rpcClient)
    }
}
