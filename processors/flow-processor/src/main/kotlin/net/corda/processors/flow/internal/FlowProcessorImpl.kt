package net.corda.processors.flow.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.flow.dummy.link.DummyLinkManagerService
import net.corda.flow.service.FlowService
import net.corda.flow.session.filter.FlowSessionFilterService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.processors.flow.FlowProcessor
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.session.mapper.service.FlowMapperService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Suppress("LongParameterList", "Unused")
@Component(service = [FlowProcessor::class])
class FlowProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = FlowService::class)
    private val flowService: FlowService,
    @Reference(service = FlowMapperService::class)
    private val flowMapperService: FlowMapperService,
    @Reference(service = DummyLinkManagerService::class)
    private val dummyLinkManagerService: DummyLinkManagerService,
    @Reference(service = FlowSessionFilterService::class)
    private val flowSessionFilterService: FlowSessionFilterService,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService,
    @Reference(service = SandboxGroupContextComponent::class)
    private val sandboxGroupContextComponent: SandboxGroupContextComponent,
) : FlowProcessor {

    private companion object {
        val log: Logger = contextLogger()
    }

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<FlowProcessorImpl>(::eventHandler)
    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
        ::flowService,
        ::dummyLinkManagerService,
        ::flowMapperService,
        ::flowSessionFilterService,
        ::virtualNodeInfoReadService,
        ::cpiInfoReadService,
        ::sandboxGroupContextComponent
    )

    override fun start(bootConfig: SmartConfig) {
        log.info("Flow processor starting.")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(BootConfigEvent(bootConfig))
    }

    override fun stop() {
        log.info("Flow processor stopping.")
        lifecycleCoordinator.stop()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.debug { "Flow processor received event $event." }

        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Flow processor is ${event.status}")
                coordinator.updateStatus(event.status)
            }
            is BootConfigEvent -> {
                configurationReadService.bootstrapConfig(event.config)
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

data class BootConfigEvent(val config: SmartConfig) : LifecycleEvent