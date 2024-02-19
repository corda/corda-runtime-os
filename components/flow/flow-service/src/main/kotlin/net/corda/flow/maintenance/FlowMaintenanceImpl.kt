package net.corda.flow.maintenance

import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import net.corda.schema.configuration.MessagingConfig.Subscription.PROCESSOR_TIMEOUT
import net.corda.schema.configuration.StateManagerConfig
import net.corda.utilities.debug
import net.corda.utilities.trace
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component(service = [FlowMaintenance::class])
class FlowMaintenanceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = StateManagerFactory::class)
    private val stateManagerFactory: StateManagerFactory,
    @Reference(service = FlowMaintenanceHandlersFactory::class)
    private val flowMaintenanceHandlersFactory: FlowMaintenanceHandlersFactory
) : FlowMaintenance {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val coordinator = coordinatorFactory.createCoordinator<FlowMaintenance>(::eventHandler)
    private var stateManager: StateManager? = null
    private var subscriptionRegistrationHandle: RegistrationHandle? = null

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        // Top level component is using ConfigurationReadService#registerComponentForUpdates, so all the below keys
        // should be present.
        val requiredKeys = listOf(ConfigKeys.STATE_MANAGER_CONFIG, ConfigKeys.MESSAGING_CONFIG, ConfigKeys.FLOW_CONFIG)
        if (requiredKeys.all { config.containsKey(it) }) {
            val messagingConfig = config.getConfig(ConfigKeys.MESSAGING_CONFIG)
            val newStateManagerConfig = config.getConfig(ConfigKeys.STATE_MANAGER_CONFIG)
            val flowConfig = getFlowConfig(config, messagingConfig)

            // close the lifecycle registration first to prevent down being signaled
            subscriptionRegistrationHandle?.close()
            stateManager?.stop()

            val stateManagerInstance = stateManagerFactory.create(newStateManagerConfig, StateManagerConfig.StateType.FLOW_CHECKPOINT)
                .also { it.start() }
            stateManager = stateManagerInstance

            /**
             * Task and executor for the cleanup of checkpoints that are idle or timed out.
             */
            coordinator.createManagedResource("FLOW_MAINTENANCE_SUBSCRIPTION") {
                subscriptionFactory.createDurableSubscription(
                    SubscriptionConfig(
                        "flow.maintenance.tasks",
                        Schemas.ScheduledTask.SCHEDULED_TASK_TOPIC_FLOW_PROCESSOR
                    ),
                    flowMaintenanceHandlersFactory.createScheduledTaskHandler(stateManagerInstance, flowConfig),
                    messagingConfig,
                    null
                )
            }.start()

            coordinator.createManagedResource("FLOW_TIMEOUT_SUBSCRIPTION") {
                subscriptionFactory.createDurableSubscription(
                    SubscriptionConfig(
                        "flow.timeout.task",
                        Schemas.Flow.FLOW_TIMEOUT_TOPIC
                    ),
                    flowMaintenanceHandlersFactory.createTimeoutEventHandler(stateManagerInstance, flowConfig),
                    messagingConfig,
                    null
                )
            }.start()

            subscriptionRegistrationHandle = coordinator.followStatusChangesByName(setOf(stateManagerInstance.name))
        }
    }

    /**
     * Flow logic requires some messaging config values
     */
    private fun getFlowConfig(
        config: Map<String, SmartConfig>,
        messagingConfig: SmartConfig
    ) = config.getConfig(ConfigKeys.FLOW_CONFIG)
        .withValue(PROCESSOR_TIMEOUT, ConfigValueFactory.fromAnyRef(messagingConfig.getLong(PROCESSOR_TIMEOUT)))
        .withValue(MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(messagingConfig.getLong(MAX_ALLOWED_MSG_SIZE)))

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.debug { "Flow maintenance event $event." }

        when (event) {
            is StartEvent -> {
                coordinator.updateStatus(LifecycleStatus.UP)
            }

            is StopEvent -> {
                logger.trace { "Flow maintenance is stopping..." }
                subscriptionRegistrationHandle?.close()
                stateManager?.stop()
            }
        }
    }
}
