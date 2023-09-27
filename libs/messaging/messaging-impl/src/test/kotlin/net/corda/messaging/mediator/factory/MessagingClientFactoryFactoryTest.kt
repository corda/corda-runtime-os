package net.corda.messaging.mediator.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class MessagingClientFactoryFactoryTest {
    private lateinit var messagingClientFactoryFactory: MessagingClientFactoryFactoryImpl
    private val cordaProducerBuilder = mock<CordaProducerBuilder>()
    private val messageBusConfig = mock<SmartConfig>()

    @BeforeEach
    fun beforeEach() {
        messagingClientFactoryFactory = MessagingClientFactoryFactoryImpl(
            cordaProducerBuilder,
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
}