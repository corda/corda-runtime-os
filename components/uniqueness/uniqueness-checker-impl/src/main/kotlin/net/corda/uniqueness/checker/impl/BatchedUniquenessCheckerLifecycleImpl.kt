package net.corda.uniqueness.checker.impl

import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.constants.WorkerRPCPaths.UNIQUENESS_PATH
import net.corda.messaging.api.subscription.config.SyncRPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.uniqueness.backingstore.BackingStoreLifecycle
import net.corda.uniqueness.checker.UniquenessChecker
import net.corda.uniqueness.checker.UniquenessCheckerLifecycle
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

/**
 * Corda lifecycle integration for the underlying [UniquenessChecker] component.
 */
@Component(service = [UniquenessCheckerLifecycle::class])
@Suppress("LongParameterList")
class BatchedUniquenessCheckerLifecycleImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = ExternalEventResponseFactory::class)
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    @Reference(service = BackingStoreLifecycle::class)
    private val backingStoreLifecycle: BackingStoreLifecycle,
    @Reference(service = UniquenessChecker::class)
    uniquenessChecker: UniquenessChecker
) : UniquenessCheckerLifecycle, UniquenessChecker by uniquenessChecker {
    private companion object {
        const val CONFIG_HANDLE = "CONFIG_HANDLE"
        const val SUBSCRIPTION_NAME = "Uniqueness Check"
        const val RPC_SUBSCRIPTION = "RPC_SUBSCRIPTION"

        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val lifecycleCoordinator: LifecycleCoordinator =
        coordinatorFactory.createCoordinator<UniquenessCheckerLifecycle>(::eventHandler)

    private val dependentComponents = DependentComponents.of(
        ::backingStoreLifecycle
    )

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        log.info("Uniqueness checker starting")
        lifecycleCoordinator.start()
    }

    override fun stop() {
        log.info("Uniqueness checker stopping")
        lifecycleCoordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.trace("Uniqueness checker received event $event")
        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    log.trace("Starting subscription.")
                    initialiseRpcSubscription()
                    coordinator.updateStatus(LifecycleStatus.UP)
                } else {
                    coordinator.updateStatus(event.status)
                    log.trace("Stopping subscription.")
                    coordinator.closeManagedResources(setOf(CONFIG_HANDLE))
                }
            }
            is StopEvent -> {
                dependentComponents.stopAll()
                log.trace("Stopping subscription.")
                coordinator.closeManagedResources(setOf(RPC_SUBSCRIPTION))
            }
            else -> {
                log.warn("Unexpected event ${event}, ignoring")
            }
        }
    }

    private fun initialiseRpcSubscription() {
        val processor = UniquenessCheckMessageProcessor(
            this,
            externalEventResponseFactory
        )
        lifecycleCoordinator.createManagedResource(RPC_SUBSCRIPTION) {
            val rpcConfig = SyncRPCConfig(SUBSCRIPTION_NAME, UNIQUENESS_PATH)
            subscriptionFactory.createHttpRPCSubscription(rpcConfig, processor).also {
                it.start()
            }
        }
    }
}
