package net.corda.processors.flow.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.flow.service.FlowService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.processors.flow.FlowProcessor
import net.corda.sandbox.service.SandboxService
import net.corda.session.mapper.service.FlowMapperService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Suppress("LongParameterList", "Unused")
@Component(service = [FlowProcessor::class])
class FlowProcessorImpl @Activate constructor(
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = FlowService::class)
    private val flowService: FlowService,
    @Reference(service = FlowMapperService::class)
    private val flowMapperService: FlowMapperService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SandboxService::class)
    private val sandboxService: SandboxService
) : FlowProcessor {

    private companion object {
        val log: Logger = contextLogger()
    }

    private val lifeCycleCoordinator = coordinatorFactory.createCoordinator<FlowProcessorImpl>(::eventHandler)

    override fun start(config: SmartConfig) {
        log.info("Flow processor starting.")
        lifeCycleCoordinator.start()
        lifeCycleCoordinator.postEvent(BootConfigEvent(config))
    }

    override fun stop() {
        log.info("Stopping application")
        lifeCycleCoordinator.stop()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.debug { "Flow Processor received: $event" }
        when (event) {
            is StartEvent -> {
                configurationReadService.start()
                flowService.start()
                flowMapperService.start()
                // HACK: This needs to change when we have the proper sandbox group service
                // for now we need to start this version of the service as it hosts the new
                // api we use elsewhere
                sandboxService.start()
            }
            is BootConfigEvent -> {
                configurationReadService.bootstrapConfig(event.config)
            }
            is StopEvent -> {
                configurationReadService.stop()
                flowService.stop()
                flowMapperService.stop()
                sandboxService.stop()
            }
            else -> {
                log.error("$event unexpected!")
            }
        }
    }
}

data class BootConfigEvent(val config: SmartConfig) : LifecycleEvent
