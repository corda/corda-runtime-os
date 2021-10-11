package net.corda.p2p.app.simulator

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
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
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.Properties
import java.util.Random
import java.util.UUID
import kotlin.concurrent.thread

@Suppress("SpreadOperator")
@Component(immediate = true)
class AppSimulator @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory
): Application {

    private companion object {
        private val logger: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
        const val KAFKA_BOOTSTRAP_SERVER = "bootstrap.servers"
        const val KAFKA_COMMON_BOOTSTRAP_SERVER = "messaging.kafka.common.bootstrap.servers"
        const val PRODUCER_CLIENT_ID = "messaging.kafka.producer.client.id"

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

    @Volatile
    private var stopped: Boolean = false
    private val subscriptions = mutableListOf<Subscription<String, AppMessage>>()
    private val writerThreads = mutableListOf<Thread>()

    private val random = Random()
    private val objectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

    private var dbConnection: Connection? = null
    private var writeSentStmt: PreparedStatement? = null
    private var writeReceivedStmt: PreparedStatement? = null


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
                logError("No file path passed for --kafka.")
                shutdownOSGiFramework()
                return
            }
            kafkaProperties.load(FileInputStream(kafkaPropertiesFile))
            if (!kafkaProperties.containsKey(KAFKA_BOOTSTRAP_SERVER)) {
                logError("No $KAFKA_BOOTSTRAP_SERVER property found in file specified via --kafka!")
                shutdownOSGiFramework()
                return
            }

            if (parameters.simulatorConfig == null) {
                logError("No value passed for --simulator-config.")
                shutdownOSGiFramework()
                return
            }

            val sendTopic = parameters.sendTopic ?: Schema.P2P_OUT_TOPIC
            val receiveTopic = parameters.receiveTopic ?: Schema.P2P_IN_TOPIC
            val simulatorConfig = ConfigFactory.parseFile(parameters.simulatorConfig).withFallback(DEFAULT_CONFIG)
            val clients = simulatorConfig.getInt(PARALLEL_CLIENTS_KEY)
            val dbParams = readDbParams(simulatorConfig)
            connectToDb(dbParams)


            val simulatorMode = simulatorConfig.getEnum(SimulationMode::class.java, "simulatorMode")
            when(simulatorMode) {
                SimulationMode.SENDER -> {
                    val loadGenerationParams = readLoadGenParams(simulatorConfig)
                    executeSender(loadGenerationParams, sendTopic, kafkaProperties, clients)
                }
                SimulationMode.RECEIVER -> {
                    startReceiver(receiveTopic, kafkaProperties, clients)
                }
                else -> throw IllegalStateException("Invalid value for simulator mode: $simulatorMode")
            }
        }
    }

    private fun executeSender(loadGenerationParams: LoadGenerationParams, sendTopic: String, kafkaProperties: Properties, clients: Int) {
        val senderId = UUID.randomUUID().toString()
        logInfo("Using sender ID: $senderId")

        val threads = (1..clients).map { client ->
            thread(isDaemon = true) {
                var messagesSent = 0
                val kafkaConfig = ConfigFactory.empty()
                    .withValue(KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(kafkaProperties[KAFKA_BOOTSTRAP_SERVER].toString()))
                    .withValue(PRODUCER_CLIENT_ID, ConfigValueFactory.fromAnyRef("app-simulator-sender-$client"))
                val publisher = publisherFactory.createPublisher(PublisherConfig("app-simulator"), kafkaConfig)
                publisher.use {
                    while (moreMessagesToSend(messagesSent, loadGenerationParams)) {
                        val messageWithIds = (1..loadGenerationParams.batchSize).map {
                            createMessage(senderId, loadGenerationParams.peer, loadGenerationParams.ourIdentity, loadGenerationParams.messageSizeBytes)
                        }
                        val records = messageWithIds.map { (messageId, message) ->
                            Record(sendTopic, messageId, message)
                        }
                        val now = Instant.now()
                        val futures = publisher.publish(records)
                        val messageSentEvents = messageWithIds.map { (messageId, _) ->
                            MessageSentEvent(senderId, messageId, now)
                        }
                        writeSentMessagesToDb(messageSentEvents)
                        futures.forEach { it.get() }
                        messagesSent += loadGenerationParams.batchSize

                        Thread.sleep(loadGenerationParams.interBatchDelay.toMillis())
                    }
                    logInfo("Client $client sent $messagesSent messages.")
                }
            }
        }
        writerThreads.addAll(threads)

        // If it's one-off we wait until all messages have been sent. Otherwise, we let the threads run until the process is stopped by the user.
        if (loadGenerationParams.loadGenerationType == LoadGenerationType.ONE_OFF) {
            writerThreads.forEach { it.join() }
            shutdownOSGiFramework()
        }
    }

    private fun moreMessagesToSend(messagesSent: Int, loadGenerationParams: LoadGenerationParams): Boolean {
        return when(loadGenerationParams.loadGenerationType) {
            LoadGenerationType.ONE_OFF -> (messagesSent < loadGenerationParams.totalNumberOfMessages!!) && !stopped
            LoadGenerationType.CONTINUOUS -> !stopped
        }
    }

    private fun startReceiver(receiveTopic: String, kafkaProperties: Properties, clients: Int) {
        (1..clients).forEach { client ->
            val subscriptionConfig = SubscriptionConfig("app-simulator", receiveTopic, 1)
            val kafkaConfig = ConfigFactory.empty()
                .withValue(KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(kafkaProperties[KAFKA_BOOTSTRAP_SERVER].toString()))
                .withValue(PRODUCER_CLIENT_ID, ConfigValueFactory.fromAnyRef("app-simulator-receiver-$client"))
            val subscription = subscriptionFactory.createEventLogSubscription(subscriptionConfig, MessageProcessor(), kafkaConfig, null)
            subscription.start()
        }
        logInfo("Started consuming messages fom $receiveTopic. When you want to stop the consumption, you can do so using Ctrl+C.")
    }

    private fun writeSentMessagesToDb(messages: List<MessageSentEvent>) {
        messages.forEach { messageSentEvent ->
            writeSentStmt!!.setString(1, messageSentEvent.sender)
            writeSentStmt!!.setString(2, messageSentEvent.messageId)
            writeSentStmt!!.setTimestamp(3, Timestamp.from(messageSentEvent.sentTime))
            writeSentStmt!!.addBatch()
        }
        writeSentStmt!!.executeBatch()
        dbConnection!!.commit()
    }

    private fun createMessage(senderId: String,
                              destinationIdentity: HoldingIdentity,
                              srcIdentity: HoldingIdentity,
                              messageSize: Int): Pair<String, AppMessage> {
        val messageId = UUID.randomUUID().toString()
        val messageHeader = AuthenticatedMessageHeader(
            destinationIdentity,
            srcIdentity,
            null,
            messageId,
            messageId,
            "app-simulator"
        )
        val randomData = ByteArray(messageSize).apply {
            random.nextBytes(this)
        }
        val payload = MessagePayload(senderId, randomData)
        val message = AuthenticatedMessage(messageHeader, ByteBuffer.wrap(objectMapper.writeValueAsBytes(payload)))
        return messageId to AppMessage(message)
    }

    private fun connectToDb(dbParams: DBParams) {
        val properties = Properties()
        properties.setProperty("user", dbParams.username)
        properties.setProperty("password", dbParams.password)
        // DriverManager uses internally Class.forName(), which doesn't work within OSGi by default. This is why we force-load the driver here.
        // For example, see:
        // http://hwellmann.blogspot.com/2009/04/jdbc-drivers-in-osgi.html
        // https://stackoverflow.com/questions/54292876/how-to-use-mysql-in-osgi-application-with-maven
        org.postgresql.Driver()
        dbConnection = DriverManager.getConnection("jdbc:postgresql://${dbParams.host}/${dbParams.db}", properties)
        dbConnection!!.autoCommit = false
        writeSentStmt = dbConnection!!.prepareStatement("INSERT INTO sent_messages (sender_id, message_id, sent_time) VALUES (?, ?, ?) on conflict do nothing")
        writeReceivedStmt = dbConnection!!.prepareStatement("INSERT INTO received_messages (sender_id, message_id, received_time) VALUES (?, ?, ?) on conflict do nothing")
    }

    private fun readDbParams(config: Config): DBParams {
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
        val loadGenerationType = config.getEnum(LoadGenerationType::class.java, "$LOAD_GEN_PARAMS_PREFIX.loadGenerationType")
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
        logInfo("Shutting down application simulator tool")
        stopped = true
        writerThreads.forEach { it.join() }
        subscriptions.forEach { it.stop() }
    }

    private fun shutdownOSGiFramework() {
        shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
    }

    private fun logInfo(error: String) {
        logger.info(error)
        consoleLogger.info(error)
    }

    private fun logError(error: String) {
        logger.error(error)
        consoleLogger.error(error)
    }

    private inner class MessageProcessor: EventLogProcessor<String, AppMessage> {

        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<AppMessage>
            get() = AppMessage::class.java

        override fun onNext(events: List<EventLogRecord<String, AppMessage>>): List<Record<*, *>> {
            val now = Instant.now()
            val messageReceivedEvents = events.map {
                val authenticatedMessage = it.value!!.message as AuthenticatedMessage
                val payload = objectMapper.readValue<MessagePayload>(authenticatedMessage.payload.array())
                MessageReceivedEvent(payload.sender, authenticatedMessage.header.messageId, now)
            }
            writeReceivedMessagesToDB(messageReceivedEvents)
            return emptyList()
        }

        private fun writeReceivedMessagesToDB(messages: List<MessageReceivedEvent>) {
            messages.forEach { messageReceivedEvent ->
                writeReceivedStmt!!.setString(1, messageReceivedEvent.sender)
                writeReceivedStmt!!.setString(2, messageReceivedEvent.messageId)
                writeReceivedStmt!!.setTimestamp(3, Timestamp.from(messageReceivedEvent.receivedTime))
                writeReceivedStmt!!.addBatch()
            }
            writeReceivedStmt!!.executeBatch()
            dbConnection!!.commit()
        }

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
    RECEIVER
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

data class MessagePayload(val sender: String, val payload: ByteArray)
data class MessageSentEvent(val sender: String, val messageId: String, val sentTime: Instant)
data class MessageReceivedEvent(val sender: String, val messageId: String, val receivedTime: Instant)