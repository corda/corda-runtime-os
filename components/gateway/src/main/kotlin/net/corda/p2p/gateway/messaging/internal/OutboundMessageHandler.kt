package net.corda.p2p.gateway.messaging.internal

import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.LifeCycle
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

/**
 * This class manages the publishing and consuming of messages to/from other internal Corda components.
 * It can spawn multiple subscribers based on how many partitions are used for the P2P.OUT topic. There is only one
 * producer/publisher for each of the inbound topics.
 *
 *
 */
class OutboundMessageHandler : EventLogProcessor<String, ByteBuffer>, LifeCycle {

    private val logger = LoggerFactory.getLogger(OutboundMessageHandler::class.java)

    override fun onNext(events: List<EventLogRecord<String, ByteBuffer>>): List<Record<*, *>> {
        TODO("Not yet implemented")
    }

    override val keyClass: Class<String>
        get() = TODO("Not yet implemented")
    override val valueClass: Class<ByteBuffer>
        get() = TODO("Not yet implemented")

    override fun start() {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

}