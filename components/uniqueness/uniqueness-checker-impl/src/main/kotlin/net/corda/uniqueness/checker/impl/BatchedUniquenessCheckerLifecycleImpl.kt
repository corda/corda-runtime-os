package net.corda.uniqueness.checker.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.flow.event.FlowEvent
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.config.SyncRPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
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
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
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
        const val GROUP_NAME = "uniqueness.checker"
        const val CONFIG_HANDLE = "CONFIG_HANDLE"
        const val SUBSCRIPTION_NAME = "Uniqueness Check"
        const val PATH = "/uniqueness-checker"
        const val SUBSCRIPTION = "SUBSCRIPTION"
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
        log.info("Uniqueness checker received event $event")
        when (event) {
            is StartEvent -> {
                configurationReadService.start()
                dependentComponents.registerAndStartAll(coordinator)
            }
            is StopEvent -> {
                dependentComponents.stopAll()
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Uniqueness checker is ${event.status}")

                if (event.status == LifecycleStatus.UP) {
                    initialiseRpcSubscription()

                    coordinator.createManagedResource(CONFIG_HANDLE) {
                        configurationReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(MESSAGING_CONFIG)
                        )
                    }
                } else {
                    coordinator.closeManagedResources(setOf(CONFIG_HANDLE))
                }
                coordinator.updateStatus(event.status)
            }
            is ConfigChangedEvent -> {
                log.info("Received configuration change event, (re)initialising subscription")
                initialiseSubscription(event.config.getConfig(MESSAGING_CONFIG))
                // RPC Subscription doesn't need to be re-created because it doesn't take config.
            }
            else -> {
                log.warn("Unexpected event ${event}, ignoring")
            }
        }
    }

    private fun initialiseSubscription(config: SmartConfig) {
        lifecycleCoordinator.createManagedResource(SUBSCRIPTION) {
            subscriptionFactory.createDurableSubscription(
                SubscriptionConfig(GROUP_NAME, Schemas.UniquenessChecker.UNIQUENESS_CHECK_TOPIC),
                UniquenessCheckMessageProcessor(
                    this,
                    externalEventResponseFactory
                ),
                config,
                null
            ).also {
                it.start()
            }
        }
    }

    private fun initialiseRpcSubscription() {
        val processor = UniquenessCheckRpcMessageProcessor(
            this,
            externalEventResponseFactory,
            UniquenessCheckRequestAvro::class.java,
            FlowEvent::class.java
        )
        lifecycleCoordinator.createManagedResource(RPC_SUBSCRIPTION) {
            val rpcConfig = SyncRPCConfig(SUBSCRIPTION_NAME, PATH)
            subscriptionFactory.createHttpRPCSubscription(rpcConfig, processor).also {
                it.start()
            }
        }
    }
}
