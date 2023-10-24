package net.corda.messaging.integration.mediator

import com.typesafe.config.ConfigFactory
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.config.EventMediatorConfigBuilder
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactoryFactory
import net.corda.messaging.api.mediator.factory.MessageRouterFactory
import net.corda.messaging.api.mediator.factory.MessagingClientFactoryFactory
import net.corda.messaging.api.mediator.factory.MultiSourceEventMediatorFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.FlowConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.osgi.test.common.annotation.InjectService

class MultiSourceEventMediatorIntegrationTest {
    @InjectService(timeout = 4000)
    lateinit var mediatorFactory: MultiSourceEventMediatorFactory

    @InjectService(timeout = 4000)
    lateinit var mediatorConsumerFactoryFactory: MediatorConsumerFactoryFactory

    @InjectService(timeout = 4000)
    lateinit var flowEventProcessorFactory: FlowEventProcessorFactory

    @InjectService(timeout = 4000)
    lateinit var stateManager: StateManager

    @InjectService(timeout = 4000)
    lateinit var messagingClientFactoryFactory: MessagingClientFactoryFactory

    private lateinit var mediator: MultiSourceEventMediator<*,*,*>

    companion object {
        const val CONSUMER_GROUP = "TEST_CONSUMER_GROUP"
        const val MESSAGE_BUS_CLIENT = "messageBusClient"
        const val RPC_CLIENT = "rpcClient"
    }

    @BeforeEach
    fun setup() {
        val configs: Map<String, SmartConfig> = mapOf()
        val smartConfigFactory: SmartConfigFactory = SmartConfigFactory.createWith(
            ConfigFactory.empty(),
            emptyList()
        )
        val messagingConfig: SmartConfig = smartConfigFactory.create(ConfigFactory.empty())

        mediator = mediatorFactory.create(
            createEventMediatorConfig(
                configs,
                messagingConfig,
                flowEventProcessorFactory.create(configs),
                stateManager
            )
        )
    }

    @AfterEach
    fun tearDown() {
        mediator.close()
    }

    private fun createEventMediatorConfig(
        configs: Map<String, SmartConfig>,
        messagingConfig: SmartConfig,
        messageProcessor: StateAndEventProcessor<String, Checkpoint, FlowEvent>,
        stateManager: StateManager,
    ): EventMediatorConfig<String, Checkpoint, FlowEvent> {
        return EventMediatorConfigBuilder<String, Checkpoint, FlowEvent>()
            .name("FlowEventMediator")
            .messagingConfig(messagingConfig)
            .consumerFactories(
                mediatorConsumerFactoryFactory.createMessageBusConsumerFactory(
                    Schemas.Flow.FLOW_EVENT_TOPIC, CONSUMER_GROUP, messagingConfig
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
            .messageRouterFactory(createMessageRouterFactory())
            .threads(configs.getConfig(ConfigKeys.FLOW_CONFIG).getInt(FlowConfig.PROCESSING_THREAD_POOL_SIZE))
            .threadName("flow-event-mediator")
            .stateManager(stateManager)
            .build()
    }

    private fun createMessageRouterFactory() = MessageRouterFactory {
        MessageRouter { message ->
            when (val event = message.payload) {
                else -> {
                    val eventType = event?.let { it::class.java }
                    throw IllegalStateException("No route defined for event type [$eventType]")
                }
            }
        }
    }
}