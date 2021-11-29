package net.corda.applications.workers.workercommon

import com.typesafe.config.Config
import net.corda.applications.common.ConfigHelper
import net.corda.applications.workers.workercommon.internal.HealthProvider
import net.corda.osgi.api.Application
import picocli.CommandLine

// TODO - Joel - Describe.
abstract class Worker: Application {
    private val healthProvider = HealthProvider()

    override fun startup(args: Array<String>) {
        healthProvider.setHealthy()
        val bootstrapConfig = getBootstrapConfigFromArgs(args)
        startup(bootstrapConfig)
    }

    override fun shutdown() = Unit

    // TODO - Joel - Describe.
    abstract fun startup(bootstrapConfig: Config)

    // TODO - Joel - Describe.
    protected fun setHealthy() = healthProvider.setHealthy()

    // TODO - Joel - Describe.
    protected fun setUnhealthy() = healthProvider.setUnhealthy()

    // TODO - Joel - Describe.
    @Suppress("SpreadOperator")
    private fun getBootstrapConfigFromArgs(args: Array<String>): Config {
        val parameters = WorkerParameters()
        CommandLine(parameters).parseArgs(*args)

        val instanceIdString = try {
            parameters.instanceId
        } catch (e: UninitializedPropertyAccessException) {
            throw IllegalArgumentException("Missing argument --instanceId.")
        }

        val instanceId = instanceIdString.toIntOrNull() ?: throw IllegalArgumentException(
            "Argument --instanceId requires an int value, but was ${parameters.instanceId}."
        )

        // TODO - Joel - Is this a production-ready method?
        return ConfigHelper.getBootstrapConfig(instanceId)
    }
}

// TODO - Joel - Describe.
private class WorkerParameters {
    @CommandLine.Option(names = ["--instanceId"], description = ["InstanceId for this worker"])
    lateinit var instanceId: String
}