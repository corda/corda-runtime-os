package net.corda.messaging.mediator.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class MediatorConsumerFactoryFactoryTest {
    private lateinit var mediatorConsumerFactoryFactory: MediatorConsumerFactoryFactoryImpl
    private val cordaConsumerBuilder = mock<CordaConsumerBuilder>()
    private val messageBusConfig = mock<SmartConfig>()

    @BeforeEach
    fun beforeEach() {
        mediatorConsumerFactoryFactory = MediatorConsumerFactoryFactoryImpl(
            cordaConsumerBuilder,
        )
    }

    @Test
    fun testCreateMessageBusConsumerFactory() {
        val messageBusConsumerFactory = mediatorConsumerFactoryFactory.createMessageBusConsumerFactory(
            "topic",
            "consumerGroup",
            "clientId",
            messageBusConfig,
        )

        Assertions.assertNotNull(messageBusConsumerFactory)
    }
}