package net.corda.session.mapper.service.integration

import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.RoutingDestination
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
import net.corda.schema.configuration.MessagingConfig
import net.corda.schema.configuration.StateManagerConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.UUID

@Component(service = [TestFlowEventMediatorFactory::class])
class TestFlowEventMediatorFactoryImpl @Activate constructor(
    @Reference(service = MediatorConsumerFactoryFactory::class)
    private val mediatorConsumerFactoryFactory: MediatorConsumerFactoryFactory,
    @Reference(service = MessagingClientFactoryFactory::class)
    private val messagingClientFactoryFactory: MessagingClientFactoryFactory,
    @Reference(service = MultiSourceEventMediatorFactory::class)
    private val eventMediatorFactory: MultiSourceEventMediatorFactory,
    @Reference(service = StateManagerFactory::class)
    private val stateManagerFactory: StateManagerFactory,
) : TestFlowEventMediatorFactory {
    companion object {
        private const val CONSUMER_GROUP = "FlowEventConsumer"
        private const val MESSAGE_BUS_CLIENT = "MessageBusClient"
    }

    override fun create(
        messagingConfig: SmartConfig,
        stateManagerConfig: SmartConfig,
        flowEventProcessor: TestFlowMessageProcessor,
    ) = eventMediatorFactory.create(
        createEventMediatorConfig(
            messagingConfig
                .withValue(MessagingConfig.Subscription.POLL_TIMEOUT, ConfigValueFactory.fromAnyRef(100))
                .withValue(MessagingConfig.Subscription.PROCESSOR_RETRIES, ConfigValueFactory.fromAnyRef(1)),
            flowEventProcessor,
            stateManagerConfig,
        )
    )

    private fun createEventMediatorConfig(
        messagingConfig: SmartConfig,
        messageProcessor: StateAndEventProcessor<String, Checkpoint, FlowEvent>,
        stateManagerConfig: SmartConfig,
    ) = EventMediatorConfigBuilder<String, Checkpoint, FlowEvent>()
        .name("FlowEventMediator ${UUID.randomUUID()}")
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
        )
        .messageProcessor(messageProcessor)
        .messageRouterFactory(createMessageRouterFactory())
        .threads(1)
        .threadName("flow-event-mediator")
        .stateManager(stateManagerFactory.create(stateManagerConfig, StateManagerConfig.StateType.FLOW_CHECKPOINT))
        .minGroupSize(20)
        .build()

    private fun createMessageRouterFactory() = MessageRouterFactory { clientFinder ->
        val messageBusClient = clientFinder.find(MESSAGE_BUS_CLIENT)

        MessageRouter { message ->
            when (val event = message.payload) {
                is FlowMapperEvent -> routeTo(messageBusClient, FLOW_MAPPER_SESSION_OUT,
                    RoutingDestination.Type.ASYNCHRONOUS
                )
                else -> {
                    val eventType = event?.let { it::class.java }
                    throw IllegalStateException("No route defined for event type [$eventType]")
                }
            }
        }
    }
}