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

    /** Sets up the worker's health-check and parses any parameters via [params] before starting the worker. */
    override fun startup(args: Array<String>) {
        healthProvider.setHealthy()
        val workerConfig = params.parseArgs(args, smartConfigFactory)
        try {
            startup(healthProvider, workerConfig)
        } catch (e: Exception) {
            healthProvider.setNotHealthy()
            healthProvider.setNotReady()
        }
    }

    /** A no-op shutdown. */
    override fun shutdown() = Unit

    /**
     * Starts the worker, passing in the [workerConfig].
     *
     * @param workerConfig The [SmartConfig] required by the worker.
     */
    protected abstract fun startup(healthProvider: HealthProvider, workerConfig: SmartConfig)
}