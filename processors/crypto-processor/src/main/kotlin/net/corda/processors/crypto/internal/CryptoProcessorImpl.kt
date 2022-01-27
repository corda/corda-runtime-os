package net.corda.processors.crypto.internal

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.processors.crypto.CryptoProcessor
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("Unused")
@Component(service = [CryptoProcessor::class])
class CryptoProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory
) : CryptoProcessor {
    private companion object {
        val log = contextLogger()
    }

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<CryptoProcessorImpl>(::eventHandler)
    private val dependentComponents = DependentComponents.of(
        // TODO - add dependencies when they are added as `@Reference` in the constructor of this class
    )

    override fun start(bootConfig: SmartConfig) {
        log.info("Crypto processor starting.")
        lifecycleCoordinator.start()
    }

    override fun stop() {
        log.info("Crypto processor stopping.")
        lifecycleCoordinator.stop()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.debug { "Crypto processor received event $event." }

        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Crypto processor is ${event.status}")
                coordinator.updateStatus(event.status)
            }
            is StopEvent -> {
                dependentComponents.stopAll()
            }
            else -> {
                log.error("Unexpected event $event!")
            }
        }
    }
}