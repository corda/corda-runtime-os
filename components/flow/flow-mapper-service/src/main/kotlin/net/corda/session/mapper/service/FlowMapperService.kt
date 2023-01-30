package net.corda.session.mapper.service

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.flow.mapper.factory.FlowMapperEventExecutorFactory
import net.corda.libs.configuration.helper.getConfig
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
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Flow.Companion.FLOW_MAPPER_EVENT_TOPIC
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.session.mapper.service.executor.FlowMapperListener
import net.corda.session.mapper.service.executor.FlowMapperMessageProcessor
import net.corda.session.mapper.service.executor.ScheduledTaskState
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors

@Component(service = [FlowMapperService::class], immediate = true)
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
) : Lifecycle {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val CONSUMER_GROUP = "FlowMapperConsumer"

        private const val SUBSCRIPTION = "SUBSCRIPTION"
        private const val CLEANUP_TASK = "TASK"
        private const val REGISTRATION = "REGISTRATION"
        private const val CONFIG_HANDLE = "CONFIG_HANDLE"
    }

    private val coordinator = coordinatorFactory.createCoordinator<FlowMapperService>(::eventHandler)

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                configurationReadService.start()
                coordinator.createManagedResource(REGISTRATION) {
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                        )
                    )
                }
            }

            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    coordinator.createManagedResource(CONFIG_HANDLE) {
                        configurationReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(FLOW_CONFIG, MESSAGING_CONFIG)
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

            coordinator.createManagedResource(CLEANUP_TASK) {
                ScheduledTaskState(
                    Executors.newSingleThreadScheduledExecutor(),
                    publisherFactory.createPublisher(
                        PublisherConfig("$CONSUMER_GROUP-cleanup-publisher"),
                        messagingConfig
                    ),
                    mutableMapOf()
                )
            }
            val newScheduledTaskState = coordinator.getManagedResource<ScheduledTaskState>(CLEANUP_TASK)!!

            coordinator.createManagedResource(SUBSCRIPTION) {
                subscriptionFactory.createStateAndEventSubscription(
                    SubscriptionConfig(CONSUMER_GROUP, FLOW_MAPPER_EVENT_TOPIC),
                    FlowMapperMessageProcessor(flowMapperEventExecutorFactory, flowConfig),
                    messagingConfig,
                    FlowMapperListener(newScheduledTaskState)
                )
            }
            coordinator.getManagedResource<StateAndEventSubscription<*, *, *>>(SUBSCRIPTION)!!.start()
            coordinator.updateStatus(LifecycleStatus.UP)
        } catch (e: CordaRuntimeException) {
            val errorMsg = "Error restarting flow mapper from config change"
            logger.error(errorMsg)
            coordinator.updateStatus(LifecycleStatus.ERROR, errorMsg)
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
