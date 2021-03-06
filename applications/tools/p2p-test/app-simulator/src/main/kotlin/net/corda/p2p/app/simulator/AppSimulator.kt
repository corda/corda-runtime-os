package net.corda.p2p.app.simulator

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.schema.TestSchema.Companion.APP_RECEIVED_MESSAGES_TOPIC
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.MessagingConfig
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

@Component(immediate = true)
class AppSimulator @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = ConfigMerger::class)
    private val configMerger: ConfigMerger,
) : Application {

    companion object {
        private val logger: Logger = contextLogger()
        private val clock: Clock = UTCClock()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
        const val DB_PARAMS_PREFIX = "dbParams"
        const val LOAD_GEN_PARAMS_PREFIX = "loadGenerationParams"
        const val PARALLEL_CLIENTS_KEY = "parallelClients"
        val DEFAULT_CONFIG = ConfigFactory.parseMap(
            mapOf(
                "$LOAD_GEN_PARAMS_PREFIX.batchSize" to 50,
                "$LOAD_GEN_PARAMS_PREFIX.interBatchDelay" to Duration.ZERO,
                "$LOAD_GEN_PARAMS_PREFIX.messageSizeBytes" to 10_000,
                PARALLEL_CLIENTS_KEY to 1
            )
        )
    }

    private val resources = mutableListOf<AutoCloseable>()

    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        consoleLogger.info("Starting application simulator tool")

        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)
        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            shutdownOSGiFramework()
        } else {
            if (!parameters.simulatorConfig.canRead()) {
                consoleLogger.error("Can not read configuration file ${parameters.simulatorConfig}.")
                shutdownOSGiFramework()
                return
            }

            val parsedMessagingParams = parameters.messagingParams.mapKeys { (key, _) ->
                "${BootConfig.BOOT_KAFKA_COMMON}.${key.trim()}"
            }.toMutableMap()
            parsedMessagingParams.computeIfAbsent("${BootConfig.BOOT_KAFKA_COMMON}.bootstrap.servers") {
                System.getenv("KAFKA_SERVERS") ?: "localhost:9092"
            }
            val bootConfig = SmartConfigFactory.create(SmartConfigImpl.empty()).create(
                ConfigFactory.parseMap(parsedMessagingParams)
                    .withValue(
                        BootConfig.TOPIC_PREFIX,
                        ConfigValueFactory.fromAnyRef("")
                    ).withValue(
                        MessagingConfig.Bus.BUS_TYPE,
                        ConfigValueFactory.fromAnyRef("KAFKA")
                    )
            )
            try {
                runSimulator(parameters, bootConfig)
            } catch (e: Throwable) {
                consoleLogger.error("Could not run: ${e.message}")
            }
        }
    }

    private fun runSimulator(parameters: CliParameters, bootConfig: SmartConfig) {
        val sendTopic = parameters.sendTopic ?: P2P_OUT_TOPIC
        val receiveTopic = parameters.receiveTopic ?: P2P_IN_TOPIC
        val simulatorConfig = ConfigFactory.parseFile(parameters.simulatorConfig).withFallback(DEFAULT_CONFIG)
        val clients = simulatorConfig.getInt(PARALLEL_CLIENTS_KEY)

        val simulatorMode = simulatorConfig.getEnum(SimulationMode::class.java, "simulatorMode")
        when (simulatorMode) {
            SimulationMode.SENDER -> {
                runSender(simulatorConfig, publisherFactory, sendTopic, bootConfig, clients, parameters.instanceId)
            }
            SimulationMode.RECEIVER -> {
                runReceiver(subscriptionFactory, receiveTopic, bootConfig, clients, parameters.instanceId)
            }
            SimulationMode.DB_SINK -> {
                runSink(simulatorConfig, subscriptionFactory, bootConfig, clients, parameters.instanceId)
            }
            else -> throw IllegalStateException("Invalid value for simulator mode: $simulatorMode")
        }
    }

    @Suppress("LongParameterList")
    private fun runSender(
        simulatorConfig: Config,
        publisherFactory: PublisherFactory,
        sendTopic: String,
        bootConfig: SmartConfig,
        clients: Int,
        instanceId: String,
    ) {
        val connectionDetails = readDbParams(simulatorConfig)
        val loadGenerationParams = readLoadGenParams(simulatorConfig)
        val sender = Sender(
            publisherFactory,
            configMerger,
            connectionDetails,
            loadGenerationParams,
            sendTopic,
            bootConfig,
            clients,
            instanceId,
            clock
        )
        sender.start()
        resources.add(sender)
        // If it's one-off we wait until all messages have been sent.
        // Otherwise, we let the threads run until the process is stopped by the user.
        if (loadGenerationParams.loadGenerationType == LoadGenerationType.ONE_OFF) {
            sender.waitUntilComplete()
            shutdownOSGiFramework()
        }
    }

    private fun runReceiver(
        subscriptionFactory: SubscriptionFactory,
        receiveTopic: String,
        bootConfig: SmartConfig,
        clients: Int,
        instanceId: String,
    ) {
        val receiver = Receiver(
            subscriptionFactory,
            configMerger,
            receiveTopic,
            APP_RECEIVED_MESSAGES_TOPIC,
            bootConfig,
            clients,
            instanceId
        )
        receiver.start()
        resources.add(receiver)
    }

    private fun runSink(
        simulatorConfig: Config,
        subscriptionFactory: SubscriptionFactory,
        bootConfig: SmartConfig,
        clients: Int,
        instanceId: String,
    ) {
        val connectionDetails = readDbParams(simulatorConfig)
        if (connectionDetails == null) {
            consoleLogger.error("dbParams configuration option is mandatory for sink mode.")
            shutdownOSGiFramework()
            return
        }
        val sink = Sink(subscriptionFactory, configMerger, connectionDetails, bootConfig, clients, instanceId)
        sink.start()
        resources.add(sink)
    }

    private fun readDbParams(config: Config): DBParams? {
        if (!config.hasPath(DB_PARAMS_PREFIX)) {
            return null
        }

        return DBParams(
            config.getString("$DB_PARAMS_PREFIX.username"),
            config.getString("$DB_PARAMS_PREFIX.password"),
            config.getString("$DB_PARAMS_PREFIX.host"),
            config.getString("$DB_PARAMS_PREFIX.db")
        )
    }

    private fun readLoadGenParams(config: Config): LoadGenerationParams {
        val peerX500Name = config.getString("$LOAD_GEN_PARAMS_PREFIX.peerX500Name")
        val peerGroupId = config.getString("$LOAD_GEN_PARAMS_PREFIX.peerGroupId")
        val ourX500Name = config.getString("$LOAD_GEN_PARAMS_PREFIX.ourX500Name")
        val ourGroupId = config.getString("$LOAD_GEN_PARAMS_PREFIX.ourGroupId")
        val loadGenerationType =
            config.getEnum(LoadGenerationType::class.java, "$LOAD_GEN_PARAMS_PREFIX.loadGenerationType")
        val totalNumberOfMessages = when (loadGenerationType) {
            LoadGenerationType.ONE_OFF -> config.getInt("$LOAD_GEN_PARAMS_PREFIX.totalNumberOfMessages")
            LoadGenerationType.CONTINUOUS -> null
            else -> throw IllegalStateException("Invalid value for load generation type: $loadGenerationType")
        }
        val batchSize = config.getInt("$LOAD_GEN_PARAMS_PREFIX.batchSize")
        val interBatchDelay = config.getDuration("$LOAD_GEN_PARAMS_PREFIX.interBatchDelay")
        val messageSizeBytes = config.getInt("$LOAD_GEN_PARAMS_PREFIX.messageSizeBytes")
        val expireAfterTime = try {
            config.getDuration("$LOAD_GEN_PARAMS_PREFIX.expireAfterTime")
        } catch (e: ConfigException.Missing) {
            null
        }
        return LoadGenerationParams(
            HoldingIdentity(peerX500Name, peerGroupId),
            HoldingIdentity(ourX500Name, ourGroupId),
            loadGenerationType,
            totalNumberOfMessages,
            batchSize,
            interBatchDelay,
            messageSizeBytes,
            expireAfterTime
        )
    }

    override fun shutdown() {
        logger.info("Shutting down application simulator tool")
        resources.forEach { it.close() }
    }

    private fun shutdownOSGiFramework() {
        shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
    }
}

class CliParameters {
    @CommandLine.Option(
        names = ["-m", "--messagingParams"],
        description = ["Messaging parameters for the simulator."]
    )
    var messagingParams = emptyMap<String, String>()

    @CommandLine.Option(
        names = ["-i", "--instance-id"],
        description = [
            "The instance ID. Defaults to the value of the env." +
                " variable INSTANCE_ID or a random number, if that hasn't been set."
        ]
    )
    var instanceId = System.getenv("INSTANCE_ID") ?: Random.nextInt().toString()

    @CommandLine.Option(
        names = ["--simulator-config"],
        description = ["File containing configuration parameters for simulator. Default to \${DEFAULT-VALUE}"]
    )
    var simulatorConfig: File = File("config.conf")

    @CommandLine.Option(
        names = ["--send-topic"],
        description = [
            "Topic to send the messages to. " +
                "Defaults to ${P2P_OUT_TOPIC}, if not specified."
        ]
    )
    var sendTopic: String? = null

    @CommandLine.Option(
        names = ["--receive-topic"],
        description = [
            "Topic to receive messages from. " +
                "Defaults to ${P2P_IN_TOPIC}, if not specified."
        ]
    )
    var receiveTopic: String? = null

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}

enum class LoadGenerationType {
    ONE_OFF,
    CONTINUOUS
}

enum class SimulationMode {
    SENDER,
    RECEIVER,
    DB_SINK
}

data class DBParams(val username: String, val password: String, val host: String, val db: String)

data class LoadGenerationParams(
    val peer: HoldingIdentity,
    val ourIdentity: HoldingIdentity,
    val loadGenerationType: LoadGenerationType,
    val totalNumberOfMessages: Int?,
    val batchSize: Int,
    val interBatchDelay: Duration,
    val messageSizeBytes: Int,
    val expireAfterTime: Duration?
) {
    init {
        when (loadGenerationType) {
            LoadGenerationType.ONE_OFF -> require(totalNumberOfMessages != null)
            LoadGenerationType.CONTINUOUS -> require(totalNumberOfMessages == null)
        }
    }
}

data class MessagePayload(val sender: String, val payload: ByteArray, val sendTimestamp: Instant)
data class MessageSentEvent(val sender: String, val messageId: String)
data class MessageReceivedEvent(
    val sender: String,
    val messageId: String,
    val sendTimestamp: Instant,
    val receiveTimestamp: Instant,
    val deliveryLatency: Duration
)
