package net.corda.messaging.mediator.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class MediatorProducerFactoryFactoryTest {
    private lateinit var mediatorProducerFactoryFactory: MediatorProducerFactoryFactoryImpl
    private val cordaProducerBuilder = mock<CordaProducerBuilder>()
    private val messageBusConfig = mock<SmartConfig>()

    @BeforeEach
    fun beforeEach() {
        mediatorProducerFactoryFactory = MediatorProducerFactoryFactoryImpl(
            cordaProducerBuilder,
        )
    }

    @Test
    fun testCreateMessageBusProducerFactory() {
        val messageBusProducerFactory = mediatorProducerFactoryFactory.createMessageBusProducerFactory(
            "MessageBusProducer1",
            messageBusConfig,
        )

        Assertions.assertNotNull(messageBusProducerFactory)
    }
}