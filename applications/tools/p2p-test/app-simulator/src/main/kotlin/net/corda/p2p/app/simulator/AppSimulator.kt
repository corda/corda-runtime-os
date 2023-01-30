package net.corda.p2p.app.simulator

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.p2p.app.simulator.AppSimulator.Companion.DEFAULT_NUMBER_OF_PARTITIONS
import net.corda.p2p.app.simulator.AppSimulator.Companion.DEFAULT_REPLICATION_FACTOR
import net.corda.p2p.app.simulator.ArgParsingUtils.Companion.getDbParameter
import net.corda.p2p.app.simulator.ArgParsingUtils.Companion.getEnumOrNull
import net.corda.p2p.app.simulator.ArgParsingUtils.Companion.getIntOrNull
import net.corda.p2p.app.simulator.ArgParsingUtils.Companion.getLoadGenDuration
import net.corda.p2p.app.simulator.ArgParsingUtils.Companion.getLoadGenDurationOrNull
import net.corda.p2p.app.simulator.ArgParsingUtils.Companion.getLoadGenEnumParameter
import net.corda.p2p.app.simulator.ArgParsingUtils.Companion.getLoadGenIntParameter
import net.corda.p2p.app.simulator.ArgParsingUtils.Companion.getLoadGenStrParameter
import net.corda.p2p.app.simulator.ArgParsingUtils.Companion.getTopicCreationParameter
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.MessagingConfig
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.types.MemberX500Name
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
    @Reference(service = KafkaTopicAdmin::class)
    private var topicAdmin: KafkaTopicAdmin,
) : Application {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val clock: Clock = UTCClock()
        const val DB_PARAMS_PREFIX = "dbParams"
        const val TOPIC_CREATION_PREFIX = "topicCreationParams"
        const val LOAD_GEN_PARAMS_PREFIX = "loadGenerationParams"
        const val PARALLEL_CLIENTS_KEY = "parallelClients"
        const val DEFAULT_PARALLEL_CLIENTS = 1
        const val DEFAULT_TOTAL_NUMBER_OF_MESSAGES = 1
        const val DEFAULT_BATCH_SIZE = 50
        const val DEFAULT_REPLICATION_FACTOR = 1
        const val DEFAULT_NUMBER_OF_PARTITIONS = 10
        val DEFAULT_INTER_BATCH_DELAY = Duration.ZERO
        const val DEFAULT_MESSAGE_SIZE_BYTES = 10_000
        internal const val APP_SIMULATOR_SUBSYSTEM = "app-simulator"
    }

    private val resources = mutableListOf<AutoCloseable>()

    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        logger.info("Starting application simulator tool")

        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)
        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            shutdownOSGiFramework()
        } else {
            val commonConfig = CommonConfig(parameters)
            try {
                runSimulator(commonConfig)
            } catch (e: Throwable) {
                logErrorAndShutdown("Could not run: ${e.message}")
            }
        }
    }

    @Suppress("ComplexMethod")
    private fun runSimulator(commonConfig: CommonConfig) {
        when (commonConfig.simulatorMode) {
            SimulationMode.SENDER -> {
                runSender(commonConfig)
            }
            SimulationMode.RECEIVER -> {
                runReceiver(commonConfig)
            }
            SimulationMode.DB_SINK -> {
                runSink(commonConfig)
            }
            else -> throw IllegalStateException("Invalid value for simulator mode: ${commonConfig.simulatorMode}")
        }
    }

    private fun runSender(commonConfig: CommonConfig) {
        val connectionDetails = DBParams.read(commonConfig)
        val loadGenerationParams = LoadGenerationParams.read(commonConfig)
        val sender = Sender(
            publisherFactory,
            configMerger,
            commonConfig,
            connectionDetails,
            loadGenerationParams,
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

    private fun runReceiver(commonConfig: CommonConfig) {
        val topicCreationParams = TopicCreationParams.read(commonConfig)
        val receiver = Receiver(
            subscriptionFactory,
            configMerger,
            topicAdmin,
            commonConfig,
            topicCreationParams
        )
        receiver.start()
        resources.add(receiver)
    }

    private fun runSink(commonConfig: CommonConfig) {
        val connectionDetails = DBParams.read(commonConfig)
        val sink = Sink(subscriptionFactory, configMerger, commonConfig, connectionDetails)
        sink.start()
        resources.add(sink)
    }

    private fun logErrorAndShutdown(error: String) {
        logger.error(error)
        shutdownOSGiFramework()
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
        names = ["-m", "--messaging-params"],
        description = ["Messaging parameters for the simulator."]
    )
    var messagingParams = emptyMap<String, String>()

    @CommandLine.Option(
        names = ["-d", "--database-params"],
        description = ["Database parameters for the simulator."]
    )
    var databaseParams = emptyMap<String, String>()

    @CommandLine.Option(
        names = ["-l", "--load-generation-params"],
        description = ["Load generation parameters for the simulator."]
    )
    var loadGenerationParams = emptyMap<String, String>()

    @CommandLine.Option(
        names = ["-t", "--topic-creation-params"],
        description = ["Topic creation parameters for the simulator."]
    )
    var topicCreationParams = emptyMap<String, String>()

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
    var simulatorConfig: File? = null

    @CommandLine.Option(
        names = ["--clients"],
        description = [" Default to \${DEFAULT-VALUE}."]
    )
    var clients: Int? = null

    @CommandLine.Option(
        names = ["--mode"],
        description = [" Default to \${DEFAULT-VALUE}."]
    )
    val simulationMode: SimulationMode? = null

    @CommandLine.Option(
        names = ["--send-topic"],
        description = [
            "Topic to send the messages to. " +
                "Defaults to \${DEFAULT-VALUE}, if not specified."
        ]
    )
    var sendTopic: String = P2P_OUT_TOPIC

    @CommandLine.Option(
        names = ["--receive-topic"],
        description = [
            "Topic to receive messages from. " +
                "Defaults to  \${DEFAULT-VALUE}, if not specified."
        ]
    )
    var receiveTopic: String = P2P_IN_TOPIC

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

class CommonConfig(val parameters: CliParameters) {
    val bootConfig: SmartConfig
    val configFromFile: Config
    val clients: Int
    val simulatorMode: SimulationMode?

    init {
        val parsedMessagingParams = parameters.messagingParams.mapKeys { (key, _) ->
            "${BootConfig.BOOT_KAFKA_COMMON}.${key.trim()}"
        }.toMutableMap()
        parsedMessagingParams.computeIfAbsent("${BootConfig.BOOT_KAFKA_COMMON}.bootstrap.servers") {
            System.getenv("KAFKA_SERVERS") ?: "localhost:9092"
        }
        bootConfig = SmartConfigFactory.createWithoutSecurityServices().create(
            ConfigFactory.parseMap(parsedMessagingParams)
                .withValue(
                    BootConfig.TOPIC_PREFIX,
                    ConfigValueFactory.fromAnyRef("")
                ).withValue(
                    MessagingConfig.Bus.BUS_TYPE,
                    ConfigValueFactory.fromAnyRef("KAFKA")
                )
        )
        configFromFile = parameters.simulatorConfig?.let { ConfigFactory.parseFile(it) } ?: ConfigFactory.empty()
        clients = parameters.clients ?: configFromFile.getIntOrNull(AppSimulator.PARALLEL_CLIENTS_KEY)
            ?: AppSimulator.DEFAULT_PARALLEL_CLIENTS
        simulatorMode = parameters.simulationMode ?: configFromFile.getEnumOrNull<SimulationMode>("simulatorMode")
    }
}

data class TopicCreationParams(val numPartitions: Int, val replicationFactor: Int) {
    companion object {
        fun read(commonConfig: CommonConfig): TopicCreationParams {
            val numPartitions = getTopicCreationParameter(
                "numPartitions", DEFAULT_NUMBER_OF_PARTITIONS, commonConfig.configFromFile, commonConfig.parameters
            )
            val replicationFactor = getTopicCreationParameter(
                "replicationFactor", DEFAULT_REPLICATION_FACTOR, commonConfig.configFromFile, commonConfig.parameters
            )
            return TopicCreationParams(numPartitions, replicationFactor)
        }
    }
}

data class DBParams(val username: String, val password: String, val host: String, val db: String) {
    companion object {
        fun read(commonConfig: CommonConfig): DBParams {
            val username = getDbParameter("username", commonConfig.configFromFile, commonConfig.parameters)
            val password = getDbParameter("password", commonConfig.configFromFile, commonConfig.parameters)
            val host = getDbParameter("host", commonConfig.configFromFile, commonConfig.parameters)
            val db = getDbParameter("db", commonConfig.configFromFile, commonConfig.parameters)
            return DBParams(username, password, host, db)
        }
    }
}

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

    companion object {
        fun read(commonConfig: CommonConfig): LoadGenerationParams {
            val peerX500Name = getLoadGenStrParameter("peerX500Name", commonConfig.configFromFile, commonConfig.parameters)
            MemberX500Name.parse(peerX500Name)
            val peerGroupId = getLoadGenStrParameter("peerGroupId", commonConfig.configFromFile, commonConfig.parameters)
            val ourX500Name = getLoadGenStrParameter("ourX500Name", commonConfig.configFromFile, commonConfig.parameters)
            MemberX500Name.parse(ourX500Name)
            val ourGroupId = getLoadGenStrParameter("ourGroupId", commonConfig.configFromFile, commonConfig.parameters)
            val loadGenerationType: LoadGenerationType =
                getLoadGenEnumParameter("loadGenerationType", commonConfig.configFromFile, commonConfig.parameters)
            val totalNumberOfMessages = when (loadGenerationType) {
                LoadGenerationType.ONE_OFF -> getLoadGenIntParameter(
                    "totalNumberOfMessages",
                    AppSimulator.DEFAULT_TOTAL_NUMBER_OF_MESSAGES,
                    commonConfig.configFromFile,
                    commonConfig.parameters,
                )

                LoadGenerationType.CONTINUOUS -> null
            }
            val batchSize =
                getLoadGenIntParameter("batchSize", AppSimulator.DEFAULT_BATCH_SIZE, commonConfig.configFromFile, commonConfig.parameters)
            val interBatchDelay =
                getLoadGenDuration(
                    "interBatchDelay",
                    AppSimulator.DEFAULT_INTER_BATCH_DELAY,
                    commonConfig.configFromFile,
                    commonConfig.parameters
                )
            val messageSizeBytes =
                getLoadGenIntParameter(
                    "messageSizeBytes",
                    AppSimulator.DEFAULT_MESSAGE_SIZE_BYTES, commonConfig.configFromFile, commonConfig.parameters
                )
            val expireAfterTime = getLoadGenDurationOrNull("expireAfterTime", commonConfig.configFromFile, commonConfig.parameters)
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
