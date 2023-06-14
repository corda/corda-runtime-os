package net.corda.flow.service

import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Flow.FLOW_EVENT_TOPIC
import net.corda.schema.configuration.BootConfig.BOOT_FLOW_PROCESSOR_COUNT
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
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

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val CONSUMER_GROUP = "FlowEventConsumer"
        private const val DEFAULT_PROCESSOR_COUNT = 2
        private const val MINIMUM_PROCESSOR_COUNT = 1
    }

    private val coordinator = coordinatorFactory.createCoordinator<FlowExecutor> { event, _ -> eventHandler(event) }
    private val subscriptions: MutableList<StateAndEventSubscription<String, Checkpoint, FlowEvent>> = mutableListOf()
    private var subscriptionRegistrationHandle: RegistrationHandle? = null

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        try {
            val messagingConfig = toMessagingConfig(config)
            val flowConfig = config.getConfig(FLOW_CONFIG)
                .withValue(PROCESSOR_TIMEOUT, ConfigValueFactory.fromAnyRef(messagingConfig.getLong(PROCESSOR_TIMEOUT)))
                .withValue(MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(messagingConfig.getLong(MAX_ALLOWED_MSG_SIZE)))

            // close the lifecycle registration first to prevent down being signaled
            subscriptionRegistrationHandle?.close()
            subscriptions.forEach { subscription -> subscription.close() }
            subscriptions.clear()

            val processorCount = getFlowProcessorCount(config)

            repeat(4) {
                subscriptions += subscriptionFactory.createStateAndEventSubscription(
                    SubscriptionConfig(CONSUMER_GROUP, FLOW_EVENT_TOPIC),
                    flowEventProcessorFactory.create(flowConfig),
                    messagingConfig,
                    flowExecutorRebalanceListener
                )
            }

            subscriptionRegistrationHandle = coordinator.followStatusChangesByName(
                subscriptions.map { subscription -> subscription.subscriptionName }.toSet()
            )

            subscriptions.forEach { subscription -> subscription.start() }
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
                subscriptions.forEach { subscription -> subscription.close() }
                log.trace { "Flow executor stopped" }
            }
        }
    }

    private fun getFlowProcessorCount(config: Map<String, SmartConfig>): Int {
        val bootConfig = config.getConfig(BOOT_CONFIG)
        return if (bootConfig.hasPath(BOOT_FLOW_PROCESSOR_COUNT)) {
            val count = bootConfig.getInt(BOOT_FLOW_PROCESSOR_COUNT)
            if (count > 1) count else MINIMUM_PROCESSOR_COUNT
        } else {
            DEFAULT_PROCESSOR_COUNT
        }
    }
}
