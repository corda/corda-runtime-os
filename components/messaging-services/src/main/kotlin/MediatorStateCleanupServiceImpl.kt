
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.Lifecycle
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
import net.corda.utilities.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
@Component(service = [MediatorStateCleanupService::class])
class MediatorStateCleanupServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = StateManagerFactory::class)
    private val stateManagerFactory: StateManagerFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
) : Lifecycle, MediatorStateCleanupService {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val coordinator = coordinatorFactory.createCoordinator<MediatorStateCleanupServiceImpl>(::eventHandler)
    private var stateManager: StateManager? = null
    private var subscriptionRegistrationHandle: RegistrationHandle? = null

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        logger.debug { "Received config. Creating state manage, mediator cleanup scheduled task and executor" }
        // Top level component is using ConfigurationReadService#registerComponentForUpdates, so all the below keys
        // should be present.
        val requiredKeys = listOf(ConfigKeys.STATE_MANAGER_CONFIG, ConfigKeys.MESSAGING_CONFIG, ConfigKeys.FLOW_CONFIG)
        if (requiredKeys.all { config.containsKey(it) }) {
            val messagingConfig = config.getConfig(ConfigKeys.MESSAGING_CONFIG)
            val newStateManagerConfig = config.getConfig(ConfigKeys.STATE_MANAGER_CONFIG)

            // close the lifecycle registration first to prevent down being signaled
            subscriptionRegistrationHandle?.close()
            stateManager?.stop()

            stateManager = stateManagerFactory.create(newStateManagerConfig).also { it.start() }
            coordinator.createManagedResource("MEDIATOR_CLEANUP_TASK_SUBSCRIPTION") {
                subscriptionFactory.createDurableSubscription(
                    SubscriptionConfig(
                        "mediator.cleanup.tasks",
                        Schemas.ScheduledTask.SCHEDULED_TASK_MEDIATOR_STATE_CLEANUP
                    ),
                    MediatorStateCleanupTask(stateManager!!, messagingConfig),
                    messagingConfig,
                    null
                )
            }.start()

            coordinator.createManagedResource("MEDIATOR_CLEANUP_EXECUTOR_SUBSCRIPTION") {
                subscriptionFactory.createDurableSubscription(
                    SubscriptionConfig(
                        "mediator.cleanup.executor",
                        Schemas.Messaging.MEDIATOR_CLEANUP_TOPIC
                    ),
                    MediatorStateCleanupExecutor(stateManager!!),
                    messagingConfig,
                    null
                )
            }.start()

            subscriptionRegistrationHandle = coordinator.followStatusChangesByName(setOf(stateManager!!.name))
        }
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                coordinator.updateStatus(LifecycleStatus.UP)
            }

            is StopEvent -> {
                subscriptionRegistrationHandle?.close()
                stateManager?.stop()
            }
        }
    }

}
