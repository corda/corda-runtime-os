package net.corda.flow.messaging.mediator.fakes

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
import net.corda.flow.messaging.mediator.FlowEventMediatorFactory
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.ledger.utxo.verification.TransactionVerificationRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.constants.WorkerRPCPaths.CRYPTO_PATH
import net.corda.messaging.api.constants.WorkerRPCPaths.LEDGER_PATH
import net.corda.messaging.api.constants.WorkerRPCPaths.PERSISTENCE_PATH
import net.corda.messaging.api.constants.WorkerRPCPaths.TOKEN_SELECTION_PATH
import net.corda.messaging.api.constants.WorkerRPCPaths.UNIQUENESS_PATH
import net.corda.messaging.api.constants.WorkerRPCPaths.VERIFICATION_PATH
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.RoutingDestination.Companion.routeTo
import net.corda.messaging.api.mediator.RoutingDestination.Type.ASYNCHRONOUS
import net.corda.messaging.api.mediator.RoutingDestination.Type.SYNCHRONOUS
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.config.EventMediatorConfigBuilder
import net.corda.messaging.api.mediator.factory.MessageRouterFactory
import net.corda.messaging.api.mediator.factory.MultiSourceEventMediatorFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.schema.Schemas.Flow.FLOW_EVENT_TOPIC
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_SESSION_OUT
import net.corda.schema.Schemas.Flow.FLOW_START
import net.corda.schema.Schemas.Flow.FLOW_STATUS_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.MessagingConfig.Subscription.MEDIATOR_PROCESSING_MIN_POOL_RECORD_COUNT
import net.corda.schema.configuration.MessagingConfig.Subscription.MEDIATOR_PROCESSING_THREAD_POOL_SIZE
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowEventMediatorFactory::class])
class TestFlowEventMediatorFactory @Activate constructor(
    @Reference(service = FlowEventProcessorFactory::class)
    private val flowEventProcessorFactory: FlowEventProcessorFactory,
    @Reference(service = MultiSourceEventMediatorFactory::class)
    private val eventMediatorFactory: MultiSourceEventMediatorFactory,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) : FlowEventMediatorFactory {
    companion object {
        private const val CONSUMER_GROUP = "FlowEventConsumer"
        private const val MESSAGE_BUS_CLIENT = "MessageBusClient"
        private const val RPC_CLIENT = "RpcClient"
    }
    private val deserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, Any::class.java)

    override fun create(
        configs: Map<String, SmartConfig>,
        messageBus: TestMessageBus,
        stateManager: StateManager,
    ) = eventMediatorFactory.create(
            createEventMediatorConfig(
                configs[ConfigKeys.MESSAGING_CONFIG]!!,
                messageBus,
                flowEventProcessorFactory.create(configs),
                stateManager,
            )
        )

    private fun createEventMediatorConfig(
        messagingConfig: SmartConfig,
        messageBus: TestMessageBus,
        messageProcessor: StateAndEventProcessor<String, Checkpoint, FlowEvent>,
        stateManager: StateManager,
    ): EventMediatorConfig<String, Checkpoint, FlowEvent> {
        val mediatorConsumerFactoryFactory = TestMediatorConsumerFactoryFactory(messageBus)
        val messagingClientFactoryFactory = TestMessagingClientFactoryFactory(messageBus)
        return EventMediatorConfigBuilder<String, Checkpoint, FlowEvent>()
            .name("TestFlowEventMediator")
            .messagingConfig(messagingConfig)
            .consumerFactories(
                mediatorConsumerFactoryFactory.createMessageBusConsumerFactory(
                    FLOW_START, CONSUMER_GROUP, messagingConfig
                ),
//                mediatorConsumerFactoryFactory.createMessageBusConsumerFactory(
//                    FLOW_SESSION, CONSUMER_GROUP, messagingConfig
//                ),
//                mediatorConsumerFactoryFactory.createMessageBusConsumerFactory(
//                    FLOW_EVENT_TOPIC, CONSUMER_GROUP, messagingConfig
//                ),
            )
            .clientFactories(
                messagingClientFactoryFactory.createMessageBusClientFactory(
                    MESSAGE_BUS_CLIENT, messagingConfig
                ),
                messagingClientFactoryFactory.createRPCClientFactory(
                    RPC_CLIENT, messagingConfig
                )
            )
            .messageProcessor(messageProcessor)
            .messageRouterFactory(createMessageRouterFactory())
            .threads(messagingConfig.getInt(MEDIATOR_PROCESSING_THREAD_POOL_SIZE))
            .threadName("test-flow-event-mediator")
            .stateManager(stateManager)
            .minGroupSize(messagingConfig.getInt(MEDIATOR_PROCESSING_MIN_POOL_RECORD_COUNT))
            .build()
    }

    private fun createMessageRouterFactory() = MessageRouterFactory { clientFinder ->
        val messageBusClient = clientFinder.find(MESSAGE_BUS_CLIENT)
        val rpcClient = clientFinder.find(RPC_CLIENT)

        fun rpcEndpoint(path: String) : String {
            return "http://localhost/api/$path"
        }

        MessageRouter { message ->
            when (val event = message.event()) {
                is EntityRequest -> routeTo(rpcClient,
                    rpcEndpoint(PERSISTENCE_PATH), SYNCHRONOUS)
                is FlowMapperEvent -> routeTo(messageBusClient,
                    FLOW_MAPPER_SESSION_OUT, ASYNCHRONOUS)
                is FlowOpsRequest -> routeTo(rpcClient,
                    rpcEndpoint(CRYPTO_PATH), SYNCHRONOUS)
                is FlowStatus -> routeTo(messageBusClient,
                    FLOW_STATUS_TOPIC, ASYNCHRONOUS)
                is LedgerPersistenceRequest -> routeTo(rpcClient,
                    rpcEndpoint(LEDGER_PATH), SYNCHRONOUS)
                is TokenPoolCacheEvent -> routeTo(rpcClient,
                    rpcEndpoint(TOKEN_SELECTION_PATH), SYNCHRONOUS)
                is TransactionVerificationRequest -> routeTo(rpcClient,
                    rpcEndpoint(VERIFICATION_PATH), SYNCHRONOUS)
                is UniquenessCheckRequestAvro -> routeTo(rpcClient,
                    rpcEndpoint(UNIQUENESS_PATH), SYNCHRONOUS)
                is FlowEvent -> routeTo(messageBusClient,
                    FLOW_EVENT_TOPIC, ASYNCHRONOUS)
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