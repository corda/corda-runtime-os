package net.corda.p2p.app.simulator

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.typesafe.config.ConfigValueFactory
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.simulator.AppSimulator.Companion.APP_SIMULATOR_SUBSYSTEM
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.MessagingConfig
import net.corda.schema.configuration.MessagingConfig.Bus.KAFKA_PRODUCER_CLIENT_ID
import net.corda.utilities.time.Clock
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.util.Random
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.thread
import kotlin.concurrent.write

@Suppress("LongParameterList")
class Sender(
    private val publisherFactory: PublisherFactory,
    private val configMerger: ConfigMerger,
    private val commonConfig: CommonConfig,
    private val dbParams: DBParams?,
    private val loadGenParams: LoadGenerationParams,
    private val clock: Clock,
) : Closeable {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val random = Random()
    private val objectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

    private val writerThreads = mutableListOf<Thread>()
    private val stopLock = ReentrantReadWriteLock()

    @Volatile
    private var stop = false

    private data class MessageMetaData(val senderId: String, val messageId: String)

    fun start() {
        val senderIds = loadGenParams.senders.associateBy { _ -> UUID.randomUUID().toString() }
        logger.info("Using sender IDs: $senderIds")

        val threads = (1..commonConfig.clients).map { client ->
            thread(isDaemon = true) {
                val dbConnection = if (dbParams != null) {
                    DbConnection(
                        dbParams,
                        "INSERT INTO sent_messages (sender_id, message_id) " +
                            "VALUES (?, ?) on conflict do nothing",
                    )
                } else {
                    null
                }
                var messagesSent = 0
                val instanceId = commonConfig.parameters.instanceId
                val configWithInstanceId = commonConfig.bootConfig
                    .withValue(KAFKA_PRODUCER_CLIENT_ID, ConfigValueFactory.fromAnyRef("app-simulator-sender-$instanceId-$client"))
                    .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef("$instanceId-$client".hashCode()))
                    .withValue(MessagingConfig.MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(10000000))
                val messagingConfig = configMerger.getMessagingConfig(configWithInstanceId)
                val publisher = publisherFactory.createPublisher(PublisherConfig("app-simulator", false), messagingConfig)
                publisher.use {
                    val allPossibleCombinations = senderIds.flatMap { (_, senderHoldingId) ->
                        loadGenParams.peers.map { destinationHoldingId ->
                            senderHoldingId to destinationHoldingId
                        }
                    }

                    var currentIndex = 0

                    while (moreMessagesToSend(messagesSent, loadGenParams)) {
                        logger.debug("Continue to send messages starting from $currentIndex index")
                        val messagesWithIds = mutableListOf<Pair<MessageMetaData, AppMessage>>()
                        while (messagesWithIds.size < loadGenParams.batchSize) {
                            val currentSenderDestinationPair = allPossibleCombinations[currentIndex]
                            val senderHoldingId = currentSenderDestinationPair.first
                            val destination = currentSenderDestinationPair.second
                            val senderId = senderIds.entries.first {
                                it.value == currentSenderDestinationPair.first
                            }.key

                            messagesWithIds.add(
                                createMessage(
                                    "$senderId:$client:${++messagesSent}",
                                    senderId,
                                    destination,
                                    senderHoldingId,
                                    loadGenParams.messageSizeBytes
                                )
                            )
                            currentIndex = (currentIndex + 1) % allPossibleCombinations.size
                        }

                        val records = messagesWithIds.map { (messageMetaData, message) ->
                            logger.debug("Going to publish message with ID ${messageMetaData.messageId}")
                            Record(commonConfig.parameters.sendTopic, messageMetaData.messageId, message)
                        }
                        stopLock.read {
                            if (!stop) {
                                val publishedIds = publisher.publish(records).zip(messagesWithIds).filter { (future, messageWithId) ->
                                    try {
                                        future.get()
                                        true
                                    } catch (e: ExecutionException) {
                                        logger.warn("Could not publish message with ID: ${messageWithId.first}", e)
                                        false
                                    }
                                }.map { it.second.first }
                                logger.info("Published ${publishedIds.size} messages")

                                if (dbConnection?.connection != null) {
                                    val messageSentEvents = publishedIds.map { messageMetaData ->
                                        MessageSentEvent(messageMetaData.senderId, messageMetaData.messageId)
                                    }
                                    writeSentMessagesToDb(dbConnection, messageSentEvents)
                                }
                            }
                        }

                        Thread.sleep(loadGenParams.interBatchDelay.toMillis())
                    }
                    logger.info("Client $client sent $messagesSent messages.")
                }
                dbConnection?.close()
            }
        }
        writerThreads.addAll(threads)
    }

    /**
     * This will wait until load generation is complete.
     * Note: in case of a one-off run, this is guaranteed to return once all messages have been sent.
     *       In case of a continuous run, this will effectively block until stop() is called.
     */
    fun waitUntilComplete() {
        writerThreads.forEach { it.join() }
    }

    override fun close() {
        stop()
    }

    private fun stop() {
        stopLock.write {
            stop = true
        }
    }

    private fun calculateTtl(expireAfterTime: Duration?): Instant? {
        return if (expireAfterTime == null) {
            null
        } else {
            Instant.ofEpochMilli(expireAfterTime.toMillis() + clock.instant().toEpochMilli())
        }
    }

    private fun moreMessagesToSend(messagesSent: Int, loadGenerationParams: LoadGenerationParams): Boolean {
        if (stop) {
            return false
        }
        return when (loadGenerationParams.loadGenerationType) {
            LoadGenerationType.ONE_OFF -> (messagesSent < loadGenerationParams.totalNumberOfMessages!!)
            LoadGenerationType.CONTINUOUS -> true
        }
    }

    private fun createMessage(
        messageId: String,
        senderId: String,
        destinationIdentity: HoldingIdentity,
        srcIdentity: HoldingIdentity,
        messageSize: Int,
    ): Pair<MessageMetaData, AppMessage> {
        val ttl = calculateTtl(loadGenParams.expireAfterTime)
        val messageHeader = AuthenticatedMessageHeader(
            destinationIdentity,
            srcIdentity,
            ttl,
            messageId,
            messageId,
            APP_SIMULATOR_SUBSYSTEM,
            MembershipStatusFilter.ACTIVE,
        )
        val randomData = ByteArray(messageSize).apply {
            random.nextBytes(this)
        }
        val payload = MessagePayload(senderId, randomData, Instant.now())
        val message = AuthenticatedMessage(messageHeader, ByteBuffer.wrap(objectMapper.writeValueAsBytes(payload)))
        logger.debug("Created message from ${srcIdentity.x500Name} to ${destinationIdentity.x500Name}")
        return MessageMetaData(senderId, messageId) to AppMessage(message)
    }

    private fun writeSentMessagesToDb(dbConnection: DbConnection, messages: List<MessageSentEvent>) {
        messages.forEach { messageSentEvent ->
            dbConnection.statement.setString(1, messageSentEvent.sender)
            dbConnection.statement.setString(2, messageSentEvent.messageId)
            dbConnection.statement.addBatch()
        }
        dbConnection.statement.executeBatch()
        dbConnection.connection.commit()
    }
}
