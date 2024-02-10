package net.corda.session.mapper.messaging.mediator

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.p2p.app.AppMessage
import net.corda.flow.mapper.factory.FlowMapperEventExecutorFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.RoutingDestination.Companion.routeTo
import net.corda.messaging.api.mediator.RoutingDestination.Type.ASYNCHRONOUS
import net.corda.messaging.api.mediator.config.EventMediatorConfigBuilder
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
import net.corda.schema.configuration.MessagingConfig.Subscription.MEDIATOR_PROCESSING_MIN_POOL_RECORD_COUNT
import net.corda.schema.configuration.MessagingConfig.Subscription.MEDIATOR_PROCESSING_THREAD_POOL_SIZE
import net.corda.session.mapper.service.executor.FlowMapperMessageProcessor
import net.corda.utilities.concurrent.SecManagerForkJoinPool
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.Executor

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
    }

    override fun create(
        flowConfig: SmartConfig,
        messagingConfig: SmartConfig,
        stateManager: StateManager,
    ) = eventMediatorFactory.create(
        createEventMediatorConfig(
            messagingConfig,
            FlowMapperMessageProcessor(flowMapperEventExecutorFactory, flowConfig),
            stateManager,
            SecManagerForkJoinPool("FlowMapperEventMediator", messagingConfig.getInt(MEDIATOR_PROCESSING_THREAD_POOL_SIZE))
        )
    )

    private fun createEventMediatorConfig(
        messagingConfig: SmartConfig,
        messageProcessor: StateAndEventProcessor<String, FlowMapperState, FlowMapperEvent>,
        stateManager: StateManager,
        executor: Executor,
    ) = EventMediatorConfigBuilder<String, FlowMapperState, FlowMapperEvent>()
        .name("FlowMapperEventMediator")
        .messagingConfig(messagingConfig)
        .consumerFactories(
            mediatorConsumerFactoryFactory.createMessageBusConsumerFactory(
                FLOW_MAPPER_START, CONSUMER_GROUP, messagingConfig
            ),
            mediatorConsumerFactoryFactory.createMessageBusConsumerFactory(
                FLOW_MAPPER_SESSION_IN, CONSUMER_GROUP, messagingConfig
            ),
            mediatorConsumerFactoryFactory.createMessageBusConsumerFactory(
                FLOW_MAPPER_SESSION_OUT, CONSUMER_GROUP, messagingConfig
            ),
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
        .executor(executor)
        .build()

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