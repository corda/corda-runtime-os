package net.corda.p2p.app.simulator

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
//import net.corda.libs.configuration.merger.ConfigMerger
//import net.corda.messaging.api.publisher.factory.PublisherFactory
//import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
//import net.corda.schema.TestSchema.Companion.APP_RECEIVED_MESSAGES_TOPIC
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
import java.lang.NumberFormatException
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlin.random.Random

@Component(immediate = true)
class AppSimulator @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
//    @Reference(service = PublisherFactory::class)
//    private val publisherFactory: PublisherFactory,
//    @Reference(service = SubscriptionFactory::class)
//    private val subscriptionFactory: SubscriptionFactory,
//    @Reference(service = ConfigMerger::class)
//    private val configMerger: ConfigMerger,
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

    private fun runSimulator(parameters: CliParameters, bootConfig: SmartConfig) {
        bootConfig.factory //Delete me
        val configFromFile = parameters.simulatorConfig?.let { ConfigFactory.parseFile(it) } ?: ConfigFactory.empty()
        val clients = parameters.clients ?: configFromFile.getIntOrNull(PARALLEL_CLIENTS_KEY) ?: DEFAULT_PARALLEL_CLIENTS
        logger.info("$clients")
        val simulatorMode = parameters.simulationMode ?: configFromFile.getEnumOrNull("simulatorMode")
        if (simulatorMode == null) {
            logErrorAndShutdown("Simulation mode must be specified as a command line option or in the config file.")
        }
        when (simulatorMode) {
            SimulationMode.SENDER -> {
                runSender(configFromFile, parameters, /*publisherFactory, bootConfig, clients, parameters.instanceId*/)
            }
            SimulationMode.RECEIVER -> {
                runReceiver(parameters, /*subscriptionFactory, bootConfig, clients, parameters.instanceId*/)
            }
            SimulationMode.DB_SINK -> {
                runSink(configFromFile, parameters, /*subscriptionFactory, bootConfig, clients, parameters.instanceId*/)
            }
            else -> throw IllegalStateException("Invalid value for simulator mode: $simulatorMode")
        }
    }

    @Suppress("LongParameterList")
    private fun runSender(
        configFromFile: Config,
        parameters: CliParameters,
//        publisherFactory: PublisherFactory,
//        bootConfig: SmartConfig,
//        clients: Int,
//        instanceId: String,
    ) {
        val sendTopic = parameters.sendTopic ?: P2P_OUT_TOPIC
        val connectionDetails = readDbParams(configFromFile, parameters)
        logger.info("$sendTopic $connectionDetails")
        val loadGenerationParams = readLoadGenParams(configFromFile, parameters)
//        val sender = Sender(
//            publisherFactory,
//            configMerger,
//            connectionDetails,
//            loadGenerationParams,
//            sendTopic,
//            bootConfig,
//            clients,
//            instanceId,
//            clock
//        )
//        sender.start()
//        resources.add(sender)
        // If it's one-off we wait until all messages have been sent.
        // Otherwise, we let the threads run until the process is stopped by the user.
        if (loadGenerationParams.loadGenerationType == LoadGenerationType.ONE_OFF) {
//            sender.waitUntilComplete()
            shutdownOSGiFramework()
        }
    }

    private fun runReceiver(
        parameters: CliParameters,
    //    subscriptionFactory: SubscriptionFactory,
//        bootConfig: SmartConfig,
//        clients: Int,
//        instanceId: String,
    ) {
        val receiveTopic = parameters.receiveTopic ?: P2P_IN_TOPIC
        logger.info("$receiveTopic")
//        val receiver = Receiver(
//            subscriptionFactory,
//            configMerger,
//            receiveTopic,
//            APP_RECEIVED_MESSAGES_TOPIC,
//            bootConfig,
//            clients,
//            instanceId
//        )
//        receiver.start()
//        resources.add(receiver)
    }

    private fun runSink(
        configFromFile: Config,
        parameters: CliParameters,
//        subscriptionFactory: SubscriptionFactory,
//        bootConfig: SmartConfig,
//        clients: Int,
//        instanceId: String,
    ) {
        val connectionDetails = readDbParams(configFromFile, parameters)
        if (connectionDetails == null) {
            logErrorAndShutdown("dbParams configuration option is mandatory for sink mode.")
            return
        }
//        val sink = Sink(subscriptionFactory, configMerger, connectionDetails, bootConfig, clients, instanceId)
//        sink.start()
//        resources.add(sink)
    }

    private fun readDbParams(config: Config, parameters: CliParameters): DBParams? {
        val username = getDbParameter("username", config, parameters) ?: return null
        val password = getDbParameter("password", config, parameters) ?: return null
        val host = getDbParameter("host", config, parameters) ?: return null
        val db = getDbParameter("db", config, parameters) ?: return null
        val dbParams = DBParams(username, password, host, db)
        logger.info("$dbParams")
        return dbParams
    }

    /***
     * Get a database parameter from the command line or the config file. Command line options override options in
     * the config file.
     */
    private fun getDbParameter(path: String, configFromFile: Config, parameters: CliParameters): String? {
        val parameter = getParameter(path, configFromFile.getConfigOrEmpty(DB_PARAMS_PREFIX), parameters.databaseParams)
        if (parameter == null) {
            logErrorAndShutdown("Database parameter $path must be specified on the command line, using -d$path, or in a config file.")
        }
        return parameter
    }

    /***
     * Get a load generation parameter from the command line or the config file. Command line options override options in
     * the config file.
     */
    private fun getLoadGenStrParameter(path: String, configFromFile: Config, parameters: CliParameters): String? {
        val parameter = getParameter(path, configFromFile.getConfigOrEmpty(LOAD_GEN_PARAMS_PREFIX), parameters.loadGenerationParams)
        if (parameter == null) {
            logErrorAndShutdown("Load generation parameter $path must be specified on the command line, using -l$path, or in a config file.")
        }
        return parameter
    }

    private inline fun <reified E: Enum<E>> getLoadGenEnumParameter(path: String, configFromFile: Config, parameters: CliParameters): E? {
        val stringParameter = getParameter(
            path,
            configFromFile.getConfigOrEmpty(LOAD_GEN_PARAMS_PREFIX),
            parameters.loadGenerationParams
        )
        if (stringParameter == null) {
            logErrorAndShutdown("Load generation parameter $path must be specified on the command line, using -l$path, or in a " +
                "config file. " + "Must be one of (${E::class.java.enumConstants.map { it.name }.toSet()})."
            )
        }
        val enum = E::class.java.enumConstants.singleOrNull {
            it.name == stringParameter
        }
        if (enum == null) {
            logErrorAndShutdown("Load generation parameter $path = $stringParameter is not one of " +
                "(${E::class.java.enumConstants.map { it.name }.toSet()})."
            )
        }
        return enum
    }

    private fun getLoadGenIntParameter(path: String, default: Int, configFromFile: Config, parameters: CliParameters): Int {
        val stringParameter = getParameter(path, configFromFile.getConfigOrEmpty(LOAD_GEN_PARAMS_PREFIX), parameters.loadGenerationParams)
            ?: return default
        return try {
            Integer.parseInt(stringParameter)
        } catch (exception: NumberFormatException) {
            logErrorAndShutdown("Load generation parameter $path = $stringParameter is not an integer.")
            default
        }
    }

    private fun getLoadGenDuration(path: String, default: Duration, configFromFile: Config, parameters: CliParameters): Duration {
        val parameterFromCommandLine = parameters.loadGenerationParams[path]
        return if (parameterFromCommandLine == null) {
            try {
                configFromFile.getDuration(path)
            } catch (exception: ConfigException.Missing) {
                default
            }
        } else {
            Duration.parse(parameterFromCommandLine)
        }
    }

    private fun getLoadGenDurationOrNull(path: String, configFromFile: Config, parameters: CliParameters): Duration? {
        val parameterFromCommandLine = parameters.loadGenerationParams[path]
        return if (parameterFromCommandLine == null) {
            try {
                configFromFile.getDuration(path)
            } catch (exception: ConfigException.Missing) {
                null
            }
        } else {
            try {
                Duration.parse(parameterFromCommandLine)
            } catch (exception: DateTimeParseException) {
                logErrorAndShutdown("Load generation parameter $path = $parameterFromCommandLine can not be parsed as a duration. " +
                    "It must have the ISO-8601 duration format PnDTnHnMn.nS. e.g. PT20.5S gets converted to 20.5 seconds.")
                null
            }
        }
    }

    private fun getParameter(path: String, config: Config, parameters: Map<String, String>): String? {
        return parameters[path] ?: config.getStringOrNull(path)
    }

    private fun Config.getStringOrNull(path: String): String? {
        return getOrNull(path, this::getString)
    }

    private fun Config.getConfigOrEmpty(path: String): Config {
        return getOrNull(path, this::getConfig) ?: ConfigFactory.empty()
    }

    private fun Config.getIntOrNull(path: String): Int? {
        return getOrNull(path, this::getInt)
    }

    private fun <E> getOrNull(path: String, getFun: (String) -> E): E? {
        return try {
            getFun(path)
        } catch (exception: ConfigException.Missing) {
            null
        }
    }

    private inline fun <reified E: Enum<E>> Config.getEnumOrNull(path: String): E? {
        return try {
            this.getEnum(E::class.java, path)
        } catch (exception: ConfigException.Missing) {
            null
        }
    }

    private fun readLoadGenParams(configFromFile: Config, parameters: CliParameters): LoadGenerationParams {
        val peerX500Name = getLoadGenStrParameter("peerX500Name", configFromFile, parameters)
        val peerGroupId = getLoadGenStrParameter("peerGroupId", configFromFile, parameters)
        val ourX500Name = getLoadGenStrParameter("ourX500Name", configFromFile, parameters)
        val ourGroupId = getLoadGenStrParameter("ourGroupId", configFromFile, parameters)
        val loadGenerationType: LoadGenerationType? = getLoadGenEnumParameter<LoadGenerationType>("loadGenerationType", configFromFile, parameters)
        val totalNumberOfMessages = when (loadGenerationType) {
            LoadGenerationType.ONE_OFF -> getLoadGenIntParameter(
                "totalNumberOfMessages",
                DEFAULT_TOTAL_NUMBER_OF_MESSAGES,
                configFromFile,
                parameters
            )
            LoadGenerationType.CONTINUOUS -> null
            else -> throw IllegalStateException("Invalid value for load generation type: $loadGenerationType")
        }
        val batchSize = getLoadGenIntParameter("batchSize", DEFAULT_BATCH_SIZE, configFromFile, parameters)
        val interBatchDelay = getLoadGenDuration("interBatchDelay", DEFAULT_INTER_BATCH_DELAY, configFromFile, parameters)
        val messageSizeBytes = getLoadGenIntParameter("messageSizeBytes", DEFAULT_MESSAGE_SIZE_BYTES, configFromFile, parameters)
        val expireAfterTime = getLoadGenDurationOrNull("expireAfterTime", configFromFile, parameters)
        val params = LoadGenerationParams(
            HoldingIdentity(peerX500Name, peerGroupId),
            HoldingIdentity(ourX500Name, ourGroupId),
            loadGenerationType,
            totalNumberOfMessages,
            batchSize,
            interBatchDelay,
            messageSizeBytes,
            expireAfterTime
        )
        logger.info("$params")
        return params
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
