package net.corda.applications.workers.workercommon

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import java.io.InputStream
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.osgi.api.Shutdown
import net.corda.schema.configuration.BootConfig.BOOT_DB
import net.corda.schema.configuration.BootConfig.BOOT_KAFKA_COMMON
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.framework.FrameworkUtil
import picocli.CommandLine

/** Associates a configuration key/value map with the path at which the configuration should be stored. */
data class PathAndConfig(val path: String, val config: Map<String, String>)

enum class BusType {
    KAFKA,
    DB
}

/** Helpers used across multiple workers. */
class WorkerHelpers {
    companion object {
        private val logger = contextLogger()
        private const val BOOT_CONFIG_PATH = "net/corda/applications/workers/workercommon/boot/corda.boot.json"

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
            validator: ConfigurationValidator,
            extraParams: List<PathAndConfig> = emptyList(),
            busType: BusType = BusType.KAFKA
        ): SmartConfig {
            val extraParamsMap = extraParams
                .map { (path, params) -> params.mapKeys { (key, _) -> "$path.$key" } }
                .flatMap { map -> map.entries }
                .associate { (key, value) -> key to value }

            val dirsConfig = mapOf(
                ConfigKeys.WORKSPACE_DIR to defaultParams.workspaceDir,
                ConfigKeys.TEMP_DIR to defaultParams.tempDir
            )

            val messagingParams = if (busType == BusType.KAFKA) {
                defaultParams.messagingParams.mapKeys { (key, _) -> "$BOOT_KAFKA_COMMON.${key.trim()}" }
            } else {
                defaultParams.messagingParams.mapKeys { (key, _) -> "$BOOT_DB.${key.trim()}" }
            }

            val config = ConfigFactory
                .parseMap(messagingParams + dirsConfig + extraParamsMap)
                .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(defaultParams.instanceId))
                .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(defaultParams.topicPrefix))

            val secretsConfig =
                ConfigFactory.parseMap(defaultParams.secretsParams.mapKeys { (key, _) -> "${ConfigKeys.SECRETS_CONFIG}.${key.trim()}" })

            val bootConfig = SmartConfigFactory.create(secretsConfig).create(config)
            logger.debug { "Worker boot config\n: ${bootConfig.root().render()}" }

            validator.validate(BOOT_CONFIG, bootConfig, loadResource(BOOT_CONFIG_PATH))
            return bootConfig
        }

        private fun loadResource(resource: String): InputStream {
            val bundle = FrameworkUtil.getBundle(this::class.java) ?: null
            val url = bundle?.getResource(resource)
                ?: this::class.java.classLoader.getResource(resource)
                ?: throw IllegalArgumentException(
                    "Failed to find resource $resource on worker startup."
                )
            return url.openStream()
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