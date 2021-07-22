package net.corda.applications.examples.demo

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.components.examples.runflow.RunFlow
import net.corda.flow.manager.FlowManager
import net.corda.lifecycle.LifeCycleCoordinator
import net.corda.lifecycle.LifeCycleEvent
import net.corda.lifecycle.SimpleLifeCycleCoordinator
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
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
    UNINITIALIZED, STARTINGMESSAGING, STOPPED
}

@Component
class DemoFlowRun @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = FlowManager::class)
    private val flowManager: FlowManager,
) : Application {

    private companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
        const val BATCH_SIZE: Int = 128
        const val TIMEOUT: Long = 10000L
        const val TOPIC_PREFIX = "messaging.topic.prefix"
        const val CONFIG_TOPIC_NAME = "config.topic.name"
        const val BOOTSTRAP_SERVERS = "bootstrap.servers"
        const val KAFKA_COMMON_BOOTSTRAP_SERVER = "messaging.kafka.common.bootstrap.servers"
    }

    private var lifeCycleCoordinator: LifeCycleCoordinator? = null

    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        consoleLogger.info("Starting demo flow run application...")
        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)

        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
        } else {
            var flowRunner: RunFlow? = null

            val kafkaProperties = getKafkaPropertiesFromFile(parameters.kafkaProperties)
            val bootstrapConfig = getBootstrapConfig(kafkaProperties)
            var state: LifeCycleState = LifeCycleState.UNINITIALIZED
            log.info("Creating life cycle coordinator")
            lifeCycleCoordinator =
                SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { event: LifeCycleEvent, _: LifeCycleCoordinator ->
                    log.info("While in ($state) received LifeCycleEvent: $event")
                    when (event) {
                        is StartEvent -> {
                            flowRunner = RunFlow(flowManager, subscriptionFactory, bootstrapConfig)
                            state = LifeCycleState.STARTINGMESSAGING
                        }
                        is StopEvent -> {
                            flowRunner?.stop()
                            state = LifeCycleState.STOPPED
                        }
                        else -> {
                            log.error("$event unexpected!")
                        }
                    }
                }
            log.info("Starting life cycle coordinator")
            lifeCycleCoordinator!!.start()
            consoleLogger.info("Demo flow runner started")
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
    @CommandLine.Option(names = ["--kafka"], description = ["File containing Kafka connection properties"])
    var kafkaProperties: File? = null

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}
