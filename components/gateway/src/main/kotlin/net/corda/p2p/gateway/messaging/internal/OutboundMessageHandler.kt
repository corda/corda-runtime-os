package net.corda.p2p.gateway.messaging.internal

import io.netty.channel.ConnectTimeoutException
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.gateway.messaging.ConnectionManager
import net.corda.v5.base.util.NetworkHostAndPort
import org.slf4j.LoggerFactory

/**
 * This is an implementation of an [EventLogProcessor] used to consume messages from a P2P message subscription. The received
 * events are processed and fed into the HTTP pipeline. No records will be produced by this processor as a result.
 */
class OutboundMessageHandler(private val connectionPool: ConnectionManager) : EventLogProcessor<String, LinkOutMessage> {

    private val logger = LoggerFactory.getLogger(OutboundMessageHandler::class.java)

    @Suppress("TooGenericExceptionCaught")
    override fun onNext(events: List<EventLogRecord<String, LinkOutMessage>>): List<Record<*, *>> {
        events.forEach { evt ->
            logger.info("Processing P2P outbound message")
            val peerMessage = evt.value
            try {
                val destination = NetworkHostAndPort.parse(peerMessage.header.address)
                connectionPool.acquire(destination).send(peerMessage.toByteBuffer().array())
            } catch (e: ConnectTimeoutException) {
                logger.warn(e.message)
            } catch (e: IllegalArgumentException) {
              logger.warn("Could not read the destination address")
            } catch (e: Exception) {
                logger.error("Unexpected error\n${e.message}")
            }
        }

        return emptyList()
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<LinkOutMessage>
        get() = LinkOutMessage::class.java
}