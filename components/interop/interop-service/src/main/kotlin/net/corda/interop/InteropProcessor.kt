package net.corda.interop

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Instant
import java.util.*

//Based on FlowP2PFilter
@Suppress("Unused")
class InteropProcessor (cordaAvroSerializationFactory: CordaAvroSerializationFactory) :
    DurableProcessor<String, AppMessage> {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val SUBSYSTEM = "interop"
    }
    private val cordaAvroDeserializer: CordaAvroDeserializer<SessionEvent> = cordaAvroSerializationFactory.createAvroDeserializer({},
        SessionEvent::class.java)

    private val cordaAvroSerializer: CordaAvroSerializer<SessionEvent> = cordaAvroSerializationFactory.createAvroSerializer({})

    override fun onNext(
        events: List<Record<String, AppMessage>>
    ): List<Record<*, *>> {
        val outputEvents = mutableListOf<Record<*, *>>()
        events.forEach { appMessage ->
            val authMessage = appMessage.value?.message
            if (authMessage != null && authMessage is AuthenticatedMessage && authMessage.header.subsystem == SUBSYSTEM) {
                getOutputRecord(authMessage.payload, appMessage.key)?.let { outputRecord ->
                    outputEvents.add(outputRecord)
                }
            }
        }
        return outputEvents
    }

    // Returns an OUTBOUND message to P2P layer, in the future it will pass a message to FlowProcessor
    private fun getOutputRecord(
        payload: ByteBuffer,
        key: String
    ) : Record<String, AppMessage>? {
        val sessionEvent = cordaAvroDeserializer.deserialize(payload.array())
        logger.info ( "Processing message from p2p.in with subsystem $SUBSYSTEM. Key: $key, Event: $sessionEvent")

        return if (sessionEvent != null) {
            sessionEvent.messageDirection = MessageDirection.OUTBOUND //
            val sessionId = key
            sessionEvent.sessionId = sessionId
            //Record(Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC, sessionId, FlowMapperEvent(sessionEvent))
            // this would be a way to push message downstream, once we have connection with FlowProcessor
            Record(Schemas.P2P.P2P_OUT_TOPIC, sessionId, generateAppMessage(sessionEvent, cordaAvroSerializer))
        } else {
            null
        }
    }

    override val keyClass = String::class.java
    override val valueClass = AppMessage::class.java

    private fun generateAppMessage(
        sessionEvent: SessionEvent,
        sessionEventSerializer: CordaAvroSerializer<SessionEvent>
    ): AppMessage {
        val sourceIdentity = sessionEvent.initiatedIdentity
        val destinationIdentity = sessionEvent.initiatingIdentity
        val header = AuthenticatedMessageHeader(
            destinationIdentity,
            sourceIdentity,
            Instant.ofEpochMilli(sessionEvent.timestamp.toEpochMilli()),
            sessionEvent.sessionId + "-" + UUID.randomUUID(),
            "",
            SUBSYSTEM
        )
        return AppMessage(AuthenticatedMessage(header, ByteBuffer.wrap(sessionEventSerializer.serialize(sessionEvent))))
    }
}
