package net.corda.flow.maintenance

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
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
) : FlowMaintenance {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val coordinator = coordinatorFactory.createCoordinator<FlowMaintenance>(::eventHandler)
    private var stateManagerConfig: SmartConfig? = null
    private var stateManager: StateManager? = null

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        // TODO - fix config key (CORE-17437).
        if(config.containsKey(ConfigKeys.MESSAGING_CONFIG)) {
            val messagingConfig = config.getConfig(ConfigKeys.MESSAGING_CONFIG)
            val newStateManagerConfig = config.getConfig(ConfigKeys.MESSAGING_CONFIG)
            // Only re-configure the state manager if the config has changes.
            // This should not be needed anymore once it's a separate key.
            if(newStateManagerConfig != stateManagerConfig) {
                stateManager?.close()
                stateManager = stateManagerFactory.create(newStateManagerConfig)
                stateManagerConfig = newStateManagerConfig
            }
            coordinator.createManagedResource("FLOW_MAINTENANCE_SUBSCRIPTION") {
                subscriptionFactory.createDurableSubscription(
                    SubscriptionConfig(
                        "flow.maintenance.tasks",
                        Schemas.ScheduledTask.SCHEDULED_TASK_TOPIC_FLOW_PROCESSOR
                    ),
                    SessionTimeoutTaskProcessor(stateManager!!),
                    messagingConfig,
                    null
                )
            }.start()
        }
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        stateManager?.close()
        stateManager = null
        coordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.debug { "Flow maintenance event $event." }

        when (event) {
            is StartEvent -> {
                coordinator.updateStatus(LifecycleStatus.UP)
                // TODO - this should register to follow the State Manager's lifecycle
            }
            is StopEvent -> {
                logger.trace { "Flow maintenance is stopping..." }
            }
        }
    }
}