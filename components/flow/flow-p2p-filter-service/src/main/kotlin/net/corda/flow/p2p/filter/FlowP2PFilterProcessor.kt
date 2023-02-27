package net.corda.flow.p2p.filter

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC
import net.corda.session.manager.Constants.Companion.FLOW_SESSION_SUBSYSTEM
import net.corda.session.manager.Constants.Companion.INITIATED_SESSION_ID_SUFFIX
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

/**
 * Processes events from the P2P.in topic.
 * If events have a subsystem of "flowSession", payloads are parsed into SessionEvents.
 * SessionEvent sessionId's are flipped to that of the counterparty, as well as the event key sessionId.
 * Messages are forwarded to the flow.mapper.event topic
 */
class FlowP2PFilterProcessor(cordaAvroSerializationFactory: CordaAvroSerializationFactory) : DurableProcessor<String, AppMessage> {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    private val cordaAvroDeserializer: CordaAvroDeserializer<SessionEvent> = cordaAvroSerializationFactory.createAvroDeserializer({},
        SessionEvent::class.java)

    override fun onNext(
        events: List<Record<String, AppMessage>>
    ): List<Record<*, *>> {
        val outputEvents = mutableListOf<Record<*, *>>()
        events.forEach { appMessage ->
            val authMessage = appMessage.value?.message
            if (authMessage != null && authMessage is AuthenticatedMessage && authMessage.header.subsystem == FLOW_SESSION_SUBSYSTEM) {
                getOutputRecord(authMessage.payload, appMessage.key)?.let { outputRecord ->
                    outputEvents.add(outputRecord)
                }
            }
        }

        return outputEvents
    }

    /**
     * Generate an output record for session events received from a counterparty to be passed to the Flow Mapper.
     * @param payload Authenticated message payload. Expected to be a SessionEvent
     * @param key Key the event arrived on. Expected to be counterparties sessionId
     * @return Record to be sent to the Flow Mapper with sessionId set to that of the receiving party and a message direction of INBOUND.
     */
    private fun getOutputRecord(
        payload: ByteBuffer,
        key: String
    ) : Record<String, FlowMapperEvent>? {
        val sessionEvent = cordaAvroDeserializer.deserialize(payload.array())
        logger.debug { "Processing message from p2p.in with subsystem $FLOW_SESSION_SUBSYSTEM. Key: $key, Event: $sessionEvent"}

        return if (sessionEvent != null) {
            sessionEvent.messageDirection = MessageDirection.INBOUND
            val sessionId = toggleSessionId(key)
            sessionEvent.sessionId = sessionId
            Record(FLOW_MAPPER_EVENT_TOPIC, sessionId, FlowMapperEvent(sessionEvent))
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
    private fun toggleSessionId(sessionId: String): String {
        return if (sessionId.endsWith(INITIATED_SESSION_ID_SUFFIX)) {
            sessionId.removeSuffix(INITIATED_SESSION_ID_SUFFIX)
        } else {
            "$sessionId$INITIATED_SESSION_ID_SUFFIX"
        }
    }
}
