package net.corda.messaging.mediator.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.mediator.config.MediatorProducerConfig
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class MessageBusProducerFactoryTest {
    private lateinit var messageBusProducerFactory: MessageBusProducerFactory
    private val cordaProducerBuilder = mock<CordaProducerBuilder>()
    private val cordaProducer = mock<CordaProducer>()
    private val messageBusConfig = mock<SmartConfig>()

    @BeforeEach
    fun beforeEach() {
        doReturn(cordaProducer).`when`(cordaProducerBuilder).createProducer(
            any(), any(), anyOrNull()
        )
        messageBusProducerFactory = MessageBusProducerFactory(
            "MessageBusProducer1",
            messageBusConfig,
            cordaProducerBuilder,
        )
    }

    @Test
    fun testCreateMessageBusProducer() {
        val config = MediatorProducerConfig {}
        val messageBusProducer = messageBusProducerFactory.create(config)
        Assertions.assertNotNull(messageBusProducer)
    }
}