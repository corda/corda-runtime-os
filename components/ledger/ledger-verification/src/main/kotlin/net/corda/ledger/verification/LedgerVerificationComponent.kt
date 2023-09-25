package net.corda.ledger.verification

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.flow.event.FlowEvent
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.ledger.utxo.verification.TransactionVerificationRequest
import net.corda.ledger.verification.processor.VerificationSubscriptionFactory
import net.corda.ledger.verification.processor.impl.VerificationRpcRequestProcessor
import net.corda.ledger.verification.sandbox.VerificationSandboxService
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SyncRPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
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
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SandboxGroupContextComponent::class)
    private val sandboxGroupContextComponent: SandboxGroupContextComponent,
    @Reference(service = VerificationSubscriptionFactory::class)
    private val verificationRequestSubscriptionFactory: VerificationSubscriptionFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = VerificationSandboxService::class)
    private val verificationSandboxService: VerificationSandboxService
) : Lifecycle {
    private var configHandle: Resource? = null
    private var verificationProcessorSubscription: Subscription<String, TransactionVerificationRequest>? = null

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        internal const val GROUP_NAME = "verification.ledger.processor"
        const val CONFIG_HANDLE = "CONFIG_HANDLE"
        const val SUBSCRIPTION_NAME = "Verification"
        const val VERIFICATION_PATH = "/verification"
        const val SUBSCRIPTION = "SUBSCRIPTION"
        const val RPC_SUBSCRIPTION = "RPC_SUBSCRIPTION"
    }

    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
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
                    configHandle?.close()
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                    )
                } else {
                    coordinator.updateStatus(event.status)
                }
            }
            is ConfigChangedEvent -> {
                verificationProcessorSubscription?.close()
                val newVerificationProcessorSubscription = verificationRequestSubscriptionFactory.create(
                    event.config.getConfig(MESSAGING_CONFIG)
                )
                val newVerificationRpcRequestProcessor()
                logger.debug("Starting LedgerVerificationComponent.")
                newVerificationProcessorSubscription.start()
                verificationProcessorSubscription = newVerificationProcessorSubscription
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            is StopEvent -> {
                verificationProcessorSubscription?.close()
                logger.debug { "Stopping LedgerVerificationComponent." }
            }
        }
    }

    private fun initialiseRpcSubscription() {
        val processor = VerificationRpcRequestProcessor(
            currentSandboxGroupContext,
            verificationSandboxService,
            UniquenessCheckRequestAvro::class.java,
            FlowEvent::class.java
        )
        lifecycleCoordinator.createManagedResource(RPC_SUBSCRIPTION) {
            val rpcConfig = SyncRPCConfig(SUBSCRIPTION_NAME, VERIFICATION_PATH)
            subscriptionFactory.createHttpRPCSubscription(rpcConfig, processor).also {
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
