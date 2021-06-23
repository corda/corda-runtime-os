package net.corda.p2p.gateway.messaging.internal

import io.netty.channel.ConnectTimeoutException
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.p2p.gateway.messaging.ConnectionManager
import net.corda.v5.base.util.NetworkHostAndPort
import org.slf4j.LoggerFactory

/**
 * This is an implementation of an [EventLogProcessor] used to consume messages from a P2P message subscription. The received
 * events are processed and fed into the HTTP pipeline. No records will be produced by this processor as a result.
 */
class OutboundMessageHandler(private val connectionPool: ConnectionManager) : EventLogProcessor<String, String> {

    private val logger = LoggerFactory.getLogger(OutboundMessageHandler::class.java)

    @Suppress("TooGenericExceptionCaught")
    override fun onNext(events: List<EventLogRecord<String, String>>): List<Record<*, *>> {
        events.forEach { evt ->
            logger.info("Processing event")
            //TODO: until messaging library is ready, will use some simple Strings which contain destination and random payload
            //Format: "address;payload" Example: "localhost:1000;localhost:10001;PING"
            val peerMessage = evt.value
            //Get peer address from message header
            val destination = getDestination(peerMessage)
            try {
                connectionPool.acquire(destination).send(peerMessage.toByteArray())
            } catch (e: ConnectTimeoutException) {
                logger.warn(e.message)
            } catch (e: Exception) {
                logger.error("Unexpected error\n${e.message}")
            }
        }

        return emptyList()
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<String>
        get() = String::class.java

    private fun getDestination(msg: String): NetworkHostAndPort {
        return NetworkHostAndPort.parse(msg.split(";")[1])
    }
}