package net.corda.applications.workers.workercommon

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.osgi.api.Shutdown
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.FrameworkUtil
import picocli.CommandLine

// TODO - Joel - Describe.
class WorkerHelpers {
    companion object {
        private val logger = contextLogger()

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

        /**
         * Prints help or version if requested.
         *
         * If help or version are printed, the application is shut down.
         *
         * @return `true` if help or version has been printed, `false` otherwise.
         */
        fun printHelpOrVersion(
            params: DefaultWorkerParams,
            applicationClass: Class<*>,
            shutdownService: Shutdown
        ): Boolean {
            if (params.helpRequested) {
                CommandLine.usage(params, System.out)

            } else if (params.versionRequested) {
                logger.info("Specification version: ${applicationClass.`package`.specificationVersion}")
                logger.info("Implementation version: ${applicationClass.`package`.implementationVersion}")
            }

            if (params.helpRequested || params.versionRequested) {
                shutdownService.shutdown(FrameworkUtil.getBundle(applicationClass))
            }

            return true
        }
    }
}