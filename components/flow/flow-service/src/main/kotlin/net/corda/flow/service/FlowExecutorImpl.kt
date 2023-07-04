package net.corda.flow.service

import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.*
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import net.corda.schema.configuration.MessagingConfig.Subscription.PROCESSOR_TIMEOUT
import net.corda.utilities.trace
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
@Component(service = [FlowExecutor::class])
class FlowExecutorImpl constructor(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val subscriptionFactory: SubscriptionFactory,
    private val flowEventProcessorFactory: FlowEventProcessorFactory,
    private val flowExecutorRebalanceListener: FlowExecutorRebalanceListener,
    private val toMessagingConfig: (Map<String, SmartConfig>) -> SmartConfig
) : FlowExecutor {

    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = SubscriptionFactory::class)
        subscriptionFactory: SubscriptionFactory,
        @Reference(service = FlowEventProcessorFactory::class)
        flowEventProcessorFactory: FlowEventProcessorFactory,
        @Reference(service = FlowExecutorRebalanceListener::class)
        flowExecutorRebalanceListener: FlowExecutorRebalanceListener
    ) : this(
        coordinatorFactory,
        subscriptionFactory,
        flowEventProcessorFactory,
        flowExecutorRebalanceListener,
        { cfg -> cfg.getConfig(MESSAGING_CONFIG) }
    )

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val CONSUMER_GROUP = "FlowEventConsumer"
    }

    private val coordinator = coordinatorFactory.createCoordinator<FlowExecutor> { event, _ -> eventHandler(event) }
    private var subscription: StateAndEventSubscription<String, Checkpoint, FlowEvent>? = null
    private var subscriptionRegistrationHandle: RegistrationHandle? = null

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        try {
            if (System.getenv("ENABLE_FLOW_PROCESS").equals("TRUE", true)) {
//            val topic = config.getConfig(BOOT_CONFIG).getInputTopic(CONSUMER_GROUP, FLOW_EVENT_TOPIC)
                //HARDCODED: Point the process to a custom flow processor deployment
                val flowProcessorTopic = System.getenv("FLOW_PROCESSOR_TOPIC")
                val consumerGroup = "$CONSUMER_GROUP-$flowProcessorTopic"
                val messagingConfig = toMessagingConfig(config)
                val flowConfig = config.getConfig(FLOW_CONFIG)
                    .withValue(
                        PROCESSOR_TIMEOUT,
                        ConfigValueFactory.fromAnyRef(messagingConfig.getLong(PROCESSOR_TIMEOUT))
                    )
                    .withValue(
                        MAX_ALLOWED_MSG_SIZE,
                        ConfigValueFactory.fromAnyRef(messagingConfig.getLong(MAX_ALLOWED_MSG_SIZE))
                    )

                // close the lifecycle registration first to prevent down being signaled
                subscriptionRegistrationHandle?.close()
                subscription?.close()

                subscription = subscriptionFactory.createStateAndEventSubscription(
                    //NOTE: To consume from flow event topic
                    SubscriptionConfig(consumerGroup, flowProcessorTopic),
                    flowEventProcessorFactory.create(flowConfig),
                    messagingConfig,
                    flowExecutorRebalanceListener
                )

                subscriptionRegistrationHandle = coordinator.followStatusChangesByName(
                    setOf(subscription!!.subscriptionName)
                )

                subscription?.start()
            } else {
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        } catch (ex: Exception) {
            val reason = "Failed to configure the flow executor using '${config}'"
            log.error(reason, ex)
            coordinator.updateStatus(LifecycleStatus.ERROR, reason)
        }
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent) {
        when (event) {
            is StartEvent -> {
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            is StopEvent -> {
                log.trace { "Flow executor is stopping..." }
                subscriptionRegistrationHandle?.close()
                subscription?.close()
                log.trace { "Flow executor stopped" }
            }
        }
    }
}
