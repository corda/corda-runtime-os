package net.corda.messaging.mediator.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messaging.api.mediator.config.MediatorConsumerConfig
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class MessageBusConsumerFactoryTest {
    private lateinit var messageBusConsumerFactory: MessageBusConsumerFactory
    private val cordaConsumerBuilder = mock<CordaConsumerBuilder>()
    private val cordaConsumer = mock<CordaConsumer<Any, Any>>()
    private val messageBusConfig = mock<SmartConfig>()

    @BeforeEach
    fun beforeEach() {
        doReturn(cordaConsumer).`when`(cordaConsumerBuilder).createConsumer(
            any(), any(), any<Class<Any>>(), any<Class<Any>>(), any(), anyOrNull()
        )
        messageBusConsumerFactory = MessageBusConsumerFactory(
            "topic",
            "group",
            messageBusConfig,
            cordaConsumerBuilder,
            "clientId",
            mock()
        )
    }

    @Test
    fun testCreateMessageBusConsumer() {
        val config = MediatorConsumerConfig(
            Any::class.java,
            Any::class.java,
        ) {}

        val messageBusConsumer = messageBusConsumerFactory.create(config)

        Assertions.assertNotNull(messageBusConsumer)
    }
}