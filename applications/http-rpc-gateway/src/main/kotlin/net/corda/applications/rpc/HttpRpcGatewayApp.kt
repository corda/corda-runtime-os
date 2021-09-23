package net.corda.applications.rpc

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.components.rpc.ConfigReceivedEvent
import net.corda.components.rpc.HttpRpcGateway
import net.corda.components.rpc.MessagingConfigUpdateEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.io.FileInputStream
import java.util.*

enum class LifeCycleState {
    UNINITIALIZED, STARTING, STARTINGMESSAGING, REINITMESSAGING
}

@Component(service = [Application::class], immediate = true)
class HttpRpcGatewayApp @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : Application {

    private companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
        const val TOPIC_PREFIX = "messaging.topic.prefix"
        const val CONFIG_TOPIC_NAME = "config.topic.name"
        const val BOOTSTRAP_SERVERS = "bootstrap.servers"
        const val KAFKA_COMMON_BOOTSTRAP_SERVER = "messaging.kafka.common.bootstrap.servers"
    }

    private var lifeCycleCoordinator: LifecycleCoordinator? = null

    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        consoleLogger.info("Starting HTTP RPC Gateway application...")
        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)

        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
        } else {
//            val instanceId = parameters.instanceId.toInt()
            val kafkaProperties = getKafkaPropertiesFromFile(parameters.kafkaProperties)
            val bootstrapConfig = getBootstrapConfig(kafkaProperties)
            var state: LifeCycleState = LifeCycleState.UNINITIALIZED
            var httpRpcGateway: HttpRpcGateway? = null
            log.info("Creating life cycle coordinator")
            lifeCycleCoordinator =
                coordinatorFactory.createCoordinator<HttpRpcGatewayApp>(
                ) { event: LifecycleEvent, _: LifecycleCoordinator ->
                    log.info("LifecycleEvent received: $event")
                    when (event) {
                        is StartEvent -> {
                            consoleLogger.info("Starting HTTP RPC Gateway")
                            state = LifeCycleState.STARTING
                            httpRpcGateway?.start(bootstrapConfig)
                        }
                        is ConfigReceivedEvent -> {
                            if (state == LifeCycleState.STARTING) {
                                state = LifeCycleState.STARTINGMESSAGING
                                //val config = bootstrapConfig.withFallback(event.currentConfigurationSnapshot[MESSAGING_CONFIG]!!)
                                consoleLogger.info("Received config from Kafka, started subscriptions")
                            }
                        }
                        is MessagingConfigUpdateEvent -> {
                            state = LifeCycleState.REINITMESSAGING
                            //val config = bootstrapConfig.withFallback(event.currentConfigurationSnapshot[MESSAGING_CONFIG]!!)
                            consoleLogger.info("Received config update from kafka, restarted subscriptions")
                        }
                        is StopEvent -> {
                            consoleLogger.info("Stopping HTTP RPC Gateway")
                            httpRpcGateway?.stop()
                        }
                        else -> {
                            log.error("$event unexpected!")
                        }
                    }
                }
            httpRpcGateway = HttpRpcGateway(lifeCycleCoordinator!!, configurationReadService)

            log.info("Starting life cycle coordinator")
            lifeCycleCoordinator!!.start()
            consoleLogger.info("HTTP RPC Gateway application started")
        }
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
        val bootstrapServer = getConfigValue(kafkaConnectionProperties, BOOTSTRAP_SERVERS)
        return ConfigFactory.empty()
            .withValue(KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(bootstrapServer))
            .withValue(CONFIG_TOPIC_NAME, ConfigValueFactory.fromAnyRef(getConfigValue(kafkaConnectionProperties, CONFIG_TOPIC_NAME)))
            .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(getConfigValue(kafkaConnectionProperties, TOPIC_PREFIX, "")))
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

    override fun shutdown() {
        consoleLogger.info("Stopping application")
        lifeCycleCoordinator?.stop()
        log.info("Stopping application")
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
