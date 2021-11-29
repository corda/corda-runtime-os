package net.corda.applications.workers.flow

import net.corda.applications.workers.workercommon.Worker
import net.corda.osgi.api.Application
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import picocli.CommandLine

// TODO - Joel - Document.
@Component(service = [Application::class])
@Suppress("unused")
class FlowWorker: Worker() {
    private companion object {
        private val logger = contextLogger()
    }

    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        logger.info("flow worker starting")

        // We comment this out for now, to avoid having to pass in any config.
//        val parameters = CliParameters()
//        CommandLine(parameters).parseArgs(*args)
//        // TODO - Joel - Is this just a debug method? Where is this config coming from?
//        val bootstrapConfig = getBootstrapConfig(parameters.instanceId.toInt())
//        FlowProcessor().startup(bootstrapConfig)

        // Sets the worker to unhealthy after 30 seconds.
//        var x = 0
//        while (true) {
//            Thread.sleep(1000)
//            println(x)
//            x++
//            if (x > 30) {
//                healthProvider.setIsUnhealthy()
//            }
//            continue
//        }
    }

    override fun shutdown() = Unit
}

// TODO - Joel - Make this part of the common worker libraries.
class CliParameters {
    @CommandLine.Option(names = ["--instanceId"], description = ["InstanceId for this worker"])
    lateinit var instanceId: String
}