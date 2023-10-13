package net.corda.entityprocessor.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.persistence.EntityRequest
import net.corda.entityprocessor.EntityRequestSubscriptionFactory
import net.corda.entityprocessor.FlowPersistenceService
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.Subscription
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.utilities.debug
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
@Component(service = [FlowPersistenceService::class])
class FlowPersistenceServiceImpl  @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SandboxGroupContextComponent::class)
    private val sandboxGroupContextComponent: SandboxGroupContextComponent,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService,
    @Reference(service = EntityRequestSubscriptionFactory::class)
    private val entityRequestSubscriptionFactory: EntityRequestSubscriptionFactory
) : FlowPersistenceService {
    private var configHandle: Resource? = null
    private var entityProcessorSubscription: Subscription<String, EntityRequest>? = null

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val RPC_SUBSCRIPTION = "RPC_SUBSCRIPTION"
    }

    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
        ::sandboxGroupContextComponent,
        ::virtualNodeInfoReadService,
        ::cpiInfoReadService,
    )
    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<FlowPersistenceService>(dependentComponents, ::eventHandler)

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.debug { "FlowPersistenceService received: $event" }
        when (event) {
            is StartEvent -> {
                logger.debug { "Starting flow persistence component." }
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    configHandle?.close()
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                    )
                    initialiseRpcSubscription()
                } else {
                    coordinator.updateStatus(event.status)
                }
            }
            is ConfigChangedEvent -> {
                entityProcessorSubscription?.close()
                val newEntityProcessorSubscription = entityRequestSubscriptionFactory.create(
                    event.config.getConfig(MESSAGING_CONFIG)
                )
                logger.debug("Starting EntityProcessor.")
                newEntityProcessorSubscription.start()
                entityProcessorSubscription = newEntityProcessorSubscription
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        }
    }

    private fun initialiseRpcSubscription() {
        val subscription = entityRequestSubscriptionFactory.createRpcSubscription()
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
