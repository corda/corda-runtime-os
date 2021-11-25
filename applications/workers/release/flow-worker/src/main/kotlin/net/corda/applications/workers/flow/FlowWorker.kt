package net.corda.applications.workers.flow

import net.corda.applications.common.ConfigHelper.Companion.getBootstrapConfig
import net.corda.osgi.api.Application
import net.corda.processors.flow.FlowProcessor
import org.osgi.service.component.annotations.Component
import picocli.CommandLine

@Component(service = [Application::class])
@Suppress("unused")
class FlowWorker: Application {

    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)
        // TODO - Is this just a debug method? Where is this config coming from?
        val bootstrapConfig = getBootstrapConfig(parameters.instanceId.toInt())
        FlowProcessor().startup(bootstrapConfig)
    }

    override fun shutdown() = Unit
}

// TODO - Define this in a common library.
class CliParameters {
    @CommandLine.Option(names = ["--instanceId"], description = ["InstanceId for this worker"])
    lateinit var instanceId: String
}

// TODO - Override logging.

// TODO - Provide health monitoring.