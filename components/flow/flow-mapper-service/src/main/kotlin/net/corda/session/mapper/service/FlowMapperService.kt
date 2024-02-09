package net.corda.session.mapper.service

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_CLEANUP_TOPIC
import net.corda.schema.Schemas.ScheduledTask.SCHEDULED_TASK_TOPIC_MAPPER_PROCESSOR
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.ConfigKeys.STATE_MANAGER_CONFIG
import net.corda.schema.configuration.FlowConfig
import net.corda.schema.configuration.StateManagerConfig
import net.corda.session.mapper.messaging.mediator.FlowMapperEventMediatorFactory
import net.corda.session.mapper.service.executor.CleanupProcessor
import net.corda.session.mapper.service.executor.ScheduledTaskProcessor
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Clock

@Suppress("LongParameterList", "ForbiddenComment")
@Component(service = [FlowMapperService::class])
class FlowMapperService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = FlowMapperEventMediatorFactory::class)
    private val flowMapperEventMediatorFactory: FlowMapperEventMediatorFactory,
    @Reference(service = StateManagerFactory::class)
    private val stateManagerFactory: StateManagerFactory
) : Lifecycle {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val CONSUMER_GROUP = "FlowMapperConsumer"
        private const val SCHEDULED_TASK_CONSUMER_GROUP = "$CONSUMER_GROUP.scheduledTasks"
        private const val CLEANUP_TASK_CONSUMER_GROUP = "$CONSUMER_GROUP.cleanup"
        private const val EVENT_MEDIATOR = "EVENT_MEDIATOR"
        private const val REGISTRATION = "REGISTRATION"
        private const val CONFIG_HANDLE = "CONFIG_HANDLE"
        private const val CLEANUP_TASK_PROCESSOR = "flow.mapper.cleanup.processor"
        private const val SCHEDULED_TASK_PROCESSOR = "flow.mapper.scheduled.task.processor"
    }

    private val coordinator = coordinatorFactory.createCoordinator<FlowMapperService>(::eventHandler)
    private var stateManager: StateManager? = null

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                configurationReadService.start()
                coordinator.createManagedResource(REGISTRATION) {
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                            LifecycleCoordinatorName.forComponent<LocallyHostedIdentitiesService>()
                        )
                    )
                }
            }

            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    coordinator.createManagedResource(CONFIG_HANDLE) {
                        configurationReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(FLOW_CONFIG, MESSAGING_CONFIG, STATE_MANAGER_CONFIG)
                        )
                    }
                } else {
                    coordinator.closeManagedResources(setOf(CONFIG_HANDLE))
                    coordinator.updateStatus(LifecycleStatus.DOWN, "Dependency ${coordinator.name} is DOWN")
                }
            }

            is ConfigChangedEvent -> {
                restartFlowMapperService(event)
            }

            is StopEvent -> {
                stateManager?.stop()
            }
        }
    }

    /**
     * Recreate the Flow Mapper service in response to new config [event]
     */
    private fun restartFlowMapperService(event: ConfigChangedEvent) {
        try {
            val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
            val flowConfig = event.config.getConfig(FLOW_CONFIG)
            val stateManagerConfig = event.config.getConfig(STATE_MANAGER_CONFIG)

            stateManager?.stop()
            stateManager = stateManagerFactory.create(stateManagerConfig, StateManagerConfig.StateType.FLOW_MAPPING)
                .also { it.start() }

            coordinator.createManagedResource(EVENT_MEDIATOR) {
                flowMapperEventMediatorFactory.create(
                    flowConfig,
                    messagingConfig,
                    stateManager!!,
                )
            }.also {
                it.start()
            }

            setupCleanupTasks(messagingConfig, flowConfig, stateManager!!)
            coordinator.updateStatus(LifecycleStatus.UP)
        } catch (e: CordaRuntimeException) {
            val errorMsg = "Error restarting flow mapper from config change"
            logger.error(errorMsg)
            coordinator.updateStatus(LifecycleStatus.ERROR, errorMsg)
        }
    }

    private fun setupCleanupTasks(
        messagingConfig: SmartConfig,
        flowConfig: SmartConfig,
        stateManager: StateManager
    ) {
        val window = flowConfig.getLong(FlowConfig.PROCESSING_FLOW_MAPPER_CLEANUP_TIME)
        val scheduledTaskProcessor = ScheduledTaskProcessor(
            stateManager,
            Clock.systemUTC(),
            window
        )
        val cleanupProcessor = CleanupProcessor(stateManager)
        coordinator.createManagedResource(SCHEDULED_TASK_PROCESSOR) {
            subscriptionFactory.createDurableSubscription(
                SubscriptionConfig(SCHEDULED_TASK_CONSUMER_GROUP, SCHEDULED_TASK_TOPIC_MAPPER_PROCESSOR),
                scheduledTaskProcessor,
                messagingConfig,
                null
            )
        }.also {
            it.start()
        }

        coordinator.createManagedResource(CLEANUP_TASK_PROCESSOR) {
            subscriptionFactory.createDurableSubscription(
                SubscriptionConfig(CLEANUP_TASK_CONSUMER_GROUP, FLOW_MAPPER_CLEANUP_TOPIC),
                cleanupProcessor,
                messagingConfig,
                null
            )
        }.also {
            it.start()
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

    @Deactivate
    fun close() {
        coordinator.close()
    }
}
