package net.corda.applications.workers.workercommon

import com.typesafe.config.Config
import net.corda.applications.common.ConfigHelper.Companion.getBootstrapConfig
import net.corda.applications.workers.workercommon.internal.HealthProviderImpl
import net.corda.osgi.api.Application
import picocli.CommandLine

// TODO - Joel - Describe.
abstract class Worker: Application {
    protected val healthProvider: HealthProvider = HealthProviderImpl()

    init {
        healthProvider.setIsHealthy()
    }

    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        // We comment this out for now, to avoid having to pass in any config.
        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)
        // TODO - Joel - Is this just a debug method? Where is this config coming from?
        val bootstrapConfig = getBootstrapConfig(parameters.instanceId.toInt())

        // TODO - Joel - This will have to take some bootstrap config.
        startup(bootstrapConfig)
    }

    override fun shutdown() = Unit

    // TODO - Joel - Describe.
    abstract fun startup(bootstrapConfig: Config)
}

// TODO - Joel - Make this part of the common worker libraries.
// TODO - Joel - Make this private?
class CliParameters {
    @CommandLine.Option(names = ["--instanceId"], description = ["InstanceId for this worker"])
    lateinit var instanceId: String
}