package net.corda.flow.messaging.mediator

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
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.ledger.utxo.verification.TransactionVerificationRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.constants.WorkerRPCPaths.CRYPTO_PATH
import net.corda.messaging.api.constants.WorkerRPCPaths.LEDGER_PATH
import net.corda.messaging.api.constants.WorkerRPCPaths.PERSISTENCE_PATH
import net.corda.messaging.api.constants.WorkerRPCPaths.UNIQUENESS_PATH
import net.corda.messaging.api.constants.WorkerRPCPaths.VERIFICATION_PATH
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.RoutingDestination.Companion.routeTo
import net.corda.messaging.api.mediator.config.EventMediatorConfigBuilder
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactoryFactory
import net.corda.messaging.api.mediator.factory.MessageRouterFactory
import net.corda.messaging.api.mediator.factory.MessagingClientFactoryFactory
import net.corda.messaging.api.mediator.factory.MultiSourceEventMediatorFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_SESSION_OUT
import net.corda.schema.Schemas.Flow.FLOW_SESSION
import net.corda.schema.Schemas.Flow.FLOW_START
import net.corda.schema.Schemas.Flow.FLOW_STATUS_TOPIC
import net.corda.schema.Schemas.Services.TOKEN_CACHE_EVENT
import net.corda.schema.configuration.BootConfig.CRYPTO_WORKER_REST_ENDPOINT
import net.corda.schema.configuration.BootConfig.PERSISTENCE_WORKER_REST_ENDPOINT
import net.corda.schema.configuration.BootConfig.UNIQUENESS_WORKER_REST_ENDPOINT
import net.corda.schema.configuration.BootConfig.VERIFICATION_WORKER_REST_ENDPOINT
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.FlowConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(service = [FlowEventMediatorFactory::class])
class FlowEventMediatorFactoryImpl @Activate constructor(
    @Reference(service = FlowEventProcessorFactory::class)
    private val flowEventProcessorFactory: FlowEventProcessorFactory,
    @Reference(service = MediatorConsumerFactoryFactory::class)
    private val mediatorConsumerFactoryFactory: MediatorConsumerFactoryFactory,
    @Reference(service = MessagingClientFactoryFactory::class)
    private val messagingClientFactoryFactory: MessagingClientFactoryFactory,
    @Reference(service = MultiSourceEventMediatorFactory::class)
    private val eventMediatorFactory: MultiSourceEventMediatorFactory,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = PlatformInfoProvider::class)
    val platformInfoProvider: PlatformInfoProvider,
) : FlowEventMediatorFactory {
    companion object {
        private const val CONSUMER_GROUP = "FlowEventConsumer"
        private const val MESSAGE_BUS_CLIENT = "MessageBusClient"
        private const val RPC_CLIENT = "RpcClient"
    }

    private val deserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, Any::class.java)

    override fun create(
        configs: Map<String, SmartConfig>,
        messagingConfig: SmartConfig,
        stateManager: StateManager,
    ) = eventMediatorFactory.create(
        createEventMediatorConfig(
            configs,
            messagingConfig,
            flowEventProcessorFactory.create(configs),
            stateManager,
        )
    )

    private fun createEventMediatorConfig(
        configs: Map<String, SmartConfig>,
        messagingConfig: SmartConfig,
        messageProcessor: StateAndEventProcessor<String, Checkpoint, FlowEvent>,
        stateManager: StateManager,
    ) = EventMediatorConfigBuilder<String, Checkpoint, FlowEvent>()
        .name("FlowEventMediator")
        .messagingConfig(messagingConfig)
        .consumerFactories(
            mediatorConsumerFactoryFactory.createMessageBusConsumerFactory(
                FLOW_START, CONSUMER_GROUP, messagingConfig
            ),
            mediatorConsumerFactoryFactory.createMessageBusConsumerFactory(
                FLOW_SESSION, CONSUMER_GROUP, messagingConfig
            ),
        )
        .clientFactories(
            messagingClientFactoryFactory.createMessageBusClientFactory(
                MESSAGE_BUS_CLIENT, messagingConfig
            ),
            messagingClientFactoryFactory.createRPCClientFactory(
                RPC_CLIENT
            )
        )
        .messageProcessor(messageProcessor)
        .messageRouterFactory(createMessageRouterFactory(messagingConfig))
        .threads(configs.getConfig(ConfigKeys.FLOW_CONFIG).getInt(FlowConfig.PROCESSING_THREAD_POOL_SIZE))
        .threadName("flow-event-mediator")
        .stateManager(stateManager)
        .build()

    private fun createMessageRouterFactory(messagingConfig: SmartConfig) = MessageRouterFactory { clientFinder ->
        val messageBusClient = clientFinder.find(MESSAGE_BUS_CLIENT)
        val rpcClient = clientFinder.find(RPC_CLIENT)

        fun rpcEndpoint(endpoint: String, path: String) : String {
            val platformVersion = platformInfoProvider.localWorkerSoftwareShortVersion
            return "http://${messagingConfig.getString(endpoint)}/api/${platformVersion}$path"
        }

        MessageRouter { message ->
            when (val event = message.event()) {
                is EntityRequest -> routeTo(rpcClient, rpcEndpoint(PERSISTENCE_WORKER_REST_ENDPOINT, PERSISTENCE_PATH))
                is FlowMapperEvent -> routeTo(messageBusClient, FLOW_MAPPER_SESSION_OUT)
                is FlowOpsRequest -> routeTo(rpcClient, rpcEndpoint(CRYPTO_WORKER_REST_ENDPOINT, CRYPTO_PATH))
                is FlowStatus -> routeTo(messageBusClient, FLOW_STATUS_TOPIC)
                is LedgerPersistenceRequest -> routeTo(rpcClient, rpcEndpoint(PERSISTENCE_WORKER_REST_ENDPOINT, LEDGER_PATH))
                is TokenPoolCacheEvent -> routeTo(messageBusClient, TOKEN_CACHE_EVENT)
                is TransactionVerificationRequest -> routeTo(rpcClient, rpcEndpoint(VERIFICATION_WORKER_REST_ENDPOINT, VERIFICATION_PATH))
                is UniquenessCheckRequestAvro -> routeTo(rpcClient, rpcEndpoint(UNIQUENESS_WORKER_REST_ENDPOINT, UNIQUENESS_PATH))
                is FlowEvent -> routeTo(messageBusClient, FLOW_EVENT_TOPIC)
                else -> {
                    val eventType = event?.let { it::class.java }
                    throw IllegalStateException("No route defined for event type [$eventType]")
                }
            }
        }
    }

    /**
     * Deserializes message payload if it is a [ByteArray] (seems to be the case for external events).
     */
    private fun MediatorMessage<Any>.event(): Any? {
        val event = payload
        return if (event is ByteArray) {
            deserializer.deserialize(event)
        } else {
            event
        }
    }
}