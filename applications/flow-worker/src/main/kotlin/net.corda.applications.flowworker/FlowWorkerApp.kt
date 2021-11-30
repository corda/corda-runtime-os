package net.corda.applications.flowworker

import net.corda.applications.common.ConfigHelper.Companion.getBootstrapConfig
import net.corda.flow.service.FlowService
import net.corda.sandbox.service.SandboxService
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.osgi.api.Application
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

@Component
class FlowWorkerApp @Activate constructor(
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = FlowService::class)
    private val flowService: FlowService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SandboxService::class)
    private val sandboxService: SandboxService,
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory
) : Application {

    private companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }

    private var lifeCycleCoordinator: LifecycleCoordinator? = null

    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        consoleLogger.info("Starting Flow Worker application...")
        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)
        val bootstrapConfig = getBootstrapConfig(parameters.instanceId.toInt())

        log.info("Starting life cycle coordinator for FlowWorker")
        lifeCycleCoordinator = coordinatorFactory.createCoordinator<FlowWorkerApp> { event: LifecycleEvent, _:  LifecycleCoordinator ->
            log.info("FlowWorkerApp received: $event")
            consoleLogger.info("FlowWorkerApp received: $event")

            when (event) {
                is StartEvent -> {
                    configurationReadService.start()
                    configurationReadService.bootstrapConfig(smartConfigFactory.create(bootstrapConfig))
                    flowService.start()
                    // HACK: This needs to change when we have the proper sandbox group service
                    // for now we need to start this version of the service as it hosts the new
                    // api we use elsewhere
                    sandboxService.start()
                }
                is StopEvent -> {
                    configurationReadService.stop()
                    flowService.stop()
                    sandboxService.stop()
                }
                else -> {
                    log.error("$event unexpected!")
                }
            }
        }
        lifeCycleCoordinator?.start()
        consoleLogger.info("Flow Worker application started")
    }

    override fun shutdown() {
        consoleLogger.info("Stopping application")
        lifeCycleCoordinator?.stop()
        log.info("Stopping application")
    }
}

class CliParameters {
    @CommandLine.Option(names = ["--instanceId"], description = ["InstanceId for this worker"])
    lateinit var instanceId: String
}
