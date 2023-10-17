package net.corda.flow.messaging

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.messaging.mediator.FlowEventMediatorFactory
import net.corda.flow.messaging.mediator.FlowEventMediatorFactoryImpl
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactoryFactory
import net.corda.messaging.api.mediator.factory.MessagingClientFactoryFactory
import net.corda.messaging.api.mediator.factory.MultiSourceEventMediatorFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.FlowConfig
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.mock

class FlowEventMediatorFactoryImplTest {
    private lateinit var flowEventMediatorFactory: FlowEventMediatorFactory
    private val flowEventProcessorFactory = mock<FlowEventProcessorFactory>()
    private val mediatorConsumerFactoryFactory = mock<MediatorConsumerFactoryFactory>()
    private val messagingClientFactoryFactory = mock<MessagingClientFactoryFactory>()
    private val multiSourceEventMediatorFactory = mock<MultiSourceEventMediatorFactory>()
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory>()
    private val platformInfoProvider = mock<PlatformInfoProvider>()
    private val flowConfig = mock<SmartConfig>()

    @BeforeEach
    fun beforeEach() {
        `when`(flowEventProcessorFactory.create(any()))
            .thenReturn(mock())

        `when`(multiSourceEventMediatorFactory.create(any<EventMediatorConfig<String, Checkpoint, FlowEvent>>()))
            .thenReturn(mock())

        `when`(flowConfig.getInt(FlowConfig.PROCESSING_THREAD_POOL_SIZE)).thenReturn(10)

        flowEventMediatorFactory = FlowEventMediatorFactoryImpl(
            flowEventProcessorFactory,
            mediatorConsumerFactoryFactory,
            messagingClientFactoryFactory,
            multiSourceEventMediatorFactory,
            cordaAvroSerializationFactory,
            platformInfoProvider
        )
    }

    @Test
    fun `successfully creates event mediator`() {
        val mediator = flowEventMediatorFactory.create(
            mapOf(ConfigKeys.FLOW_CONFIG to flowConfig),
            mock(),
            mock(),
        )

        assertNotNull(mediator)
    }
}