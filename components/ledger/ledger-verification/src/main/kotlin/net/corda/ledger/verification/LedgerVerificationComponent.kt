package net.corda.ledger.verification

import net.corda.ledger.verification.processor.VerificationSubscriptionFactory
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.utilities.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
@Component(service = [LedgerVerificationComponent::class])
class LedgerVerificationComponent @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SandboxGroupContextComponent::class)
    private val sandboxGroupContextComponent: SandboxGroupContextComponent,
    @Reference(service = VerificationSubscriptionFactory::class)
    private val verificationRequestSubscriptionFactory: VerificationSubscriptionFactory,
) : Lifecycle {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val RPC_SUBSCRIPTION = "RPC_SUBSCRIPTION"
    }

    private val dependentComponents = DependentComponents.of(
        ::sandboxGroupContextComponent
    )
    private val lifecycleCoordinator =
        coordinatorFactory.createCoordinator<LedgerVerificationComponent>(dependentComponents, ::eventHandler)

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.debug { "LedgerVerificationComponent received: $event" }
        when (event) {
            is StartEvent -> {
                logger.debug { "Starting LedgerVerificationComponent." }
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    logger.debug("Starting LedgerVerificationComponent.")
                    initialiseRpcSubscription()
                    coordinator.updateStatus(LifecycleStatus.UP)
                } else {
                    coordinator.updateStatus(event.status)
                    coordinator.closeManagedResources(setOf(RPC_SUBSCRIPTION))
                }
            }
            is StopEvent -> {
                logger.debug { "Stopping LedgerVerificationComponent." }
                coordinator.closeManagedResources(setOf(RPC_SUBSCRIPTION))
            }
        }
    }

    private fun initialiseRpcSubscription() {
        val subscription = verificationRequestSubscriptionFactory.createSubscription()
        lifecycleCoordinator.createManagedResource(RPC_SUBSCRIPTION) {
            subscription.also {
                it.start()
            }
        }
    }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }
}
