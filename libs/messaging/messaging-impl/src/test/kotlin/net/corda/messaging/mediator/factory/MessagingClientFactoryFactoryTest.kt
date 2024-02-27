package net.corda.messaging.mediator.factory

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.concurrent.Executor

class MessagingClientFactoryFactoryTest {
    private lateinit var messagingClientFactoryFactory: MessagingClientFactoryFactoryImpl
    private val cordaProducerBuilder = mock<CordaProducerBuilder>()
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory>()
    private val platformDigestService = mock<PlatformDigestService>()
    private val messageBusConfig = mock<SmartConfig>()
    private val executor = mock<Executor>()

    @BeforeEach
    fun beforeEach() {
        messagingClientFactoryFactory = MessagingClientFactoryFactoryImpl(
            cordaProducerBuilder,
            cordaAvroSerializationFactory,
            platformDigestService
        )
    }

    @Test
    fun testCreateMessageBusClientFactory() {
        val messageBusClientFactory = messagingClientFactoryFactory.createMessageBusClientFactory(
            "MessageBusClient1",
            messageBusConfig,
        )

        Assertions.assertNotNull(messageBusClientFactory)
    }

    @Test
    fun testCreateRPCClientFactory() {
        val rpcClientFactory = messagingClientFactoryFactory.createRPCClientFactory(
            "rpcClient1",
            executor
        )

        Assertions.assertNotNull(rpcClientFactory)
    }
}
