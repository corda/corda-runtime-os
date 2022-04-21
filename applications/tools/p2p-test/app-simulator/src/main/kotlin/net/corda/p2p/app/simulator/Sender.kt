package net.corda.p2p.app.simulator

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.typesafe.config.ConfigValueFactory
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.schema.configuration.MessagingConfig.Boot.INSTANCE_ID
import net.corda.schema.configuration.MessagingConfig.Bus.BOOTSTRAP_SERVER
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.schema.configuration.MessagingConfig.Bus.KAFKA_PRODUCER_CLIENT_ID
import net.corda.v5.base.util.contextLogger
import java.io.Closeable
import java.nio.ByteBuffer
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.thread
import kotlin.concurrent.write

@Suppress("LongParameterList")
class Sender(private val publisherFactory: PublisherFactory,
             private val dbParams: DBParams?,
             private val loadGenParams: LoadGenerationParams,
             private val sendTopic: String,
             private val kafkaServers: String,
             private val clients: Int,
             private val instanceId: String,
             private val clock: Clock
             ): Closeable {

    companion object {
        private val logger = contextLogger()
    }

    private val random = Random()
    private val objectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

    private val writerThreads = mutableListOf<Thread>()
    private val stopLock = ReentrantReadWriteLock()
    @Volatile
    private var stop = false

    fun start() {
        val senderId = UUID.randomUUID().toString()
        logger.info("Using sender ID: $senderId")

        val threads = (1..clients).map { client ->
            thread(isDaemon = true) {
                val dbConnection = if(dbParams != null) {
                    DbConnection(dbParams,
                        "INSERT INTO sent_messages (sender_id, message_id) " +
                                "VALUES (?, ?) on conflict do nothing")
                } else {
                    null
                }
                var messagesSent = 0
                val kafkaConfig = SmartConfigImpl.empty()
                    .withValue(BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(kafkaServers))
                    .withValue(BUS_TYPE, ConfigValueFactory.fromAnyRef("KAFKA"))
                    .withValue(KAFKA_PRODUCER_CLIENT_ID, ConfigValueFactory.fromAnyRef("app-simulator-sender-$instanceId-$client"))
                    .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef("$instanceId-$client".hashCode()))
                val publisher = publisherFactory.createPublisher(PublisherConfig("app-simulator"), kafkaConfig)
                publisher.use {
                    while (moreMessagesToSend(messagesSent, loadGenParams)) {
                        val messageWithIds = (1..loadGenParams.batchSize).map {
                            "$senderId:$client:${++messagesSent}"
                        }.map {
                            createMessage(it, senderId, loadGenParams.peer, loadGenParams.ourIdentity, loadGenParams.messageSizeBytes)
                        }
                        val records = messageWithIds.map { (messageId, message) ->
                            Record(sendTopic, messageId, message)
                        }
                        stopLock.read {
                            if(!stop) {
                                val publishedIds = publisher.publish(records).zip(messageWithIds).filter { (future, messageWithId)->
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
                                    val messageSentEvents = publishedIds.map { messageId ->
                                        MessageSentEvent(senderId, messageId)
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

    fun stop() {
        stopLock.write {
            stop = true
        }
    }

    private fun calculateTtl(expireAfterTime: Duration?): Long? {
        return if(expireAfterTime == null) {
            null
        } else {
            expireAfterTime.toMillis() + clock.instant().toEpochMilli()
        }
    }

    private fun moreMessagesToSend(messagesSent: Int, loadGenerationParams: LoadGenerationParams): Boolean {
        if(stop) {
            return false
        }
        return when(loadGenerationParams.loadGenerationType) {
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
    ): Pair<String, AppMessage> {
        val ttl = calculateTtl(loadGenParams.expireAfterTime)
        val messageHeader = AuthenticatedMessageHeader(
            destinationIdentity,
            srcIdentity,
            ttl,
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
