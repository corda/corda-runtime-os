package net.corda.applications.workers.workercommon

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfigFactory
import picocli.CommandLine

/**
 * Parses the [args] into the [params].
 *
 * @throws IllegalArgumentException If parsing fails.
 */
fun <T> getParams(args: Array<String>, params: T): T {
    val commandLine = CommandLine(params)
    try {
        commandLine.parseArgs(*args)
    } catch (e: CommandLine.ParameterException) {
        throw IllegalArgumentException(e.message)
    }
    return params
}

/** Uses [smartConfigFactory] to create a `SmartConfig` wrapping the worker's additional parameters in [params]. */
fun getAdditionalConfig(params: DefaultWorkerParams, smartConfigFactory: SmartConfigFactory) =
    smartConfigFactory.create(ConfigFactory.parseMap(params.additionalParams))

/** Sets up the [healthMonitor] based on the [params]. */
fun setUpHealthMonitor(healthMonitor: HealthMonitor, params: DefaultWorkerParams) {
    if (!params.disableHealthMonitor) {
        healthMonitor.listen(params.healthMonitorPort)
    }
}