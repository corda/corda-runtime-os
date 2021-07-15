package net.corda.p2p.gateway.messaging.internal

import com.typesafe.config.ConfigFactory
import io.netty.channel.ConnectTimeoutException
import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.gateway.Gateway.Companion.PUBLISHER_ID
import net.corda.p2p.gateway.messaging.ConnectionManager
import net.corda.p2p.gateway.messaging.http.HttpMessage
import net.corda.p2p.schema.Schema.Companion.LINK_IN_TOPIC
import net.corda.v5.base.util.NetworkHostAndPort
import org.slf4j.LoggerFactory
import java.io.IOException
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.util.LinkedList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * This is an implementation of an [EventLogProcessor] used to consume messages from a P2P message subscription. The received
 * events are processed and fed into the HTTP pipeline. No records will be produced by this processor as a result.
 */
class OutboundMessageHandler(private val connectionPool: ConnectionManager,
                             publisherFactory: PublisherFactory
) : EventLogProcessor<String, LinkOutMessage> {

    private val logger = LoggerFactory.getLogger(OutboundMessageHandler::class.java)
    private val workers: ThreadPoolExecutor = Executors.newFixedThreadPool(4) as ThreadPoolExecutor
    private var p2pInPublisher = publisherFactory.createPublisher(PublisherConfig(PUBLISHER_ID), ConfigFactory.empty())

    @Suppress("TooGenericExceptionCaught", "ComplexMethod")
    override fun onNext(events: List<EventLogRecord<String, LinkOutMessage>>): List<Record<*, *>> {
        val destinationToMessagesMap = mutableMapOf<NetworkHostAndPort, MutableList<LinkInMessage>>()
        val workResults = mutableListOf<Future<*>>()
        events.forEach { evt ->
            // Separate records by destination. This way, we can continue processing messages to connected targets while
            // trying to establish connections for others; if connection times out, we abandon all messages for the unreachable target
            evt.value?.let { peerMessage ->
                val destination = NetworkHostAndPort.parse(peerMessage.header.address)
                val messages = destinationToMessagesMap.getOrDefault(destination, mutableListOf())
                messages.add(LinkInMessage(peerMessage.payload))
                destinationToMessagesMap[destination] = messages
            }
        }

        destinationToMessagesMap.forEach { entry ->
            workResults.add(workers.submit {
                val messagesToRetry = LinkedList<LinkInMessage>()
                try {
                    val client = connectionPool.acquire(entry.key)
                    entry.value.forEach { message ->
                        try {
                            val responseBarrier = CountDownLatch(1)
                            val responseSub = client.onReceive.subscribe { response ->
                                responseMessageHandler(response)
                                responseBarrier.countDown()
                            }
                            client.send(message.toByteBuffer().array())
                            if (!responseBarrier.await(1000, TimeUnit.MILLISECONDS)) {
                                logger.info("Response from ${entry.key} has not arrived in time. Scheduling for re-send")
                                // If the response has not arrived in the specified time, the message
                                // is queued for redelivery
                                messagesToRetry.add(message)
                            }
                            responseSub.unsubscribe()
                        } catch (e: IllegalStateException) {
                            logger.warn("Could not send message to target ${entry.key}. Scheduling for re-send", e)
                            messagesToRetry.add(message)
                        }
                    }
                } catch (e: ConnectTimeoutException) {
                    logger.warn("Could not establish a connection to ${entry.key}. Scheduling for re-send")
                    messagesToRetry.addAll(entry.value)
                }

                // Schedule any pending re-tries
                if (messagesToRetry.size > 0) {
                    Executors.newSingleThreadScheduledExecutor()
                        .schedule({ resend(entry.key, messagesToRetry) }, 5000L, TimeUnit.MILLISECONDS).get()
                }
            })
        }

        workResults.forEach { it.get() }

        return emptyList()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun resend(target: NetworkHostAndPort, messages: LinkedList<LinkInMessage>) {
        logger.debug("Retrying delivery of message to $target. No of messages to retry ${messages.size}")
        var successCounter = 0
        messages.forEach { message ->
            try {
                val client = connectionPool.acquire(target)
                val responseBarrier = CountDownLatch(1)
                val responseSub = client.onReceive.subscribe { response ->
                    responseMessageHandler(response)
                    successCounter++
                    responseBarrier.countDown()
                }
                client.send(message.toByteBuffer().array())
                responseBarrier.await(1000, TimeUnit.MILLISECONDS)
                responseSub.unsubscribe()
            } catch (e: Exception) {
                logger.warn("Could not re-send message to $target", e)
            } finally {
                logger.info("Successfully re-delivered $successCounter out of ${messages.size} messages to $target")
            }
        }
    }

    /**
     * Handler for P2P messages sent back as a result of a request. Typically, these responses have no payloads and serve
     * as an indication of successful receipt on the other end. In case of a session request message, the response will
     * contain information which then needs to be forwarded to the LinkManager
     */
    private fun responseMessageHandler(message: HttpMessage) {
        logger.debug("Processing response message from ${message.source} with status $${message.statusCode}")
        if (HttpResponseStatus.OK == message.statusCode) {
            // response messages should have empty payloads unless they are part of the initial session handshake
            if (message.payload.isNotEmpty()) {
                try {
                    // Attempt to deserialize as an early check. Shouldn't forward unrecognised message types
                    LinkInMessage.fromByteBuffer(ByteBuffer.wrap(message.payload))
                    val record = Record(LINK_IN_TOPIC, "key", message)
                    p2pInPublisher.publish(listOf(record))
                } catch (e: IOException) {
                    logger.warn("Invalid message received. Cannot deserialize")
                    logger.debug(e.stackTraceToString())
                }
            }
        } else {
            logger.warn("Something went wrong with peer processing an outbound message. Peer response status ${message.statusCode}")
        }
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<LinkOutMessage>
        get() = LinkOutMessage::class.java
}