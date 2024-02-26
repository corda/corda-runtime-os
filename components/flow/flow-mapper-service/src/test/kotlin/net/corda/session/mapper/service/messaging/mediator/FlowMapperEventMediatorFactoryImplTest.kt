package net.corda.session.mapper.service.messaging.mediator

import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.flow.mapper.factory.FlowMapperEventExecutorFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactoryFactory
import net.corda.messaging.api.mediator.factory.MessagingClientFactoryFactory
import net.corda.messaging.api.mediator.factory.MultiSourceEventMediatorFactory
import net.corda.schema.configuration.MessagingConfig
import net.corda.session.mapper.messaging.mediator.FlowMapperEventMediatorFactory
import net.corda.session.mapper.messaging.mediator.FlowMapperEventMediatorFactoryImpl
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.mock

class FlowMapperEventMediatorFactoryImplTest {
    private lateinit var flowMapperEventMediatorFactory: FlowMapperEventMediatorFactory
    private val flowMapperEventExecutorFactory = mock<FlowMapperEventExecutorFactory>()
    private val mediatorConsumerFactoryFactory = mock<MediatorConsumerFactoryFactory>()
    private val messagingClientFactoryFactory = mock<MessagingClientFactoryFactory>()
    private val multiSourceEventMediatorFactory = mock<MultiSourceEventMediatorFactory>()
    private val config = mock<SmartConfig>()

    @BeforeEach
    fun beforeEach() {
        `when`(multiSourceEventMediatorFactory.create(any<EventMediatorConfig<String, FlowMapperState, FlowMapperEvent>>()))
            .thenReturn(mock())
        `when`(config.getInt(MessagingConfig.Subscription.MEDIATOR_PROCESSING_THREAD_POOL_SIZE)).thenReturn(10)

        flowMapperEventMediatorFactory = FlowMapperEventMediatorFactoryImpl(
            flowMapperEventExecutorFactory,
            mediatorConsumerFactoryFactory,
            messagingClientFactoryFactory,
            multiSourceEventMediatorFactory,
        )
    }

    @Test
    fun `successfully creates event mediator`() {
        val mediator = flowMapperEventMediatorFactory.create(mock(), config, mock(), mock())

        assertNotNull(mediator)
    }
}