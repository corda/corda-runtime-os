package net.corda.flow.service

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.config.getConfig
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.FlowConfig
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

@Suppress("LongParameterList")
class FlowExecutor(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val config: Map<String, SmartConfig>,
    private val subscriptionFactory: SubscriptionFactory,
    private val flowEventProcessorFactory: FlowEventProcessorFactory
) : Lifecycle {

    companion object {
        private val logger = contextLogger()
        private const val CONSUMER_GROUP = "FlowEventConsumer"

        private val tempFlowConfig = ConfigFactory.empty()
            .withValue(FlowConfig.SESSION_MESSAGE_RESEND_WINDOW, ConfigValueFactory.fromAnyRef(500000L))
            .withValue(FlowConfig.SESSION_HEARTBEAT_TIMEOUT_WINDOW, ConfigValueFactory.fromAnyRef(500000L))
            .withValue(FlowConfig.PROCESSING_MAX_FLOW_SLEEP_DURATION, ConfigValueFactory.fromAnyRef(60000L))
            .withValue(FlowConfig.PROCESSING_MAX_RETRY_ATTEMPTS, ConfigValueFactory.fromAnyRef(5L))
            .withValue(FlowConfig.PROCESSING_MAX_RETRY_DELAY, ConfigValueFactory.fromAnyRef(16000L))
        private val configFactory = SmartConfigFactory.create(tempFlowConfig)
        private val tempFlowSmartConfig = configFactory.create(tempFlowConfig)
    }

    private val coordinator = coordinatorFactory.createCoordinator<FlowExecutor> { event, _ -> eventHandler(event) }

    private var messagingSubscription: StateAndEventSubscription<String, Checkpoint, FlowEvent>? = null

    private fun eventHandler(event: LifecycleEvent) {
        when (event) {
            is StartEvent -> {
                logger.debug { "Starting the flow executor" }
                val messagingConfig = config.getConfig(MESSAGING_CONFIG)
                messagingSubscription = subscriptionFactory.createStateAndEventSubscription(
                    SubscriptionConfig(CONSUMER_GROUP, FLOW_EVENT_TOPIC),
                    // TODO Needs to be reviewed as part of CORE-3780, using temporary config for FLOW_CONFIG until then
                    // flowEventProcessorFactory.create(config[FLOW_CONFIG] ?: throw CordaMessageAPIConfigException(FLOW_CONFIG)),
                    flowEventProcessorFactory.create(tempFlowSmartConfig),
                    messagingConfig
                )
                messagingSubscription?.start()
            }
            is StopEvent -> {
                logger.debug { "Flow executor terminating" }
                messagingSubscription?.close()
            }
        }
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.close()
    }
}
