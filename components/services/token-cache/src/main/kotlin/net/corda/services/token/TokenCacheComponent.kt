package net.corda.services.token

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
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
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(service = [TokenCacheComponent::class])
class TokenCacheComponent @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = FlowExecutor::class)
    private val flowExecutor: FlowExecutor,
    @Reference(service = FlowWakeUpScheduler::class)
    private val flowWakeUpScheduler: FlowWakeUpScheduler
) : Lifecycle {

    companion object {
        private val logger = contextLogger()
        private val configSections = setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG, ConfigKeys.FLOW_CONFIG)
    }

    private var registration: RegistrationHandle? = null
    private var configHandle: AutoCloseable? = null
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
                            LifecycleCoordinatorName.forComponent<SandboxGroupContextComponent>(),
                            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
                            LifecycleCoordinatorName.forComponent<CpiInfoReadService>(),
                            LifecycleCoordinatorName.forComponent<FlowExecutor>(),
                        )
                    )
                flowExecutor.start()
            }

            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    configHandle?.close()
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        configSections
                    )
                }else {
                    coordinator.updateStatus(event.status)
                }
            }

            is ConfigChangedEvent -> {
                val config = event.config

                /*
                 * The order is important here we need to ensure the scheduler
                 * is configured before we configure the executor to prevent a race between receiving the first
                 * state events and scheduler creating a publisher.
                 */
                flowWakeUpScheduler.onConfigChange(config)
                flowExecutor.onConfigChange(config)
                coordinator.updateStatus(LifecycleStatus.UP)
            }

            is StopEvent -> {
                flowExecutor.stop()
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
