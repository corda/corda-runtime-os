package net.corda.applications.flowworker

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.component.sandbox.SandboxServiceCoordinator
import net.corda.configuration.read.ConfigurationReadService
import net.corda.flow.worker.FlowService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.Bundle
import org.osgi.framework.FrameworkUtil
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.nio.file.Paths
import java.util.*


val PLATFORM_PUBLIC_BUNDLE_NAMES = listOf(
    "javax.persistence-api",
    "jcl.over.slf4j",
    "net.corda.application",
    "net.corda.base",
    "net.corda.crypto-api",
    "net.corda.flows",
    "net.corda.kotlin-stdlib-jdk7.osgi-bundle",
    "net.corda.kotlin-stdlib-jdk8.osgi-bundle",
    "net.corda.ledger",
    "net.corda.legacy-api",
    "net.corda.persistence",
    "net.corda.serialization",
    "org.apache.aries.spifly.dynamic.bundle",
    "org.apache.felix.framework",
    "org.apache.felix.scr",
    "org.hibernate.orm.core",
    "org.jetbrains.kotlin.osgi-bundle",
    "slf4j.api"
)

internal const val BASE_DIRECTORY_KEY = "baseDirectory"
internal const val BLACKLISTED_KEYS_KEY = "blacklistedKeys"
internal const val PLATFORM_VERSION_KEY = "platformVersion"
internal const val PLATFORM_SANDBOX_PUBLIC_BUNDLES_KEY = "platformSandboxPublicBundles"
internal const val PLATFORM_SANDBOX_PRIVATE_BUNDLES_KEY = "platformSandboxPrivateBundles"

@Component
class FlowWorkerApp @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = FlowService::class)
    private val flowService: FlowService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SandboxServiceCoordinator::class)
    private val sandboxServiceCoordinator: SandboxServiceCoordinator,
    @Reference(service = ConfigurationAdmin::class)
    private val configurationAdmin: ConfigurationAdmin,
) : Application {

    private companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
        const val TOPIC_PREFIX = "messaging.topic.prefix"
        const val CONFIG_TOPIC_NAME = "config.topic.name"
        const val BOOTSTRAP_SERVERS = "bootstrap.servers"
        private const val INSTANCE_ID_KEY = "instance-id"
        const val KAFKA_COMMON_BOOTSTRAP_SERVER = "messaging.kafka.common.bootstrap.servers"
    }

    private var lifeCycleCoordinator: LifecycleCoordinator? = null

    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        consoleLogger.info("Starting demo application...")
        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)

        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
        } else {
            updateConfigurationService()

            val bootstrapConfig = getBootstrapConfig(getKafkaPropertiesFromFile(parameters.kafkaProperties))
                .withValue(INSTANCE_ID_KEY, ConfigValueFactory.fromAnyRef(parameters.instanceId.toInt()))

            log.info("Creating life cycle coordinator")
            lifeCycleCoordinator = coordinatorFactory.createCoordinator<FlowWorkerApp>(
            ) { event: LifecycleEvent, _: LifecycleCoordinator ->
                log.info("FlowWorkerApp received: $event")
                consoleLogger.info("FlowWorkerApp received: $event")

                when (event) {
                    is StartEvent -> {
                        configurationReadService.start()
                        configurationReadService.bootstrapConfig(bootstrapConfig)
                        flowService.start()
                        sandboxServiceCoordinator.start()
                    }
                    is StopEvent -> {
                        configurationReadService.stop()
                        flowService.stop()
                        sandboxServiceCoordinator.stop()
                    }
                    else -> {
                        log.error("$event unexpected!")
                    }
                }
            }

            log.info("Starting life cycle coordinator for FlowWorker")
            lifeCycleCoordinator!!.start()
            consoleLogger.info("Flow Worker application started")
        }
    }


    private fun updateConfigurationService() {
        val privateBundleNames = FrameworkUtil.getBundle(this::class.java).bundleContext.bundles.filter { bundle ->
            bundle.symbolicName !in PLATFORM_PUBLIC_BUNDLE_NAMES
        }.map(Bundle::getSymbolicName)

        configurationAdmin.getConfiguration(ConfigurationAdmin::class.java.name)?.also { config ->
            val properties = Properties()
            properties[BASE_DIRECTORY_KEY] = Paths.get(".").toAbsolutePath().toString()
            properties[BLACKLISTED_KEYS_KEY] = emptyList<String>()
            properties[PLATFORM_VERSION_KEY] = 999
            properties[PLATFORM_SANDBOX_PUBLIC_BUNDLES_KEY] = PLATFORM_PUBLIC_BUNDLE_NAMES
            properties[PLATFORM_SANDBOX_PRIVATE_BUNDLES_KEY] = privateBundleNames
            @Suppress("unchecked_cast")
            config.update(properties as Dictionary<String, Any>)
        }
    }

    override fun shutdown() {
        consoleLogger.info("Stopping application")
        lifeCycleCoordinator?.stop()
        log.info("Stopping application")
    }

    private fun getKafkaPropertiesFromFile(kafkaPropertiesFile: File?): Properties? {
        if (kafkaPropertiesFile == null) {
            return null
        }

        val kafkaConnectionProperties = Properties()
        kafkaConnectionProperties.load(FileInputStream(kafkaPropertiesFile))
        return kafkaConnectionProperties
    }

    private fun getBootstrapConfig(kafkaConnectionProperties: Properties?): Config {
        return ConfigFactory.empty()
            .withValue(
                KAFKA_COMMON_BOOTSTRAP_SERVER,
                ConfigValueFactory.fromAnyRef(getConfigValue(kafkaConnectionProperties, BOOTSTRAP_SERVERS, "localhost:9092"))
            )
            .withValue(CONFIG_TOPIC_NAME, ConfigValueFactory.fromAnyRef(getConfigValue(kafkaConnectionProperties, CONFIG_TOPIC_NAME, "ConfigTopic")))
            .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(getConfigValue(kafkaConnectionProperties, TOPIC_PREFIX, "demo")))
    }

    private fun getConfigValue(kafkaConnectionProperties: Properties?, path: String, default: String? = null): String {
        var configValue = System.getProperty(path)
        if (configValue == null && kafkaConnectionProperties != null) {
            configValue = kafkaConnectionProperties[path].toString()
        }

        if (configValue == null) {
            if (default != null) {
                return default
            }
            log.error(
                "No $path property found! " +
                        "Pass property in via --kafka properties file or via -D$path"
            )
            shutdown()
        }
        return configValue
    }
}

class CliParameters {
    @CommandLine.Option(names = ["--instanceId"], description = ["InstanceId for this worker"])
    lateinit var instanceId: String

    @CommandLine.Option(names = ["--kafka"], description = ["File containing Kafka connection properties"])
    var kafkaProperties: File? = null

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}