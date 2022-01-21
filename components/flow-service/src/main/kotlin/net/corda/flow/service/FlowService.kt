package net.corda.flow.service

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.flow.manager.factory.FlowEventProcessorFactory
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
import net.corda.messaging.api.config.toMessagingConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.schema.configuration.ConfigKeys.Companion.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.Companion.FLOW_CONFIG
import net.corda.schema.configuration.ConfigKeys.Companion.MESSAGING_CONFIG
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * This component is a sketch of how the flow service might be structured using the configuration service and the flow
 * libraries to put together a component that reacts to config changes. It should be read as not a finished component,
 * but rather a suggestion of how to put together the pieces to build components.
 */
@Suppress("LongParameterList")
@Component(service = [FlowService::class])
class FlowService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = FlowEventProcessorFactory::class)
    private val flowEventProcessorFactory: FlowEventProcessorFactory
) : Lifecycle {

    companion object {
        private val logger = contextLogger()
    }

    private var registration: RegistrationHandle? = null
    private var configHandle: AutoCloseable? = null
    private var executor: FlowExecutor? = null

    private val coordinator = coordinatorFactory.createCoordinator<FlowService>(::eventHandler)

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.debug { "FlowService received: $event" }
        when (event) {
            is StartEvent -> {
                logger.debug { "Starting flow runner component." }
                registration?.close()
                registration =
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                            // HACK: This needs to change when we have the proper sandbox group service
                            // for now we need to start this version of the service as it hosts the new
                            // api we use elsewhere
                            LifecycleCoordinatorName.forComponent<SandboxGroupContextComponent>(),
                            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
                            LifecycleCoordinatorName.forComponent<CpiInfoReadService>()
                        )
                    )
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(BOOT_CONFIG, FLOW_CONFIG, MESSAGING_CONFIG)
                    )
                } else {
                    configHandle?.close()
                }
            }
            is ConfigChangedEvent -> {
                executor?.stop()
                val messagingConfig = event.config.toMessagingConfig()
                val newExecutor = FlowExecutor(
                    coordinatorFactory,
                    messagingConfig,
                    subscriptionFactory,
                    flowEventProcessorFactory
                )
                newExecutor.start()
                executor = newExecutor
            }
            is StopEvent -> {
                executor?.stop()
                logger.debug { "Stopping flow runner component." }
                registration?.close()
                registration = null
            }
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
}
