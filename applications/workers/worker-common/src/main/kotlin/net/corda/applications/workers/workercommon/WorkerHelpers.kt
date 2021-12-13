package net.corda.applications.workers.workercommon

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.osgi.api.Shutdown
import org.osgi.framework.FrameworkUtil
import picocli.CommandLine

/** Helpers used across multiple workers. */
class WorkerHelpers {
    companion object {
        private const val INSTANCE_ID = "instance-id"
        private const val TOPIC_MESSAGE_PREFIX_PATH = "messaging.topic.prefix"

        /**
         * Parses the [args] into the [params].
         *
         * @throws IllegalArgumentException If parsing fails.
         */
        fun <T> getParams(args: Array<String>, params: T): T {
            val commandLine = CommandLine(params)
            try {
                @Suppress("SpreadOperator")
                commandLine.parseArgs(*args)
            } catch (e: CommandLine.ParameterException) {
                throw IllegalArgumentException(e.message)
            }
            return params
        }

        /** Uses [smartConfigFactory] to create a `SmartConfig` wrapping the worker's
         * additional parameters, instanceId and topic prefix from [params]. */
        fun getBootstrapConfig(params: DefaultWorkerParams, smartConfigFactory: SmartConfigFactory) =
            smartConfigFactory.create(ConfigFactory.parseMap(params.additionalParams)
                .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(params.instanceId)))
                .withValue(TOPIC_MESSAGE_PREFIX_PATH, ConfigValueFactory.fromAnyRef(getConfigValue(params.topicPrefix, "")))

        private fun getConfigValue(topicPrefix: String, default: String): Any? {
            return if (!topicPrefix.isNullOrBlank()) {
                topicPrefix
            } else {
                default
            }
        }


        /** Sets up the [healthMonitor] based on the [params]. */
        fun setUpHealthMonitor(healthMonitor: HealthMonitor, params: DefaultWorkerParams) {
            if (!params.disableHealthMonitor) {
                healthMonitor.listen(params.healthMonitorPort)
            }
        }

        /**
         * Prints help if `params.helpRequested` is true. Else prints version if `params.versionRequested` is true.
         *
         * If help or version are printed, the application is shut down using [shutdownService].
         *
         * The version printed is the specification version and implementation of the JAR containing [appClass].
         *
         * @return `true` if help or version has been printed, `false` otherwise.
         */
        fun printHelpOrVersion(params: DefaultWorkerParams, appClass: Class<*>, shutdownService: Shutdown): Boolean {
            if (params.helpRequested) {
                CommandLine.usage(params, System.out)

            } else if (params.versionRequested) {
                val appName = appClass.simpleName
                println("$appName specification version: ${appClass.`package`.specificationVersion}")
                println("$appName implementation version: ${appClass.`package`.implementationVersion}")
            }

            if (params.helpRequested || params.versionRequested) {
                shutdownService.shutdown(FrameworkUtil.getBundle(appClass))
                return true
            }

            return false
        }
    }
}