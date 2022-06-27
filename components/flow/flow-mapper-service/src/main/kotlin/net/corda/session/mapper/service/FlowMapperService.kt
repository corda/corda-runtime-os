package net.corda.session.mapper.service

import java.util.concurrent.Executors
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.flow.mapper.factory.FlowMapperEventExecutorFactory
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Flow.Companion.FLOW_MAPPER_EVENT_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.session.mapper.service.executor.FlowMapperListener
import net.corda.session.mapper.service.executor.FlowMapperMessageProcessor
import net.corda.session.mapper.service.executor.ScheduledTaskState
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

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
        private val logger = contextLogger()
        private const val CONSUMER_GROUP = "FlowMapperConsumer"
    }

    private val coordinator = coordinatorFactory.createCoordinator<FlowMapperService>(::eventHandler)
    private var registration: RegistrationHandle? = null
    private var configHandle: AutoCloseable? = null
    private var stateAndEventSub: StateAndEventSubscription<String, FlowMapperState, FlowMapperEvent>? = null
    private var scheduledTaskState: ScheduledTaskState? = null

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                logger.info("Starting flow mapper processor component.")
                registration?.close()
                configurationReadService.start()
                registration =
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                        )
                    )
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                    )
                } else {
                    configHandle?.close()
                    coordinator.updateStatus(LifecycleStatus.DOWN, "Dependency ${coordinator.name} is DOWN")
                }
            }
            is ConfigChangedEvent -> {
                logger.info("Flow mapper processor component configuration received")
                restartFlowMapperService(event)
            }
            is StopEvent -> {
                logger.info("Stopping flow mapper component.")
                stateAndEventSub?.close()
                stateAndEventSub = null
                scheduledTaskState?.close()
                scheduledTaskState = null
                registration?.close()
                registration = null
            }
        }
    }

    /**
     * Recreate the Flow Mapper service in response to new config [event]
     */
    private fun restartFlowMapperService(event: ConfigChangedEvent) {
        try {
            val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)

            scheduledTaskState?.close()
            stateAndEventSub?.close()

            val newScheduledTaskState = ScheduledTaskState(
                Executors.newSingleThreadScheduledExecutor(),
                publisherFactory.createPublisher(PublisherConfig("$CONSUMER_GROUP-cleanup-publisher"), messagingConfig),
                mutableMapOf()
            )
            scheduledTaskState = newScheduledTaskState
            stateAndEventSub = subscriptionFactory.createStateAndEventSubscription(
                SubscriptionConfig(CONSUMER_GROUP, FLOW_MAPPER_EVENT_TOPIC),
                FlowMapperMessageProcessor(flowMapperEventExecutorFactory),
                messagingConfig,
                FlowMapperListener(newScheduledTaskState)
            )
            stateAndEventSub?.start()
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

    override fun close() {
        coordinator.close()
    }
}
