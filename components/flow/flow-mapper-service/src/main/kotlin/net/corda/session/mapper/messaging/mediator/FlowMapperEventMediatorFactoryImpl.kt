package net.corda.session.mapper.messaging.mediator

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.p2p.app.AppMessage
import net.corda.flow.mapper.factory.FlowMapperEventExecutorFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.getIntOrDefault
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.RoutingDestination.Companion.routeTo
import net.corda.messaging.api.mediator.RoutingDestination.Type.ASYNCHRONOUS
import net.corda.messaging.api.mediator.config.EventMediatorConfigBuilder
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactory
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactoryFactory
import net.corda.messaging.api.mediator.factory.MessageRouterFactory
import net.corda.messaging.api.mediator.factory.MessagingClientFactoryFactory
import net.corda.messaging.api.mediator.factory.MultiSourceEventMediatorFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_SESSION_IN
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_SESSION_OUT
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_START
import net.corda.schema.Schemas.Flow.FLOW_SESSION
import net.corda.schema.Schemas.Flow.FLOW_START
import net.corda.schema.Schemas.P2P.P2P_OUT_TOPIC
import net.corda.schema.configuration.BootConfig.WORKER_MEDIATOR_REPLICAS_FLOW_MAPPER_SESSION_IN
import net.corda.schema.configuration.BootConfig.WORKER_MEDIATOR_REPLICAS_FLOW_MAPPER_SESSION_OUT
import net.corda.schema.configuration.MessagingConfig.Subscription.MEDIATOR_PROCESSING_MIN_POOL_RECORD_COUNT
import net.corda.schema.configuration.MessagingConfig.Subscription.MEDIATOR_PROCESSING_THREAD_POOL_SIZE
import net.corda.session.mapper.service.executor.FlowMapperMessageProcessor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.UUID

@Component(service = [FlowMapperEventMediatorFactory::class])
class FlowMapperEventMediatorFactoryImpl @Activate constructor(
    @Reference(service = FlowMapperEventExecutorFactory::class)
    private val flowMapperEventExecutorFactory: FlowMapperEventExecutorFactory,
    @Reference(service = MediatorConsumerFactoryFactory::class)
    private val mediatorConsumerFactoryFactory: MediatorConsumerFactoryFactory,
    @Reference(service = MessagingClientFactoryFactory::class)
    private val messagingClientFactoryFactory: MessagingClientFactoryFactory,
    @Reference(service = MultiSourceEventMediatorFactory::class)
    private val eventMediatorFactory: MultiSourceEventMediatorFactory,
) : FlowMapperEventMediatorFactory {
    companion object {
        private const val CONSUMER_GROUP = "FlowMapperConsumer"
        private const val MESSAGE_BUS_CLIENT = "MessageBusClient"
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun create(
        flowConfig: SmartConfig,
        messagingConfig: SmartConfig,
        bootConfig: SmartConfig,
        stateManager: StateManager,
    ) = eventMediatorFactory.create(
        createEventMediatorConfig(
            messagingConfig,
            FlowMapperMessageProcessor(flowMapperEventExecutorFactory, flowConfig),
            bootConfig,
            stateManager,
        )
    )

    @Suppress("SpreadOperator")
    private fun createEventMediatorConfig(
        messagingConfig: SmartConfig,
        messageProcessor: StateAndEventProcessor<String, FlowMapperState, FlowMapperEvent>,
        bootConfig: SmartConfig,
        stateManager: StateManager,
    ) = EventMediatorConfigBuilder<String, FlowMapperState, FlowMapperEvent>()
        .name("FlowMapperEventMediator")
        .messagingConfig(messagingConfig)
        .consumerFactories(
            *createMediatorConsumerFactories(messagingConfig, bootConfig).toTypedArray()
        )
        .clientFactories(
            messagingClientFactoryFactory.createMessageBusClientFactory(
                MESSAGE_BUS_CLIENT, messagingConfig
            ),
        )
        .messageProcessor(messageProcessor)
        .messageRouterFactory(createMessageRouterFactory())
        .threads(messagingConfig.getInt(MEDIATOR_PROCESSING_THREAD_POOL_SIZE))
        .threadName("flow-mapper-event-mediator")
        .stateManager(stateManager)
        .minGroupSize(messagingConfig.getInt(MEDIATOR_PROCESSING_MIN_POOL_RECORD_COUNT))
        .stateCaching(true)
        .build()

    private fun createMediatorConsumerFactories(messagingConfig: SmartConfig,  bootConfig: SmartConfig): List<MediatorConsumerFactory> {
        val clientId = "MultiSourceSubscription--$CONSUMER_GROUP--$FLOW_MAPPER_START--${UUID.randomUUID()}"
        val mediatorConsumerFactory: MutableList<MediatorConsumerFactory> = mutableListOf(
            mediatorConsumerFactoryFactory.createMessageBusConsumerFactory(
                FLOW_MAPPER_START, CONSUMER_GROUP, clientId, messagingConfig,
            ),
        )

        mediatorConsumerFactory.addAll(
            createMediatorConsumerFactories(
                messagingConfig,
                bootConfig,
                WORKER_MEDIATOR_REPLICAS_FLOW_MAPPER_SESSION_IN,
                FLOW_MAPPER_SESSION_IN
            )
        )
        mediatorConsumerFactory.addAll(
            createMediatorConsumerFactories(
                messagingConfig,
                bootConfig,
                WORKER_MEDIATOR_REPLICAS_FLOW_MAPPER_SESSION_OUT,
                FLOW_MAPPER_SESSION_OUT
            )
        )

        return mediatorConsumerFactory
    }

    private fun createMediatorConsumerFactories(
        messagingConfig: SmartConfig,
        bootConfig: SmartConfig,
        configName: String,
        topicName: String
    ): List<MediatorConsumerFactory> {
        val mediatorReplicas = bootConfig.getIntOrDefault(configName, 1)
        logger.info("Creating $mediatorReplicas mediator(s) consumer factories for $topicName")
        val mediatorConsumerFactory: List<MediatorConsumerFactory> = (1..mediatorReplicas).map {
            val clientId = "MultiSourceSubscription--$CONSUMER_GROUP--$topicName--${UUID.randomUUID()}"
            mediatorConsumerFactoryFactory.createMessageBusConsumerFactory(
                topicName, CONSUMER_GROUP, clientId, messagingConfig
            )
        }

        return mediatorConsumerFactory
    }

    private fun createMessageRouterFactory() = MessageRouterFactory { clientFinder ->
        val messageBusClient = clientFinder.find(MESSAGE_BUS_CLIENT)

        MessageRouter { message ->
            when (val event = message.payload) {
                is AppMessage -> routeTo(messageBusClient, P2P_OUT_TOPIC, ASYNCHRONOUS)
                is FlowEvent -> {
                    if (event.payload is StartFlow) {
                        routeTo(messageBusClient, FLOW_START, ASYNCHRONOUS)
                    } else {
                        routeTo(messageBusClient, FLOW_SESSION, ASYNCHRONOUS)
                    }
                }
                is FlowMapperEvent -> routeTo(messageBusClient, FLOW_MAPPER_SESSION_IN, ASYNCHRONOUS)
                else -> {
                    val eventType = event?.let { it::class.java }
                    throw IllegalStateException("No route defined for event type [$eventType]")
                }
            }
        }
    }
}