package net.corda.session.mapper.service

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.flow.mapper.factory.FlowMapperEventExecutorFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_CLEANUP_TOPIC
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC
import net.corda.schema.Schemas.ScheduledTask.SCHEDULED_TASK_TOPIC_MAPPER_PROCESSOR
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.FlowConfig
import net.corda.session.mapper.service.executor.CleanupProcessor
import net.corda.session.mapper.service.executor.FlowMapperListener
import net.corda.session.mapper.service.executor.FlowMapperMessageProcessor
import net.corda.session.mapper.service.executor.ScheduledTaskProcessor
import net.corda.session.mapper.service.executor.ScheduledTaskState
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.concurrent.Executors
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.schema.configuration.ConfigKeys.STATE_MANAGER_CONFIG

@Suppress("LongParameterList", "ForbiddenComment")
@Component(service = [FlowMapperService::class])
class FlowMapperService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = FlowMapperEventExecutorFactory::class)
    private val flowMapperEventExecutorFactory: FlowMapperEventExecutorFactory,
    @Reference(service = StateManagerFactory::class)
    private val stateManagerFactory: StateManagerFactory
) : Lifecycle {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val CONSUMER_GROUP = "FlowMapperConsumer"
        private const val SCHEDULED_TASK_CONSUMER_GROUP = "$CONSUMER_GROUP.scheduledTasks"
        private const val CLEANUP_TASK_CONSUMER_GROUP = "$CONSUMER_GROUP.cleanup"
        private const val SUBSCRIPTION = "SUBSCRIPTION"
        private const val CLEANUP_TASK = "TASK"
        private const val REGISTRATION = "REGISTRATION"
        private const val CONFIG_HANDLE = "CONFIG_HANDLE"
        private const val SCHEDULED_TASK_PROCESSOR = "flow.mapper.scheduled.task.processor"
        private const val CLEANUP_TASK_PROCESSOR = "flow.mapper.cleanup.processor"
        private const val STATE_MANAGER = "flow.mapper.state.manager"
    }

    private val coordinator = coordinatorFactory.createCoordinator<FlowMapperService>(::eventHandler)

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

            // TODO: This can be removed once the state manager is integrated into the flow mapper and the new cleanup
            // tasks work correctly.
            val newScheduledTaskState = coordinator.createManagedResource(CLEANUP_TASK) {
                ScheduledTaskState(
                    Executors.newSingleThreadScheduledExecutor(),
                    publisherFactory.createPublisher(
                        PublisherConfig("$CONSUMER_GROUP-cleanup-publisher"),
                        messagingConfig
                    ),
                    mutableMapOf()
                )
            }

            coordinator.createManagedResource(SUBSCRIPTION) {
                subscriptionFactory.createStateAndEventSubscription(
                    SubscriptionConfig(CONSUMER_GROUP, FLOW_MAPPER_EVENT_TOPIC),
                    FlowMapperMessageProcessor(flowMapperEventExecutorFactory, flowConfig),
                    messagingConfig,
                    FlowMapperListener(newScheduledTaskState)
                )
            }.also {
                it.start()
            }
            setupCleanupTasks(messagingConfig, flowConfig, stateManagerConfig)
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
        stateManagerConfig: SmartConfig
    ) {
        val window = flowConfig.getLong(FlowConfig.PROCESSING_FLOW_CLEANUP_TIME)
        val stateManager = coordinator.createManagedResource(STATE_MANAGER) {
            stateManagerFactory.create(stateManagerConfig)
        }
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
