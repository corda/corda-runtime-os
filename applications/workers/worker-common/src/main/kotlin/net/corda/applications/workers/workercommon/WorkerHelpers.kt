package net.corda.applications.workers.workercommon

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import java.io.InputStream
import java.lang.management.ManagementFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.secret.SecretsServiceFactoryResolver
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.osgi.api.Shutdown
import net.corda.schema.configuration.BootConfig.BOOT_DB
import net.corda.schema.configuration.BootConfig.BOOT_KAFKA_COMMON
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import net.corda.v5.base.util.debug
import org.osgi.framework.FrameworkUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
         * Return a SmartConfig object for the top level of the bootstrap configuration.
         *
         * Uses [smartConfigFactory] to create a `SmartConfig` containing the instance ID, topic prefix, additional
         * params in the [defaultParams], and any [extraParams]. Check that the configuration matches the schema
         * defined in
         * https://github.com/corda/corda-api/tree/release/os/5.0/data/config-schema/src/main/resources/net/corda/schema/configuration
         * but do not fill in defaults.
         *
         * This is typically called during the startup of a worker. The config object is then passed in to the
         * [start] method of a Corda component, e.g. the processor object that goes with the worker component.
         * For the most part, processors will then post a [BootConfigEvent] to their lifecycle coordinator with
         * this boot configuration as a field in the event, which will eventually cause the parameters
         * passed in here to be end up contributing towards a merged configuration.
         *
         * This is also passed into [ConfigurationReadService.bootstrapConfig].
         */
        fun getBootstrapConfig(
            secretsServiceFactoryResolver: SecretsServiceFactoryResolver,
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
                .withValue(MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(defaultParams.maxAllowedMessageSize))

            val secretsConfig =
                ConfigFactory.parseMap(defaultParams.secretsParams.mapKeys { (key, _) -> "${ConfigKeys.SECRETS_CONFIG}.${key.trim()}" })

            val smartConfigFactory = SmartConfigFactory
                .createWith(secretsConfig, secretsServiceFactoryResolver.findAll())
            val bootConfig = smartConfigFactory.create(config)
            logger.debug { "Worker boot config\n: ${bootConfig.root().render()}" }

            validator.validate(BOOT_CONFIG, bootConfig, loadResource(BOOT_CONFIG_PATH), true)

            // we now know bootConfig has:
            //
            // 1. parameters passed directly in on the command line
            // 2. has defaults from DefaultWorkerParams for the command line options which were not specified.
            // 2. INSTANCE_ID and TOPIC_PREFIX set indirectly via the command line
            //
            // Also, boot config has been validated; all the keys in it are declared in the JSON schema and are of
            // the types specified in the schema
            //
            // However, bootConfig does not:
            //  - have database configuration records applied
            //  - have unspecified fields filled in with defaults from the schema. (This part is handled later)

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

        /** Sets up the [workerMonitor] based on the [params]. */
        fun setupMonitor(workerMonitor: WorkerMonitor, params: DefaultWorkerParams, workerType: String) {
            if (!params.disableWorkerMonitor) {
                workerMonitor.listen(params.workerMonitorPort, workerType)
            }
        }

        fun startBanner() {

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

        /**
         * Logs info about Worker startup process, including info from current process and [PlatformInfoProvider].
         */
        fun Logger.loggerStartupInfo(platformInfoProvider: PlatformInfoProvider) {
            info("LocalWorkerPlatformVersion ${platformInfoProvider.localWorkerPlatformVersion}")
            info("LocalWorkerSoftwareVersion ${platformInfoProvider.localWorkerSoftwareVersion}")

            val processHandle = ProcessHandle.current()
            val processInfo = processHandle.info()
            info("PID: ${processHandle.pid()}")
            info("Command: ${processInfo.command().orElse("Null")}")

            val arguments = processInfo.arguments()
            if (arguments.isPresent) {
                arguments.get().forEachIndexed { i, arg ->
                    if ("-ddatabase.pass" !in arg && "-spassphrase" !in arg) {
                        info("argument $i, $arg")
                    }
                }
            } else {
                info("arguments: Null")
            }

            info("User: ${processInfo.user().orElse("Null")}")
            info("StartInstant: ${if (processInfo.startInstant().isPresent) processInfo.startInstant().get() else "Null"}")
            info("TotalCpuDuration: ${if (processInfo.totalCpuDuration().isPresent) processInfo.totalCpuDuration().get() else "Null"}")

            val mxBeanInfo = ManagementFactory.getRuntimeMXBean()
            info("classpath: ${mxBeanInfo.classPath}")
            info("VM ${mxBeanInfo.vmName} ${mxBeanInfo.vmVendor} ${mxBeanInfo.vmVersion}")
        }
    }
}