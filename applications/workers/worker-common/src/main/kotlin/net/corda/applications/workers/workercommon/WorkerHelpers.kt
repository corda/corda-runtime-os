package net.corda.applications.workers.workercommon

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.applications.workers.workercommon.internal.CUSTOM_CONFIG_PATH
import net.corda.applications.workers.workercommon.internal.MSG_CONFIG_PATH
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.schema.messaging.INSTANCE_ID
import net.corda.libs.configuration.schema.messaging.TOPIC_PREFIX
import net.corda.osgi.api.Shutdown
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.framework.FrameworkUtil
import picocli.CommandLine

/** Associates a configuration key/value map with the path at which the configuration should be stored. */
data class PathAndConfig(val path: String, val config: Map<String, String>)

/** Helpers used across multiple workers. */
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
                @Suppress("SpreadOperator")
                commandLine.parseArgs(*args)
            } catch (e: CommandLine.ParameterException) {
                throw IllegalArgumentException(e.message)
            }
            return params
        }

        /**
         * Uses [smartConfigFactory] to create a `SmartConfig` containing the instance ID, topic prefix, additional
         * params in the [defaultParams], and any [extraParams].
         */
        fun getBootstrapConfig(
            defaultParams: DefaultWorkerParams,
            extraParams: List<PathAndConfig> = emptyList()
        ): SmartConfig {
            val messagingParamsMap = defaultParams.messagingParams.mapKeys { (key, _) -> "$MSG_CONFIG_PATH.${key.trim()}" }
            val additionalParamsMap = defaultParams.additionalParams.mapKeys { (key, _) -> "$CUSTOM_CONFIG_PATH.${key.trim()}" }
            val extraParamsMap = extraParams
                .map { (path, params) -> params.mapKeys { (key, _) -> "$path.$key" } }
                .flatMap { map -> map.entries }
                .associate { (key, value) -> key to value }

            val config = ConfigFactory
                .parseMap(messagingParamsMap + additionalParamsMap + extraParamsMap)
                .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(defaultParams.instanceId))
                .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(defaultParams.topicPrefix))

            val secretsConfig = ConfigFactory.parseMap(defaultParams.secretsParams.mapKeys {
                    (key, _) -> "${ConfigKeys.SECRETS_CONFIG}.${key.trim()}" })

            val bootConfig = SmartConfigFactory.create(secretsConfig).create(config)
            logger.debug { "Worker boot config\n: ${bootConfig.root().render()}" }

            return bootConfig
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