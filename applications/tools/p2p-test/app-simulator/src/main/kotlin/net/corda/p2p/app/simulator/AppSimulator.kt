package net.corda.p2p.app.simulator

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.p2p.app.simulator.AppSimulatorTopicCreator.Companion.APP_RECEIVED_MESSAGES_TOPIC
import net.corda.p2p.app.simulator.ArgParsingUtils.Companion.getDbParameter
import net.corda.p2p.app.simulator.ArgParsingUtils.Companion.getEnumOrNull
import net.corda.p2p.app.simulator.ArgParsingUtils.Companion.getIntOrNull
import net.corda.p2p.app.simulator.ArgParsingUtils.Companion.getLoadGenDuration
import net.corda.p2p.app.simulator.ArgParsingUtils.Companion.getLoadGenDurationOrNull
import net.corda.p2p.app.simulator.ArgParsingUtils.Companion.getLoadGenEnumParameter
import net.corda.p2p.app.simulator.ArgParsingUtils.Companion.getLoadGenIntParameter
import net.corda.p2p.app.simulator.ArgParsingUtils.Companion.getLoadGenStrParameter
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
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
        private val logger: Logger = contextLogger()
        private val clock: Clock = UTCClock()
        const val DB_PARAMS_PREFIX = "dbParams"
        const val LOAD_GEN_PARAMS_PREFIX = "loadGenerationParams"
        const val PARALLEL_CLIENTS_KEY = "parallelClients"
        const val DEFAULT_PARALLEL_CLIENTS = 1
        const val DEFAULT_TOTAL_NUMBER_OF_MESSAGES = 1
        const val DEFAULT_BATCH_SIZE = 50
        val DEFAULT_INTER_BATCH_DELAY = Duration.ZERO
        const val DEFAULT_MESSAGE_SIZE_BYTES = 10_000
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
                logErrorAndShutdown("Could not run: ${e.message}")
            }
        }
    }

    @Suppress("ComplexMethod")
    private fun runSimulator(parameters: CliParameters, bootConfig: SmartConfig) {
        val configFromFile = parameters.simulatorConfig?.let { ConfigFactory.parseFile(it) } ?: ConfigFactory.empty()
        val clients = parameters.clients ?: configFromFile.getIntOrNull(PARALLEL_CLIENTS_KEY) ?: DEFAULT_PARALLEL_CLIENTS
        val simulatorMode = parameters.simulationMode ?: configFromFile.getEnumOrNull("simulatorMode")
        if (simulatorMode == null) {
            logErrorAndShutdown("Simulation mode must be specified as a command line option or in the config file.")
        }
        when (simulatorMode) {
            SimulationMode.SENDER -> {
                runSender(configFromFile, parameters, bootConfig, clients, parameters.instanceId)
            }
            SimulationMode.RECEIVER -> {
                runReceiver(parameters, bootConfig, clients, parameters.instanceId)
            }
            SimulationMode.DB_SINK -> {
                runSink(configFromFile, parameters, bootConfig, clients, parameters.instanceId)
            }
            else -> throw IllegalStateException("Invalid value for simulator mode: $simulatorMode")
        }
    }

    @Suppress("LongParameterList")
    private fun runSender(
        configFromFile: Config,
        parameters: CliParameters,
        bootConfig: SmartConfig,
        clients: Int,
        instanceId: String,
    ) {
        val sendTopic = parameters.sendTopic ?: P2P_OUT_TOPIC
        val connectionDetails = readDbParams(configFromFile, parameters)
        val loadGenerationParams = readLoadGenParams(configFromFile, parameters)
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
        parameters: CliParameters,
        bootConfig: SmartConfig,
        clients: Int,
        instanceId: String,
    ) {
        val receiveTopic = parameters.receiveTopic ?: P2P_IN_TOPIC
        val receiver = Receiver(
            subscriptionFactory,
            configMerger,
            topicAdmin,
            receiveTopic,
            bootConfig,
            clients,
            instanceId
        )
        receiver.start()
        resources.add(receiver)
    }

    @Suppress("LongParameterList")
    private fun runSink(
        configFromFile: Config,
        parameters: CliParameters,
        bootConfig: SmartConfig,
        clients: Int,
        instanceId: String,
    ) {
        val connectionDetails = readDbParams(configFromFile, parameters)
        if (connectionDetails == null) {
            logErrorAndShutdown("dbParams configuration option is mandatory for sink mode.")
            return
        }
        val sink = Sink(subscriptionFactory, configMerger, connectionDetails, bootConfig, clients, instanceId)
        sink.start()
        resources.add(sink)
    }

    private fun readDbParams(config: Config, parameters: CliParameters): DBParams? {
        val username = getDbParameter("username", config, parameters, ::logErrorAndShutdown) ?: return null
        val password = getDbParameter("password", config, parameters, ::logErrorAndShutdown) ?: return null
        val host = getDbParameter("host", config, parameters, ::logErrorAndShutdown) ?: return null
        val db = getDbParameter("db", config, parameters, ::logErrorAndShutdown) ?: return null
        val dbParams = DBParams(username, password, host, db)
        logger.info("$dbParams")
        return dbParams
    }

    private fun readLoadGenParams(configFromFile: Config, parameters: CliParameters): LoadGenerationParams {
        val peerX500Name = getLoadGenStrParameter("peerX500Name", configFromFile, parameters, ::logErrorAndShutdown)
        val peerGroupId = getLoadGenStrParameter("peerGroupId", configFromFile, parameters, ::logErrorAndShutdown)
        val ourX500Name = getLoadGenStrParameter("ourX500Name", configFromFile, parameters, ::logErrorAndShutdown)
        val ourGroupId = getLoadGenStrParameter("ourGroupId", configFromFile, parameters, ::logErrorAndShutdown)
        val loadGenerationType: LoadGenerationType? =
            getLoadGenEnumParameter<LoadGenerationType>("loadGenerationType", configFromFile, parameters, ::logErrorAndShutdown)
        val totalNumberOfMessages = when (loadGenerationType) {
            LoadGenerationType.ONE_OFF -> getLoadGenIntParameter(
                "totalNumberOfMessages",
                DEFAULT_TOTAL_NUMBER_OF_MESSAGES,
                configFromFile,
                parameters,
                ::logErrorAndShutdown
            )

            LoadGenerationType.CONTINUOUS -> null
            else -> throw IllegalStateException("Invalid value for load generation type: $loadGenerationType")
        }
        val batchSize = getLoadGenIntParameter("batchSize", DEFAULT_BATCH_SIZE, configFromFile, parameters, ::logErrorAndShutdown)
        val interBatchDelay =
            getLoadGenDuration("interBatchDelay", DEFAULT_INTER_BATCH_DELAY, configFromFile, parameters, ::logErrorAndShutdown)
        val messageSizeBytes =
            getLoadGenIntParameter("messageSizeBytes", DEFAULT_MESSAGE_SIZE_BYTES, configFromFile, parameters, ::logErrorAndShutdown)
        val expireAfterTime = getLoadGenDurationOrNull("expireAfterTime", configFromFile, parameters, ::logErrorAndShutdown)
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
        names = ["-m", "--messagingParams"],
        description = ["Messaging parameters for the simulator."]
    )
    var messagingParams = emptyMap<String, String>()

    @CommandLine.Option(
        names = ["-d", "--databaseParams"],
        description = ["Database parameters for the simulator."]
    )
    var databaseParams = emptyMap<String, String>()

    @CommandLine.Option(
        names = ["-l", "--loadGenerationParams"],
        description = ["Load generation parameters for the simulator."]
    )
    var loadGenerationParams = emptyMap<String, String>()

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
