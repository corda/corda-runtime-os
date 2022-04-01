package net.corda.flow.p2p.filter

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.schema.Schemas.Flow.Companion.FLOW_MAPPER_EVENT_TOPIC
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.nio.ByteBuffer

/**
 * Processes events from the P2P.in topic.
 * If events have a subsystem of "flowSession", payloads are parsed into SessionEvents.
 * SessionEvent sessionId's are flipped to that of the counterparty, as well as the event key sessionId.
 * Messages are forwarded to the flow.mapper.event topic
 */
class FlowP2PFilterProcessor(cordaAvroSerializationFactory: CordaAvroSerializationFactory) : DurableProcessor<String, AppMessage> {

    companion object {
        private val logger = contextLogger()
        private const val FLOW_SESSION_SUBSYSTEM = "flowSession"
        const val INITIATED_SESSION_ID_SUFFIX = "-INITIATED"
    }
    private val cordaAvroDeserializer: CordaAvroDeserializer<FlowMapperEvent> = cordaAvroSerializationFactory.createAvroDeserializer({},
        FlowMapperEvent::class.java)

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
     * @param payload Authenticated message payload. Expected to be a FlowMapperEvent with a payload of SessionEvent
     * @param key Key the event arrived on. Expected to be counterparties sessionId
     * @return Record to be sent to the Flow MApper with sessionId set to that of the receiving party and a message direction of INBOUND.
     */
    private fun getOutputRecord(
        payload: ByteBuffer,
        key: String
    ) : Record<String, FlowMapperEvent>? {
        val flowMapperEvent = cordaAvroDeserializer.deserialize(payload.array())
        val flowMapperEventPayload = flowMapperEvent?.payload
        logger.debug { "Processing message from p2p.in with subsystem $FLOW_SESSION_SUBSYSTEM. Key: $key, Event: $flowMapperEvent"}

        return if (flowMapperEventPayload is SessionEvent) {
            flowMapperEventPayload.messageDirection = MessageDirection.INBOUND
            val sessionId = toggleSessionId(key)
            flowMapperEventPayload.sessionId = sessionId
            Record(FLOW_MAPPER_EVENT_TOPIC, sessionId, flowMapperEvent)
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
