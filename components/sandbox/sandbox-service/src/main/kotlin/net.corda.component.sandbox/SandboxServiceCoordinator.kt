package net.corda.component.sandbox

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory


/**
 * This component is a sketch of how the flow service might be structured using the configuration service and the flow
 * libraries to put together a component that reacts to config changes. It should be read as not a finished component,
 * but rather a suggestion of how to put together the pieces to create components.
 */
@Component(service = [SandboxServiceCoordinator::class])
class SandboxServiceCoordinator @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SandboxService::class)
    private val sandboxService: SandboxService
) : Lifecycle {

    private companion object {
        private val logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }

    private val coordinator = coordinatorFactory.createCoordinator<SandboxServiceCoordinator>(::eventHandler)

    private var registration: RegistrationHandle? = null

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        consoleLogger.info("SandboxServiceCoordinator received: $event")
        when (event) {
            is StartEvent -> {
                logger.debug { "Starting sandbox component." }
                sandboxService.start()
                coordinator.postEvent(SandboxAvailableEvent())

                registration?.close()
                /*registration =
                    coordinator.followStatusChangesByName(
                        setOf(
                            //LifecycleCoordinatorName.forComponent<CPIService>()
                        )
                    )*/
            }
            is RegistrationStatusChangeEvent -> {
                // No need to check what registration this is as there is only one.
                if (event.status == LifecycleStatus.UP) {
                    sandboxService.start()
                    coordinator.postEvent(SandboxAvailableEvent())
                } else {
                    sandboxService.stop()
                    coordinator.updateStatus(LifecycleStatus.DOWN, "Connected to configuration repository.")
                }
            }
            is SandboxAvailableEvent -> {
                coordinator.updateStatus(LifecycleStatus.UP, "Connected to configuration repository.")
            }
            is StopEvent -> {
                logger.debug { "Stopping sandbox component." }
                sandboxService.stop()

                //does this trigger a RegistrationStatusChangeEvent?
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
