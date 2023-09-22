package net.corda.messaging.mediator.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.mediator.config.MessagingClientConfig
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class MessageBusClientFactoryTest {
    private lateinit var messageBusClientFactory: MessageBusClientFactory
    private val cordaProducerBuilder = mock<CordaProducerBuilder>()
    private val cordaProducer = mock<CordaProducer>()
    private val messageBusConfig = mock<SmartConfig>()

    @BeforeEach
    fun beforeEach() {
        doReturn(cordaProducer).`when`(cordaProducerBuilder).createProducer(
            any(), any(), anyOrNull()
        )
        messageBusClientFactory = MessageBusClientFactory(
            "MessageBusClient1",
            messageBusConfig,
            cordaProducerBuilder,
        )
    }

    @Test
    fun testCreateMessageBusClient() {
        val config = MessagingClientConfig {}
        val messageBusClient = messageBusClientFactory.create(config)
        Assertions.assertNotNull(messageBusClient)
    }
}