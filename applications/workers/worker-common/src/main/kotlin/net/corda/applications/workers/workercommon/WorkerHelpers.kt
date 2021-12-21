package net.corda.applications.workers.workercommon

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.applications.workers.workercommon.internal.CUSTOM_CONFIG_PATH
import net.corda.applications.workers.workercommon.internal.INSTANCE_ID_PATH
import net.corda.applications.workers.workercommon.internal.MESSAGING_CONFIG_PATH
import net.corda.applications.workers.workercommon.internal.TOPIC_PREFIX_PATH
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.osgi.api.Shutdown
import org.osgi.framework.FrameworkUtil
import picocli.CommandLine

/** Helpers used across multiple workers. */
class WorkerHelpers {
    companion object {
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

        /**
         * Uses [smartConfigFactory] to create a `SmartConfig` containing the instance ID, topic prefix and default
         * parameters.
         */
        fun getBootstrapConfig(params: DefaultWorkerParams, smartConfigFactory: SmartConfigFactory): SmartConfig {
            val messagingParamsMap = params.messagingParams.mapKeys { (k, _) -> "$MESSAGING_CONFIG_PATH.$k" }
            val additionalParamsMap = params.additionalParams.mapKeys { (k, _) -> "$CUSTOM_CONFIG_PATH.$k" }

            return smartConfigFactory.create(
                ConfigFactory
                    .parseMap(messagingParamsMap + additionalParamsMap)
                    .withValue(INSTANCE_ID_PATH, ConfigValueFactory.fromAnyRef(params.instanceId))
                    .withValue(TOPIC_PREFIX_PATH, ConfigValueFactory.fromAnyRef(params.topicPrefix))
            )
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