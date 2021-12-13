package net.corda.applications.rpc

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.components.rpc.HttpRpcGateway
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.math.absoluteValue
import kotlin.random.Random
import net.corda.applications.rpc.internal.HttpRpcGatewayAppEventHandler
import net.corda.configuration.read.ConfigurationReadService

@Component(service = [Application::class], immediate = true)
@Suppress("LongParameterList")
class HttpRpcGatewayApp @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory,
    @Reference(service = HttpRpcGateway::class)
    private val httpRpcGateway: HttpRpcGateway,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
) : Application {

    private companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
        const val TOPIC_PREFIX = "messaging.topic.prefix"
        const val CONFIG_TOPIC_NAME = "config.topic.name"
        const val BOOTSTRAP_SERVERS = "bootstrap.servers"
        const val KAFKA_COMMON_BOOTSTRAP_SERVER = "messaging.kafka.common.bootstrap.servers"

        const val TEMP_DIRECTORY_PREFIX = "http-rpc-gateway-app-temp-dir"
        const val CONFIG_FILE = "local_http_rpc_gateway.conf"
        const val GENERAL_CONFIG_INSTANCE_ID_PATH = "instanceId"
    }

    private var lifeCycleCoordinator: LifecycleCoordinator? = null
    private var tempDirectoryPath: Path? = null
    private var sub: AutoCloseable? = null

    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        consoleLogger.info("Starting HTTP RPC Gateway application...")
        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)

        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
        } else {
            val kafkaProperties = getKafkaPropertiesFromFile(parameters.kafkaProperties)
            val bootstrapConfig = getBootstrapConfig(parameters.instanceId, kafkaProperties)

            log.info("Starting configuration read service with bootstrap config ${bootstrapConfig}.")
            configurationReadService.start()
            configurationReadService.bootstrapConfig(bootstrapConfig)

            log.info("Creating life cycle coordinator")
            lifeCycleCoordinator = coordinatorFactory.createCoordinator<HttpRpcGatewayApp>(
                HttpRpcGatewayAppEventHandler(httpRpcGateway)
            )

            log.info("Starting life cycle coordinator")
            lifeCycleCoordinator!!.start()
        }
    }

    override fun close() {
        lifeCycleCoordinator?.stop()
        super.close()
    }

    private fun getKafkaPropertiesFromFile(kafkaPropertiesFile: File?): Properties? {
        if (kafkaPropertiesFile == null) {
            return null
        }

        val kafkaConnectionProperties = Properties()
        kafkaConnectionProperties.load(FileInputStream(kafkaPropertiesFile))
        return kafkaConnectionProperties
    }

    /**
     * Create the config file found in resources in a temp folder and return the full path
     */
    private fun createConfigFile(): String {
        tempDirectoryPath = Files.createTempDirectory(TEMP_DIRECTORY_PREFIX)
        val bundleContext = FrameworkUtil.getBundle(HttpRpcGatewayApp::class.java).bundleContext
        val configFileURL = bundleContext.bundle.getResource(CONFIG_FILE)
        val configFileContent = configFileURL.openStream().readAllBytes()
        val configFilePath = Path.of(tempDirectoryPath.toString(), CONFIG_FILE)
        configFilePath.toFile().writeBytes(configFileContent)
        return configFilePath.toString()
    }

    private fun getBootstrapConfig(instanceId: Int, kafkaConnectionProperties: Properties?): SmartConfig {

        val bootstrapServer = getConfigValue(kafkaConnectionProperties, BOOTSTRAP_SERVERS)
        val configFile = createConfigFile()
        log.debug { "Config file saved to: $configFile" }
        return smartConfigFactory.create(ConfigFactory.empty()
            .withValue(KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(bootstrapServer))
            .withValue(
                CONFIG_TOPIC_NAME,
                ConfigValueFactory.fromAnyRef(getConfigValue(kafkaConnectionProperties, CONFIG_TOPIC_NAME))
            )
            .withValue(
                TOPIC_PREFIX,
                ConfigValueFactory.fromAnyRef(getConfigValue(kafkaConnectionProperties, TOPIC_PREFIX, ""))
            )
            .withValue(GENERAL_CONFIG_INSTANCE_ID_PATH, ConfigValueFactory.fromAnyRef(instanceId))
            .withValue("config.file", ConfigValueFactory.fromAnyRef(configFile)))
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
            log.error("No $path property found! Pass property in via --kafka properties file or via -D$path")
            shutdown()
        }
        return configValue
    }

    override fun shutdown() {
        consoleLogger.info("Stopping application")
        log.info("Stopping application")
        sub?.close()
        sub = null
        lifeCycleCoordinator?.stop()
        tempDirectoryPath?.let {
            File(it.toUri()).deleteRecursively()
        }
    }
}

class CliParameters {
    @CommandLine.Option(names = ["--instanceId"], description = ["InstanceId for this worker. Defaults to a random value."])
    var instanceId: Int = Random.nextInt().absoluteValue

    @CommandLine.Option(names = ["--kafka"], description = ["File containing Kafka connection properties"])
    var kafkaProperties: File? = null

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}
