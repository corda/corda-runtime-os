package net.corda.ledger.persistence

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.ledger.persistence.processor.LedgerPersistenceRequestSubscriptionFactory
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.utilities.debug
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
@Component(service = [LedgerPersistenceService::class])
class LedgerPersistenceService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SandboxGroupContextComponent::class)
    private val sandboxGroupContextComponent: SandboxGroupContextComponent,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService,
    @Reference(service = LedgerPersistenceRequestSubscriptionFactory::class)
    private val ledgerPersistenceRequestSubscriptionFactory: LedgerPersistenceRequestSubscriptionFactory
) : Lifecycle {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val RPC_SUBSCRIPTION = "RPC_SUBSCRIPTION"
    }

    private val dependentComponents = DependentComponents.of(
        ::sandboxGroupContextComponent,
        ::virtualNodeInfoReadService,
        ::cpiInfoReadService,
    )
    private val lifecycleCoordinator =
        coordinatorFactory.createCoordinator<LedgerPersistenceService>(dependentComponents, ::eventHandler)

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    logger.debug {"The status of event: $event changed to ${event.status}, starting subscription."}
                    initialiseRpcSubscription()
                    coordinator.updateStatus(LifecycleStatus.UP)
                } else {
                    coordinator.updateStatus(event.status)
                    coordinator.closeManagedResources(setOf(RPC_SUBSCRIPTION))
                    logger.debug {"The status of event: $event changed to ${event.status}, stopping subscription."}
                }
            }
            is StopEvent -> {
                coordinator.closeManagedResources(setOf(RPC_SUBSCRIPTION))
                logger.debug { "Received stop event: $event, stopping subscription." }
            }
        }
    }

    private fun initialiseRpcSubscription() {
        lifecycleCoordinator.createManagedResource(RPC_SUBSCRIPTION) {
            ledgerPersistenceRequestSubscriptionFactory.createRpcSubscription().also {
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
