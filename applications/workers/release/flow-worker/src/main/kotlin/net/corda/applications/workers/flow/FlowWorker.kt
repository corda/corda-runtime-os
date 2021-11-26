package net.corda.applications.workers.flow

import net.corda.osgi.api.Application
import org.osgi.service.component.annotations.Component
import picocli.CommandLine
import java.io.File

// TODO - Document.
@Component(service = [Application::class])
@Suppress("unused")
class FlowWorker: Application {
    private val healthMonitor = HealthMonitor()

    init {
        healthMonitor.setIsHealthy()
    }

    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        println("flow worker starting")

        // We comment this out for now, to avoid having to pass in any config.
//        val parameters = CliParameters()
//        CommandLine(parameters).parseArgs(*args)
//        // TODO - Is this just a debug method? Where is this config coming from?
//        val bootstrapConfig = getBootstrapConfig(parameters.instanceId.toInt())
//        FlowProcessor().startup(bootstrapConfig)

        // Sets the worker to unhealthy after 30 seconds.
        var x = 0
        while (true) {
            Thread.sleep(1000)
            println(x)
            x++
            if (x > 30) {
                healthMonitor.setIsUnhealthy()
            }
            continue
        }
    }

    override fun shutdown() = Unit
}

// TODO - Make this part of the common worker libraries.
class CliParameters {
    @CommandLine.Option(names = ["--instanceId"], description = ["InstanceId for this worker"])
    lateinit var instanceId: String
}

// TODO - Implement Docker-friendly logging.
    // TODO - Allow default Docker-friendly logging to be overridden.

// TODO - Make this part of the common worker libraries.
/**
 * Allows the health of the worker to be monitored by external processes.
 *
 * The worker is considered healthy if and only if a file exists at `/tmp/worker_is_healthy`. Otherwise, the worker is
 * considered unhealthy. Docker Healthcheck and Kubernetes liveness probes can therefore monitor the health of the
 * worker using the command `cat /tmp/worker_is_healthy`, which will return 0 if the file exists, and 1 otherwise.
 *
 * Since this file does not exist initially, the worker starts in an unhealthy state. Health checks should therefore be
 * delayed until an initial call to [setIsHealthy].
 */
class HealthMonitor {
    companion object {
        // TODO - Move to constants file.
        private const val healthCheckFileName = "/tmp/worker_is_healthy"
        private val healthCheckFile = File(healthCheckFileName)
    }

    /** Marks the worker as healthy. */
    fun setIsHealthy() = healthCheckFile.createNewFile()

    /** Marks the worker as unhealthy. */
    fun setIsUnhealthy() = healthCheckFile.delete()
}