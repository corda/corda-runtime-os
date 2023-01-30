package net.corda.interop.service

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
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("LongParameterList", "Unused")
@Component(service = [InteropService::class])
class InteropService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService
) : Lifecycle {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val configSections = setOf(BOOT_CONFIG, MESSAGING_CONFIG, FLOW_CONFIG)
    }

    private var registration: RegistrationHandle? = null
    private var configHandle: AutoCloseable? = null
    private val coordinator = coordinatorFactory.createCoordinator<InteropService>(::eventHandler)

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                registration?.close()
                registration =
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                            //LifecycleCoordinatorName.forComponent<FlowExecutor>(),
                        )
                    )
                //flowExecutor.start()
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
                //val config = event.config

                /*
                 * The order is important here we need to ensure the scheduler
                 * is configured before we configure the executor to prevent a race between receiving the first
                 * state events and scheduler creating a publisher.
                 */
                //flowExecutor.onConfigChange(config)
                coordinator.updateStatus(LifecycleStatus.UP)
            }

            is StopEvent -> {
                //flowExecutor.stop()
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
