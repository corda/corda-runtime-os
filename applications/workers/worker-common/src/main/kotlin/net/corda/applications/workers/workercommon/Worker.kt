package net.corda.applications.workers.workercommon

import com.typesafe.config.Config
import net.corda.applications.common.ConfigHelper
import net.corda.applications.workers.workercommon.internal.HealthProvider
import net.corda.osgi.api.Application
import picocli.CommandLine
import picocli.CommandLine.MissingParameterException
import kotlin.math.absoluteValue
import kotlin.random.Random

/** The superclass of all Corda workers. */
abstract class Worker: Application {
    private val healthProvider = HealthProvider()

    /**
     * Sets up the worker's health-check and parses any [args] before starting the worker.
     *
     * TODO - Joel - Once decided, include some information on the accepted args.
     */
    override fun startup(args: Array<String>) {
        healthProvider.setHealthy()
        val bootstrapConfig = getBusConfig(args)
        startup(bootstrapConfig)
    }

    /** A no-op shutdown. */
    override fun shutdown() = Unit

    /**
     * Starts the worker, passing in the [busConfig].
     *
     * @param busConfig The [Config] required to connect to the bus.
     */
    abstract fun startup(busConfig: Config)

    /** Marks the worker as healthy. */
    protected fun setHealthy() = healthProvider.setHealthy()

    /** Marks the worker as unhealthy. */
    protected fun setUnhealthy() = healthProvider.setUnhealthy()

    /**
     * Retrieves the [Config] to connect to the bus
     *
     * @throws IllegalArgumentException If the `--instanceId` argument is missing, or does not have an int parameter.
     */
    @Suppress("SpreadOperator")
    private fun getBusConfig(args: Array<String>): Config {
        val parameters = WorkerParameters()
        try {
            CommandLine(parameters).parseArgs(*args)
        } catch (e: MissingParameterException) {
            throw IllegalArgumentException(
                "Argument --instanceId requires an int parameter."
            )
        }

        val instanceId = try {
            parameters.instanceId.toIntOrNull() ?: throw IllegalArgumentException(
                "Argument --instanceId requires an int parameter, but was ${parameters.instanceId}."
            )
        } catch (e: UninitializedPropertyAccessException) {
            Random.nextInt().absoluteValue
        }

        // TODO - Joel - Need to update the parsed config based on discussion with Dries.
        return ConfigHelper.getBootstrapConfig(instanceId)
    }
}

/** The expected parameters in the arguments to the worker. */
private class WorkerParameters {
    @CommandLine.Option(names = ["--instanceId"], description = ["InstanceId for this worker"])
    lateinit var instanceId: String
}