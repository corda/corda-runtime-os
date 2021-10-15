package net.corda.p2p.app.simulator

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.p2p.app.HoldingIdentity
import net.corda.p2p.schema.Schema
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import java.time.Instant
import java.util.Properties

@Component(immediate = true)
class AppSimulator @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory
): Application {

    companion object {
        private val logger: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
        const val KAFKA_BOOTSTRAP_SERVER = "bootstrap.servers"
        const val KAFKA_BOOTSTRAP_SERVER_KEY = "messaging.kafka.common.bootstrap.servers"
        const val PRODUCER_CLIENT_ID = "messaging.kafka.producer.client.id"
        const val DELIVERED_MSG_TOPIC = "app.received_msg"

        const val DB_PARAMS_PREFIX = "dbParams"
        const val LOAD_GEN_PARAMS_PREFIX = "loadGenerationParams"
        const val PARALLEL_CLIENTS_KEY = "parallelClients"
        val DEFAULT_CONFIG = ConfigFactory.parseMap(mapOf(
            "$LOAD_GEN_PARAMS_PREFIX.batchSize" to 50,
            "$LOAD_GEN_PARAMS_PREFIX.interBatchDelay" to Duration.ZERO,
            "$LOAD_GEN_PARAMS_PREFIX.messageSizeBytes" to 10_000,
            PARALLEL_CLIENTS_KEY to 1
        ))
    }

    private val resources = mutableListOf<Closeable>()
    private var dbConnection: Connection? = null


    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        consoleLogger.info("Starting application simulator tool")

        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)
        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            shutdownOSGiFramework()
        } else {
            val kafkaProperties = Properties()
            val kafkaPropertiesFile = parameters.kafkaConnection
            if (kafkaPropertiesFile == null) {
                logger.error("No file path passed for --kafka.")
                shutdownOSGiFramework()
                return
            }
            kafkaProperties.load(FileInputStream(kafkaPropertiesFile))
            if (!kafkaProperties.containsKey(KAFKA_BOOTSTRAP_SERVER)) {
                logger.error("No $KAFKA_BOOTSTRAP_SERVER property found in file specified via --kafka!")
                shutdownOSGiFramework()
                return
            }

            if (parameters.simulatorConfig == null) {
                logger.error("No value passed for --simulator-config.")
                shutdownOSGiFramework()
                return
            }

            runSimulator(parameters, kafkaProperties)
        }
    }

    private fun runSimulator(parameters: CliParameters, kafkaProperties: Properties) {
        val sendTopic = parameters.sendTopic ?: Schema.P2P_OUT_TOPIC
        val receiveTopic = parameters.receiveTopic ?: Schema.P2P_IN_TOPIC
        val simulatorConfig = ConfigFactory.parseFile(parameters.simulatorConfig).withFallback(DEFAULT_CONFIG)
        val clients = simulatorConfig.getInt(PARALLEL_CLIENTS_KEY)
        val dbConnection = readDbParams(simulatorConfig)?.let { connectToDb(it) }

        val simulatorMode = simulatorConfig.getEnum(SimulationMode::class.java, "simulatorMode")
        when(simulatorMode) {
            SimulationMode.SENDER -> {
                runSender(simulatorConfig, publisherFactory, dbConnection, sendTopic, kafkaProperties, clients)
            }
            SimulationMode.RECEIVER -> {
                runReceiver(subscriptionFactory, receiveTopic, kafkaProperties, clients)
            }
            SimulationMode.DB_SINK -> {
                runSink(subscriptionFactory, dbConnection, kafkaProperties, clients)
            }
            else -> throw IllegalStateException("Invalid value for simulator mode: $simulatorMode")
        }
    }

    @Suppress("LongParameterList")
    private fun runSender(simulatorConfig: Config, publisherFactory: PublisherFactory, dbConnection: Connection?,
                          sendTopic: String, kafkaProperties: Properties, clients: Int) {
        val loadGenerationParams = readLoadGenParams(simulatorConfig)
        val sender = Sender(publisherFactory, dbConnection, loadGenerationParams, sendTopic, kafkaProperties, clients)
        sender.start()
        resources.add(sender)
        // If it's one-off we wait until all messages have been sent.
        // Otherwise, we let the threads run until the process is stopped by the user.
        if (loadGenerationParams.loadGenerationType == LoadGenerationType.ONE_OFF) {
            sender.waitUntilComplete()
            shutdownOSGiFramework()
        }
    }

    private fun runReceiver(subscriptionFactory: SubscriptionFactory, receiveTopic: String, kafkaProperties: Properties, clients: Int) {
        val receiver = Receiver(subscriptionFactory, receiveTopic, DELIVERED_MSG_TOPIC, kafkaProperties, clients)
        receiver.start()
        resources.add(receiver)
    }

    private fun runSink(subscriptionFactory: SubscriptionFactory, dbConnection: Connection?, kafkaProperties: Properties, clients: Int) {
        if (dbConnection == null) {
            logger.error("dbParams configuration option is mandatory for sink mode.")
            shutdownOSGiFramework()
            return
        }
        val sink = Sink(subscriptionFactory, dbConnection, kafkaProperties, clients)
        sink.start()
        resources.add(sink)
    }

    private fun connectToDb(dbParams: DBParams): Connection {
        val properties = Properties()
        properties.setProperty("user", dbParams.username)
        properties.setProperty("password", dbParams.password)
        // DriverManager uses internally Class.forName(), which doesn't work within OSGi by default.
        // This is why we force-load the driver here. For example, see:
        // http://hwellmann.blogspot.com/2009/04/jdbc-drivers-in-osgi.html
        // https://stackoverflow.com/questions/54292876/how-to-use-mysql-in-osgi-application-with-maven
        org.postgresql.Driver()
        dbConnection = DriverManager.getConnection("jdbc:postgresql://${dbParams.host}/${dbParams.db}", properties)
        dbConnection!!.autoCommit = false
        return dbConnection!!
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
        val totalNumberOfMessages = when(loadGenerationType) {
            LoadGenerationType.ONE_OFF -> config.getInt("$LOAD_GEN_PARAMS_PREFIX.totalNumberOfMessages")
            LoadGenerationType.CONTINUOUS -> null
            else -> throw IllegalStateException("Invalid value for load generation type: $loadGenerationType")
        }
        val batchSize = config.getInt("$LOAD_GEN_PARAMS_PREFIX.batchSize")
        val interBatchDelay = config.getDuration("$LOAD_GEN_PARAMS_PREFIX.interBatchDelay")
        val messageSizeBytes = config.getInt("$LOAD_GEN_PARAMS_PREFIX.messageSizeBytes")
        return LoadGenerationParams(
            HoldingIdentity(peerX500Name, peerGroupId),
            HoldingIdentity(ourX500Name, ourGroupId),
            loadGenerationType,
            totalNumberOfMessages,
            batchSize,
            interBatchDelay,
            messageSizeBytes
        )
    }

    override fun shutdown() {
        logger.info("Shutting down application simulator tool")
        dbConnection?.close()
        resources.forEach { it.close() }
    }

    private fun shutdownOSGiFramework() {
        shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
    }

}

class CliParameters {
    @CommandLine.Option(names = ["--kafka"], description = ["File containing Kafka connection properties."])
    var kafkaConnection: File? = null

    @CommandLine.Option(names = ["--simulator-config"], description = ["File containing configuration parameters for simulator."])
    var simulatorConfig: File? = null

    @CommandLine.Option(names = ["--send-topic"], description = ["Topic to send the messages to. " +
            "Defaults to ${Schema.P2P_OUT_TOPIC}, if not specified."])
    var sendTopic: String? = null

    @CommandLine.Option(names = ["--receive-topic"], description = ["Topic to receive messages from. " +
            "Defaults to ${Schema.P2P_IN_TOPIC}, if not specified."])
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

data class LoadGenerationParams(val peer: HoldingIdentity,
                                val ourIdentity: HoldingIdentity,
                                val loadGenerationType: LoadGenerationType,
                                val totalNumberOfMessages: Int?,
                                val batchSize: Int,
                                val interBatchDelay: Duration,
                                val messageSizeBytes: Int) {
    init {
        when (loadGenerationType) {
            LoadGenerationType.ONE_OFF -> require(totalNumberOfMessages != null)
            LoadGenerationType.CONTINUOUS -> require(totalNumberOfMessages == null)
        }
    }
}

data class MessagePayload(val sender: String, val payload: ByteArray, val sendTimestamp: Instant)
data class MessageSentEvent(val sender: String, val messageId: String)
data class MessageReceivedEvent(val sender: String, val messageId: String, val sendTimestamp: Instant,
                                val receiveTimestamp: Instant, val deliveryLatency: Duration)