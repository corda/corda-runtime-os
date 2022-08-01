package net.corda.entityprocessor.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.entityprocessor.EntityProcessorFactory
import net.corda.entityprocessor.FlowPersistenceProcessor
import net.corda.entityprocessor.FlowPersistenceService
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
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

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
    @Reference(service = EntityProcessorFactory::class)
    private val flowEventProcessorFactory: EntityProcessorFactory
) : FlowPersistenceService {
    private var configHandle: AutoCloseable? = null
    private var flowPersistenceProcessor: FlowPersistenceProcessor? = null

    companion object {
        private val logger = contextLogger()
    }

    private val coordinator = coordinatorFactory.createCoordinator<FlowPersistenceService>(::eventHandler)
    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
        ::sandboxGroupContextComponent,
        ::virtualNodeInfoReadService,
        ::cpiInfoReadService,
    )

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.debug { "FlowPersistenceService received: $event" }
        when (event) {
            is StartEvent -> {
                logger.debug { "Starting flow persistence component." }
                dependentComponents.registerAndStartAll(coordinator)
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG)
                    )
                } else {
                    configHandle?.close()
                }
            }
            is ConfigChangedEvent -> {
                flowPersistenceProcessor?.stop()
                val newFlowPersisenceProcessor = flowEventProcessorFactory.create(
                    event.config.getConfig(MESSAGING_CONFIG)
                )
                logger.debug("Starting FlowPersistenceProcessor.")
                newFlowPersisenceProcessor.start()
                flowPersistenceProcessor = newFlowPersisenceProcessor
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            is StopEvent -> {
                flowPersistenceProcessor?.stop()
                logger.debug { "Stopping FlowPersistenceProcessor." }
                dependentComponents.stopAll()
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
