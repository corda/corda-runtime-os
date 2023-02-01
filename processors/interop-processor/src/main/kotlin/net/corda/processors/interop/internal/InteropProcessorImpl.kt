package net.corda.processors.interop.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.interop.InteropService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.processors.interop.InteropProcessor
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("LongParameterList", "Unused")
@Component(service = [InteropProcessor::class])
class InteropProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = InteropService::class)
    private val interopService: InteropService,
) : InteropProcessor {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
        ::interopService
    )

    private val lifecycleCoordinator =
        coordinatorFactory.createCoordinator<InteropProcessorImpl>(dependentComponents, ::eventHandler)

    override fun start(bootConfig: SmartConfig) {
        log.info("Interop processor starting.")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(BootConfigEvent(bootConfig))
    }

    override fun stop() {
        log.info("Interop processor stopping.")
        lifecycleCoordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.debug { "Interop processor received event $event." }

        when (event) {
            is StartEvent -> {
                // Nothing to do
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Interop processor is ${event.status}")
                coordinator.updateStatus(event.status)
            }
            is BootConfigEvent -> {
                configurationReadService.bootstrapConfig(event.config)
            }
            is StopEvent -> {
                // Nothing to do
            }
            else -> {
                log.error("Unexpected event $event!")
            }
        }
    }
}

data class BootConfigEvent(val config: SmartConfig) : LifecycleEvent
