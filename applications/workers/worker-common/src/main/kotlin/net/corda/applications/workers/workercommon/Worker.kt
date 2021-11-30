package net.corda.applications.workers.workercommon

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.applications.workers.workercommon.internal.CONFIG_NAME_EXTRA_PARAMS
import net.corda.applications.workers.workercommon.internal.CONFIG_NAME_INSTANCE_ID
import net.corda.applications.workers.workercommon.internal.FileBasedHealthProvider
import net.corda.applications.workers.workercommon.internal.WorkerParams
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.osgi.api.Application
import picocli.CommandLine

/**
 * The superclass of all Corda workers.
 *
 * @param smartConfigFactory The factory for creating a `SmartConfig` object from the worker's configuration.
 */
abstract class Worker(private val smartConfigFactory: SmartConfigFactory) : Application {
    private val healthProvider = FileBasedHealthProvider()

    /** Sets up the worker's health-check and parses any [args] per [WorkerParams] before starting the worker. */
    override fun startup(args: Array<String>) {
        healthProvider.setHealthy()
        val workerConfig = getWorkerConfig(args)
        startup(healthProvider, workerConfig)
    }

    /** A no-op shutdown. */
    override fun shutdown() = Unit

    /**
     * Starts the worker, passing in the [workerConfig].
     *
     * @param workerConfig The [Config] required by the worker.
     */
    protected abstract fun startup(healthProvider: HealthProvider, workerConfig: Config)

    /**
     * Retrieves the [Config] to connect to the bus
     *
     * @throws IllegalArgumentException If the `--instanceId` argument is missing, or does not have an int parameter.
     */
    private fun getWorkerConfig(args: Array<String>): Config {
        val params = WorkerParams()
        val commandLine = CommandLine(params)

        try {
            commandLine.parseArgs(*args)
        } catch (e: CommandLine.ParameterException) {
            throw IllegalArgumentException(e.message)
        }

        return smartConfigFactory.create(
            ConfigFactory
                .empty()
                .withValue(CONFIG_NAME_INSTANCE_ID, ConfigValueFactory.fromAnyRef(params.instanceId))
                .withValue(CONFIG_NAME_EXTRA_PARAMS, ConfigValueFactory.fromAnyRef(params.additionalParams))
        )
    }
}