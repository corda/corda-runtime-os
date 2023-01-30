package net.corda.processors.verification.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.ledger.verification.LedgerVerificationComponent
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.processors.verification.VerificationProcessor
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("Unused")
@Component(service = [VerificationProcessor::class])
class VerificationProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = LedgerVerificationComponent::class)
    private val ledgerVerificationComponent: LedgerVerificationComponent,
) : VerificationProcessor {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
        ::ledgerVerificationComponent
    )

    private val lifecycleCoordinator =
        coordinatorFactory.createCoordinator<VerificationProcessorImpl>(dependentComponents, ::eventHandler)

    override fun start(bootConfig: SmartConfig) {
        log.info("Verification processor starting.")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(BootConfigEvent(bootConfig))
    }

    override fun stop() {
        log.info("Verification processor stopping.")
        lifecycleCoordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.debug { "Verification processor received event $event." }

        when (event) {
            is StartEvent -> {
                // Nothing to do
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Verification processor is ${event.status}")
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

    data class BootConfigEvent(val config: SmartConfig) : LifecycleEvent
}
