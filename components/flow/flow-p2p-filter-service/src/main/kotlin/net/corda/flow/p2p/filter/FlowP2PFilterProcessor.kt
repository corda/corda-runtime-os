package net.corda.flow.p2p.filter

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_SESSION_IN
import net.corda.session.manager.Constants.Companion.FLOW_SESSION_SUBSYSTEM
import net.corda.session.manager.Constants.Companion.INITIATED_SESSION_ID_SUFFIX
import net.corda.tracing.traceEventProcessingNullableSingle
import net.corda.utilities.debug
import org.slf4j.LoggerFactory

/**
 * Processes events from the P2P.in topic.
 * If events have a subsystem of "flowSession", payloads are parsed into SessionEvents.
 * SessionEvent sessionId's are flipped to that of the counterparty, as well as the event key sessionId.
 * Messages are forwarded to the flow.mapper.session.in topic
 */
class FlowP2PFilterProcessor(
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory
) :
    DurableProcessor<String, AppMessage> {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    override fun onNext(
        events: List<Record<String, AppMessage>>
    ): List<Record<*, *>> {
        val cordaAvroDeserializer = cordaAvroSerializationFactory.createAvroDeserializer(
            {},
            SessionEvent::class.java
        )
        return events.mapNotNull { appMessage ->
            val authMessage = appMessage.value?.message as? AuthenticatedMessage
            if (authMessage?.header?.subsystem == FLOW_SESSION_SUBSYSTEM) {
                val sessionEvent = cordaAvroDeserializer.deserialize(authMessage.payload.array())
                traceEventProcessingNullableSingle(appMessage, "Flow P2P Filter Event") {
                    getOutputRecord(sessionEvent, authMessage.header.traceId)
                }
            } else {
                null
            }
        }
    }

    /**
     * Generate an output record for session events received from a counterparty to be passed to the Flow Mapper.
     * @param payload Authenticated message payload. Expected to be a SessionEvent
     * @param traceId Trace the event arrived on. Expected to be counterparties sessionId
     * @return Record to be sent to the Flow Mapper with sessionId set to that of the receiving party and a message direction of INBOUND.
     */
    private fun getOutputRecord(
        sessionEvent: SessionEvent?,
        traceId: String
    ): Record<String, FlowMapperEvent>? {
        logger.debug { "Processing message from p2p.in with subsystem $FLOW_SESSION_SUBSYSTEM. traceId: $traceId, Event: $sessionEvent" }

        return if (sessionEvent != null) {
            sessionEvent.messageDirection = MessageDirection.INBOUND
            val sessionId = toggleSessionId(traceId)
            sessionEvent.sessionId = sessionId
            Record(FLOW_MAPPER_SESSION_IN, sessionId, FlowMapperEvent(sessionEvent))
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
