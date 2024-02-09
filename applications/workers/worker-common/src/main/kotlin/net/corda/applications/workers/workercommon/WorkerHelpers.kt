package net.corda.applications.workers.workercommon

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.secret.SecretsServiceFactoryResolver
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.osgi.api.Shutdown
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.BootConfig.BOOT_WORKER_SERVICE
import net.corda.schema.configuration.ConfigDefaults
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import org.osgi.framework.FrameworkUtil
import org.slf4j.Logger
import picocli.CommandLine
import java.io.InputStream
import java.lang.management.ManagementFactory
import kotlin.math.absoluteValue
import kotlin.random.Random

enum class BusType {
    KAFKA,
    DATABASE
}

/** Helpers used across multiple workers. */
class WorkerHelpers {
    companion object {
        private const val BOOT_CONFIG_PATH = "net/corda/applications/workers/workercommon/boot/corda.boot.json"
        private val SENSITIVE_ARGS = setOf(
            "-ddatabase.pass",
            "database.pass",
            "--stateManager.database.pass",
            "-spassphrase",
            "-msasl.jaas.config"
        )

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
         * Creates a Typesafe Config object from a map of parameters by adding a top-level key to each parameter's key.
         *
         * This function takes a map of parameters and a `topLevelKey` as input and generates a Typesafe Config object.
         * Each key in the input map is modified by prepending the `topLevelKey` followed by a dot ('.') separator.
         *
         * For example, given `topLevelKey` = "config" and the input map:
         * ```
         * {
         *     "key1" to "value1",
         *     "key2" to "value2"
         * }
         * ```
         * The resulting Typesafe Config object will have keys like:
         * ```
         * config.key1 = "value1"
         * config.key2 = "value2"
         * ```
         *
         * @param topLevelKey The top-level key to be added to each parameter's key.
         * @param params The input map of parameters to be included in the resulting Config.
         * @return A Typesafe Config object created from the modified parameter keys.
         */
        fun createConfigFromParams(topLevelKey: String, params: Map<String, String>): Config {
            return ConfigFactory.parseMap(
                params.mapKeys { (originalKey, _) -> "$topLevelKey.$originalKey" }
            )
        }

        /**
         * Merges a list of Config objects over a base Config, with configurations from the list taking precedence.
         *
         * @param baseConfig The base Config which will be used as fallback when merged with the reciever config list.
         * @return A new Config object containing the merged configuration.
         */
        fun List<Config>.mergeOver(baseConfig: Config): Config {
            val accumulator = ConfigFactory.empty()
            return this.fold(accumulator) { mergedConfig, config ->
                mergedConfig.withFallback(config)
            }.withFallback(baseConfig)
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
            extraConfigs: List<Config> = emptyList(),
        ): SmartConfig {
            val defaultParamsAndValues = listOf<Triple<String, Any?, Any>>(
                Triple(ConfigKeys.WORKSPACE_DIR, defaultParams.workspaceDir, ConfigDefaults.WORKSPACE_DIR),
                Triple(ConfigKeys.TEMP_DIR, defaultParams.tempDir, ConfigDefaults.TEMP_DIR),
                Triple(BootConfig.INSTANCE_ID, defaultParams.instanceId, Random.nextInt().absoluteValue),
                Triple(BootConfig.TOPIC_PREFIX, defaultParams.topicPrefix, ""),
                Triple(
                    BootConfig.WORKER_MEDIATOR_REPLICAS_FLOW_SESSION,
                    defaultParams.mediatorReplicasFlowSession,
                    FLOW_WORKER_MEDIATOR_REPLICAS_DEFAULT
                ),
                Triple(
                    BootConfig.WORKER_MEDIATOR_REPLICAS_FLOW_MAPPER_SESSION_IN,
                    defaultParams.mediatorReplicasFlowMapperSessionIn,
                    FLOW_WORKER_MEDIATOR_REPLICAS_DEFAULT
                ),
                Triple(
                    BootConfig.WORKER_MEDIATOR_REPLICAS_FLOW_MAPPER_SESSION_OUT,
                    defaultParams.mediatorReplicasFlowMapperSessionOut,
                    FLOW_WORKER_MEDIATOR_REPLICAS_DEFAULT
                ),
                Triple(MAX_ALLOWED_MSG_SIZE, defaultParams.maxAllowedMessageSize, 972800),
            )
            val defaultParamsMap = defaultParamsAndValues
                .mapNotNull { t -> t.second?.let { t.first to t.second } }
                .toMap()

            val defaultParamsDefaultValuesMap = defaultParamsAndValues.associate { it.first to it.third }

            //if we've requested a db message bus use that. default use kafka when not set
            val defaultMessagingParams = defaultParams.messaging
            val messagingParams = if (defaultMessagingParams[BUS_TYPE] == BusType.DATABASE.name) {
                defaultMessagingParams.mapKeys { (key, _) -> "${BootConfig.BOOT_DB}.${key.trim()}" }
            } else {
                defaultMessagingParams.mapKeys { (key, _) -> "${BootConfig.BOOT_KAFKA_COMMON}.${key.trim()}" }
            }

            val secretsConfig =
                defaultParams.secrets.mapKeys { (key, _) -> "${BootConfig.BOOT_SECRETS}.${key.trim()}" }

            val builtConfig = ConfigFactory.parseMap(messagingParams + defaultParamsMap + secretsConfig)

            val configWithExtras = extraConfigs.mergeOver(builtConfig)

            // merge with all files
            val configWithFiles = defaultParams.configFiles.reversed().fold(configWithExtras) { acc, next ->
                val fileConfig = ConfigFactory.parseFile(next.toFile())
                acc.withFallback(fileConfig)
            }.withFallback(ConfigFactory.parseMap(defaultParamsDefaultValuesMap))

            val smartConfigFactory = SmartConfigFactory
                .createWith(
                    configWithFiles.getConfig(BootConfig.BOOT_SECRETS).atPath(BootConfig.BOOT_SECRETS),
                    secretsServiceFactoryResolver.findAll()
                )

            val bootConfig = smartConfigFactory.create(configWithFiles.withoutPath(BootConfig.BOOT_SECRETS))
            validator.validate(ConfigKeys.BOOT_CONFIG, bootConfig, loadResource(BOOT_CONFIG_PATH), true)

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
                arguments.get().map { arg ->
                    SENSITIVE_ARGS.firstOrNull { arg.trim().startsWith(it) }
                        .let { prefix -> if (prefix == null) arg else "$prefix=[REDACTED]" }
                }.forEachIndexed { i, redactedArg -> info("argument $i, $redactedArg") }
            } else {
                info("arguments: Null")
            }

            info("User: ${processInfo.user().orElse("Null")}")
            info(
                "StartInstant: ${
                    if (processInfo.startInstant().isPresent) processInfo.startInstant().get() else "Null"
                }"
            )
            info(
                "TotalCpuDuration: ${
                    if (processInfo.totalCpuDuration().isPresent) processInfo.totalCpuDuration().get() else "Null"
                }"
            )

            val mxBeanInfo = ManagementFactory.getRuntimeMXBean()
            info("classpath: ${mxBeanInfo.classPath}")
            info("VM ${mxBeanInfo.vmName} ${mxBeanInfo.vmVendor} ${mxBeanInfo.vmVersion}")
        }
    }
}
