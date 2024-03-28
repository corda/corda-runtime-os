package net.corda.flow.messaging

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStatus
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.persistence.EntityRequest
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.flow.fiber.cache.FlowFiberCache
import net.corda.flow.messaging.mediator.FlowEventMediatorFactory
import net.corda.flow.messaging.mediator.FlowEventMediatorFactoryImpl
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.ledger.utxo.verification.TransactionVerificationRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.messaging.api.constants.WorkerRPCPaths.CRYPTO_PATH
import net.corda.messaging.api.constants.WorkerRPCPaths.LEDGER_PATH
import net.corda.messaging.api.constants.WorkerRPCPaths.PERSISTENCE_PATH
import net.corda.messaging.api.constants.WorkerRPCPaths.TOKEN_SELECTION_PATH
import net.corda.messaging.api.constants.WorkerRPCPaths.UNIQUENESS_PATH
import net.corda.messaging.api.constants.WorkerRPCPaths.VERIFICATION_PATH
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactoryFactory
import net.corda.messaging.api.mediator.factory.MessagingClientFactoryFactory
import net.corda.messaging.api.mediator.factory.MessagingClientFinder
import net.corda.messaging.api.mediator.factory.MultiSourceEventMediatorFactory
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_SESSION_OUT
import net.corda.schema.Schemas.Flow.FLOW_STATUS_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.MessagingConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FlowEventMediatorFactoryImplTest {
    private lateinit var flowEventMediatorFactory: FlowEventMediatorFactory
    private val flowEventProcessorFactory = mock<FlowEventProcessorFactory>()
    private val mediatorConsumerFactoryFactory = mock<MediatorConsumerFactoryFactory>()
    private val messagingClientFactoryFactory = mock<MessagingClientFactoryFactory>()
    private val multiSourceEventMediatorFactory = mock<MultiSourceEventMediatorFactory>()
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory>()
    private val platformInfoProvider = mock<PlatformInfoProvider>()
    private val flowFiberCache = mock<FlowFiberCache>()
    private val config = mock<SmartConfig>()

    val captor = argumentCaptor<EventMediatorConfig<String, Checkpoint, FlowEvent>>()

    @BeforeEach
    fun beforeEach() {
        `when`(flowEventProcessorFactory.create(any()))
            .thenReturn(mock())

        `when`(multiSourceEventMediatorFactory.create(captor.capture()))
            .thenReturn(mock())

        `when`(config.getInt(MessagingConfig.Subscription.MEDIATOR_PROCESSING_THREAD_POOL_SIZE)).thenReturn(10)

        flowEventMediatorFactory = FlowEventMediatorFactoryImpl(
            flowEventProcessorFactory,
            mediatorConsumerFactoryFactory,
            messagingClientFactoryFactory,
            multiSourceEventMediatorFactory,
            cordaAvroSerializationFactory,
            platformInfoProvider,
            flowFiberCache
        )
    }

    private fun endpoint(suffix: String): String {
        // As no config is supplied in these tests, the parameterized parts of the endpoint will be null.
        return "http://null/api/null$suffix"
    }

    @Test
    fun `successfully creates event mediator with expected routes`() {
        val mediator = flowEventMediatorFactory.create(
            mapOf(ConfigKeys.MESSAGING_CONFIG to config),
            mock(),
            mock(),
            mock()
        )
        assertNotNull(mediator)
        val clientFinder = mock<MessagingClientFinder>().apply {
            whenever(this.find(any())).thenReturn(mock())
        }
        val config = captor.firstValue
        val router = config.messageRouterFactory.create(clientFinder)
        assertThat(router.getDestination(MediatorMessage(FlowMapperEvent())).endpoint)
            .isEqualTo(FLOW_MAPPER_SESSION_OUT)
        assertThat(router.getDestination(MediatorMessage(EntityRequest())).endpoint)
            .isEqualTo(endpoint(PERSISTENCE_PATH))
        assertThat(router.getDestination(MediatorMessage(FlowOpsRequest())).endpoint)
            .isEqualTo(endpoint(CRYPTO_PATH))
        assertThat(router.getDestination(MediatorMessage(FlowStatus())).endpoint).isEqualTo(FLOW_STATUS_TOPIC)
        assertThat(router.getDestination(MediatorMessage(LedgerPersistenceRequest())).endpoint)
            .isEqualTo(endpoint(LEDGER_PATH))
        assertThat(router.getDestination(MediatorMessage(TokenPoolCacheEvent())).endpoint).isEqualTo(
            endpoint(TOKEN_SELECTION_PATH)
        )
        assertThat(router.getDestination(MediatorMessage(TransactionVerificationRequest())).endpoint).isEqualTo(
            endpoint(VERIFICATION_PATH)
        )
        assertThat(router.getDestination(MediatorMessage(UniquenessCheckRequestAvro())).endpoint).isEqualTo(
            endpoint(UNIQUENESS_PATH)
        )

        // External messaging
        val externalMessagingKafkaTopic = "custom.kafka.topic"
        val externalMessagingMessage = "message"
        assertThat(router.getDestination(
            MediatorMessage(
                payload = externalMessagingMessage,
                properties = mutableMapOf(MessagingClient.MSG_PROP_TOPIC to externalMessagingKafkaTopic)
            )
        ).endpoint).isEqualTo(externalMessagingKafkaTopic)
    }
}