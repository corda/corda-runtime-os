package net.corda.applications.examples.demo

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.components.examples.config.reader.ConfigReader
import net.corda.components.examples.config.reader.ConfigReader.Companion.MESSAGING_CONFIG
import net.corda.components.examples.config.reader.ConfigReceivedEvent
import net.corda.components.examples.config.reader.MessagingConfigUpdateEvent
import net.corda.components.examples.durable.RunDurableSub
import net.corda.components.examples.pubsub.RunPubSub
import net.corda.components.examples.stateevent.RunStateEventSub
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.read.factory.ConfigReaderFactory
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
    UNINITIALIZED, STARTINGCONFIG, STARTINGMESSAGING, REINITMESSAGING
}

@Component
class DemoApp @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = ConfigReaderFactory::class)
    private var configReaderFactory: ConfigReaderFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory,
) : Application {

    private companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
        const val TOPIC_PREFIX = "messaging.topic.prefix"
        const val BOOTSTRAP_SERVERS = "bootstrap.servers"
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
            var durableSub: RunDurableSub? = null
            var pubsubSub: RunPubSub? = null
            var stateEventSub: RunStateEventSub? = null
            var configReader: ConfigReader? = null

            val instanceId = parameters.instanceId.toInt()
            val kafkaProperties = getKafkaPropertiesFromFile(parameters.kafkaProperties)
            val bootstrapConfig = getBootstrapConfig(kafkaProperties)
            var state: LifeCycleState = LifeCycleState.UNINITIALIZED
            log.info("Creating life cycle coordinator")
            lifeCycleCoordinator =
                coordinatorFactory.createCoordinator<DemoApp>(
                ) { event: LifecycleEvent, _: LifecycleCoordinator ->
                    log.info("LifecycleEvent received: $event")
                    when (event) {
                        is StartEvent -> {
                            consoleLogger.info("Starting kafka config reader")
                            state = LifeCycleState.STARTINGCONFIG
                            configReader?.start(bootstrapConfig)
                        }
                        is ConfigReceivedEvent -> {
                            if (state == LifeCycleState.STARTINGCONFIG) {
                                state = LifeCycleState.STARTINGMESSAGING
                                val config = bootstrapConfig.withFallback(event.currentConfigurationSnapshot[MESSAGING_CONFIG]!!)
                                durableSub =
                                    RunDurableSub(
                                        subscriptionFactory,
                                        config,
                                        instanceId,
                                        parameters.durableKillProcessOnRecord.toInt(),
                                        parameters.durableProcessorDelay.toLong()
                                    )
                                stateEventSub = RunStateEventSub(
                                    instanceId,
                                    config,
                                    subscriptionFactory,
                                    parameters.stateEventKillProcessOnRecord.toInt(),
                                    parameters.stateEventProcessorDelay.toLong()
                                )
                                pubsubSub = RunPubSub(subscriptionFactory, config)

                                durableSub?.start()
                                stateEventSub?.start()
                                pubsubSub?.start()
                                consoleLogger.info("Received config from kafka, started subscriptions")
                            }
                        }
                        is MessagingConfigUpdateEvent -> {
                            state = LifeCycleState.REINITMESSAGING
                            val config = bootstrapConfig.withFallback(event.currentConfigurationSnapshot[MESSAGING_CONFIG]!!)
                            stateEventSub?.reStart(config)
                            pubsubSub?.reStart(config)
                            durableSub?.reStart(config)
                            consoleLogger.info("Received config update from kafka, restarted subscriptions")
                        }
                        is StopEvent -> {
                            configReader?.stop()
                            durableSub?.stop()
                            stateEventSub?.stop()
                            pubsubSub?.stop()
                        }
                        else -> {
                            log.error("$event unexpected!")
                        }
                    }
                }
            configReader = ConfigReader(lifeCycleCoordinator!!, configReaderFactory)

            log.info("Starting life cycle coordinator")
            lifeCycleCoordinator!!.start()
            consoleLogger.info("Demo application started")
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

    private fun getBootstrapConfig(kafkaConnectionProperties: Properties?): SmartConfig {
        val bootstrapServer = getConfigValue(kafkaConnectionProperties, BOOTSTRAP_SERVERS)

        // TODO - inject the secrets provider
        return smartConfigFactory.create(ConfigFactory.empty()
            .withValue(KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(bootstrapServer))
            .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(getConfigValue(kafkaConnectionProperties, TOPIC_PREFIX, ""))))
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

    @CommandLine.Option(
        names = ["--durableKillProcessOnRecord"],
        description = ["Exit process via durable processor on this record count"]
    )
    var durableKillProcessOnRecord: String = "0"

    @CommandLine.Option(
        names = ["--durableProcessorDelay"],
        description = ["Milli seconds delay between processing each record returned from kafka in the durable processor"]
    )
    var durableProcessorDelay: String = "0"

    @CommandLine.Option(
        names = ["--stateEventProcessorDelay"],
        description = ["Milli seconds delay between processing each record returned from kafka in the state and event processor"]
    )
    var stateEventProcessorDelay: String = "0"

    @CommandLine.Option(
        names = ["--stateEventKillProcessOnRecord"],
        description = ["Exit process via state and event processor on this record count"]
    )
    var stateEventKillProcessOnRecord: String = "0"

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}
