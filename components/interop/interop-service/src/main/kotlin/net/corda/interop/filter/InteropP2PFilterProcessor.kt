package net.corda.interop.filter

import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.FLOW_INTEROP_EVENT_TOPIC
import org.slf4j.LoggerFactory

/**
 * Processes events from the P2P.in topic.
 * If events have a subsystem of "interop", they are forwarded to the `interop.event` topic.
 */
class InteropP2PFilterProcessor: DurableProcessor<String, AppMessage> {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val SUBSYSTEM = "interop"
    }

    override fun onNext(
        events: List<Record<String, AppMessage>>
    ): List<Record<*, *>> = events.mapNotNull { (_, key, value) ->
        val authMessage = value?.message
        if (authMessage == null ||
            authMessage !is AuthenticatedMessage ||
            authMessage.header.subsystem != SUBSYSTEM
        ) return@mapNotNull null

        logger.info("Processing message from p2p.in with subsystem $SUBSYSTEM. Key: $key." )

        return listOf(Record(FLOW_INTEROP_EVENT_TOPIC, key, authMessage))

    }

    override val keyClass = String::class.java
    override val valueClass = AppMessage::class.java
}
