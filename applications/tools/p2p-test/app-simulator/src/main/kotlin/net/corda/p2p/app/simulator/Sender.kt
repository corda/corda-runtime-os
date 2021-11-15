package net.corda.p2p.app.simulator

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.p2p.app.HoldingIdentity
import net.corda.p2p.app.simulator.AppSimulator.Companion.KAFKA_BOOTSTRAP_SERVER_KEY
import net.corda.p2p.app.simulator.AppSimulator.Companion.PRODUCER_CLIENT_ID
import net.corda.v5.base.util.contextLogger
import java.io.Closeable
import java.nio.ByteBuffer
import java.sql.Connection
import java.time.Instant
import java.util.Random
import java.util.UUID
import kotlin.concurrent.thread

@Suppress("LongParameterList")
class Sender(private val publisherFactory: PublisherFactory,
             private val dbConnection: Connection?,
             private val loadGenParams: LoadGenerationParams,
             private val sendTopic: String,
             private val kafkaServers: String,
             private val clients: Int): Closeable {

    companion object {
        private val logger = contextLogger()
    }

    private val random = Random()
    private val objectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())
    private val writeSentStmt = dbConnection?.prepareStatement("INSERT INTO sent_messages (sender_id, message_id) " +
            "VALUES (?, ?) on conflict do nothing")

    private val writerThreads = mutableListOf<Thread>()
    @Volatile
    private var stopped = false

    fun start() {
        val senderId = UUID.randomUUID().toString()
        logger.info("Using sender ID: $senderId")

        val threads = (1..clients).map { client ->
            thread(isDaemon = true) {
                var messagesSent = 0
                val kafkaConfig = SmartConfigImpl.empty()
                    .withValue(KAFKA_BOOTSTRAP_SERVER_KEY, ConfigValueFactory.fromAnyRef(kafkaServers))
                    .withValue(PRODUCER_CLIENT_ID, ConfigValueFactory.fromAnyRef("app-simulator-sender-$client"))
                val publisher = publisherFactory.createPublisher(PublisherConfig("app-simulator"), kafkaConfig)
                publisher.use {
                    while (moreMessagesToSend(messagesSent, loadGenParams)) {
                        val messageWithIds = (1..loadGenParams.batchSize).map {
                            createMessage(senderId, loadGenParams.peer, loadGenParams.ourIdentity, loadGenParams.messageSizeBytes)
                        }
                        val records = messageWithIds.map { (messageId, message) ->
                            Record(sendTopic, messageId, message)
                        }
                        val futures = publisher.publish(records)

                        if (dbConnection != null) {
                            val messageSentEvents = messageWithIds.map { (messageId, _) ->
                                MessageSentEvent(senderId, messageId)
                            }
                            writeSentMessagesToDb(messageSentEvents)
                        }
                        futures.forEach { it.get() }
                        messagesSent += loadGenParams.batchSize

                        Thread.sleep(loadGenParams.interBatchDelay.toMillis())
                    }
                    logger.info("Client $client sent $messagesSent messages.")
                }
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

    fun stop() {
        stopped = true
    }

    private fun moreMessagesToSend(messagesSent: Int, loadGenerationParams: LoadGenerationParams): Boolean {
        return when(loadGenerationParams.loadGenerationType) {
            LoadGenerationType.ONE_OFF -> (messagesSent < loadGenerationParams.totalNumberOfMessages!!) && !stopped
            LoadGenerationType.CONTINUOUS -> !stopped
        }
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
        val payload = MessagePayload(senderId, randomData, Instant.now())
        val message = AuthenticatedMessage(messageHeader, ByteBuffer.wrap(objectMapper.writeValueAsBytes(payload)))
        return messageId to AppMessage(message)
    }

    private fun writeSentMessagesToDb(messages: List<MessageSentEvent>) {
        messages.forEach { messageSentEvent ->
            writeSentStmt!!.setString(1, messageSentEvent.sender)
            writeSentStmt.setString(2, messageSentEvent.messageId)
            writeSentStmt.addBatch()
        }
        writeSentStmt!!.executeBatch()
        dbConnection!!.commit()
    }


}