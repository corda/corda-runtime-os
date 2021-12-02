package net.corda.applications.workers.workercommon

import net.corda.applications.workers.healthprovider.HealthProvider
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.osgi.api.Application

/**
 * The superclass of all Corda workers.
 *
 * @param smartConfigFactory The factory for creating a `SmartConfig` object from the worker's configuration.
 * @param healthProvider The health provider to track the worker's health.
 * @param params The startup parameters handled by the worker.
 */
abstract class Worker(
    private val smartConfigFactory: SmartConfigFactory,
    private val healthProvider: HealthProvider,
    private val params: WorkerParams = WorkerParams()
) : Application {

    // TODO - Joel - Provide config option to disable healthcheck.
    // TODO - Joel - Provide config option to change healthcheck port.

    /** Sets up the worker's health-check and parses any parameters via [params] before starting the worker. */
    override fun startup(args: Array<String>) {
        val workerConfig = params.parseArgs(args, smartConfigFactory)
        startup(workerConfig)
    }

    /** A no-op shutdown. */
    override fun shutdown() = Unit

    /** Starts the worker, passing in the [config]. */
    protected abstract fun startup(config: SmartConfig)
}