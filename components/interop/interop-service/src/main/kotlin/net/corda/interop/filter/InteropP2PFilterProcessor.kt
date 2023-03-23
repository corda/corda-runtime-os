package net.corda.interop.filter

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.FLOW_INTEROP_EVENT_TOPIC
import net.corda.session.manager.Constants
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

/**
 * Processes events from the P2P.in topic.
 * If events have a subsystem of "interop", they are forwarded to the `interop.event` topic.
 */
class InteropP2PFilterProcessor(cordaAvroSerializationFactory: CordaAvroSerializationFactory): DurableProcessor<String, AppMessage> {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val SUBSYSTEM = "interop"
    }
    private val cordaAvroDeserializer: CordaAvroDeserializer<SessionEvent> = cordaAvroSerializationFactory.createAvroDeserializer({},
        SessionEvent::class.java)

    override fun onNext(
        events: List<Record<String, AppMessage>>
    ): List<Record<*, *>> = events.mapNotNull { (_, key, value) ->
        val authMessage = value?.message
        if (authMessage == null ||
            authMessage !is AuthenticatedMessage ||
            authMessage.header.subsystem != SUBSYSTEM
        ) return@mapNotNull null

        logger.info("Processing message from p2p.in with subsystem $SUBSYSTEM. Key: $key." )

        getOutputRecord(authMessage.payload, key)
    }

    private fun getOutputRecord(
        payload: ByteBuffer,
        key: String
    ) : Record<String, FlowMapperEvent>? {
        val sessionEvent = cordaAvroDeserializer.deserialize(payload.array())
        logger.info("Processing message from p2p.in with subsystem ${Constants.FLOW_SESSION_SUBSYSTEM}. Key: $key, Event: $sessionEvent")

        return if (sessionEvent != null) {
            sessionEvent.messageDirection = MessageDirection.INBOUND
            val sessionId = toggleSessionId(key)
            sessionEvent.sessionId = sessionId
            Record(FLOW_INTEROP_EVENT_TOPIC, sessionId, FlowMapperEvent(sessionEvent))
        } else {
            null
        }
    }

    override val keyClass = String::class.java
    override val valueClass = AppMessage::class.java

    /**
     * Toggle the [sessionId] to that of the other party and return it.
     * Initiating party sessionId will be a random UUID.
     * Initiated party sessionId will be the initiating party session id with a suffix of "-INITIATED" added.
     * @return the toggled session id
     */
    private fun toggleSessionId(sessionId: String): String =
        if (sessionId.endsWith(Constants.INITIATED_SESSION_ID_SUFFIX)) {
            sessionId.removeSuffix(Constants.INITIATED_SESSION_ID_SUFFIX)
        } else {
            "$sessionId${Constants.INITIATED_SESSION_ID_SUFFIX}"
        }
}
