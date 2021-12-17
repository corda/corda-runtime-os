package net.corda.session.manager.impl.processor.helper

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.MessageDirection
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionError
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Companion.FLOW_MAPPER_EVENT_TOPIC
import java.time.Clock

fun generateAckRecord(sequenceNum: Int, sessionId: String, clock: Clock): Record<String, FlowMapperEvent> {
    return Record(
        FLOW_MAPPER_EVENT_TOPIC, sessionId, FlowMapperEvent(
            MessageDirection.OUTBOUND, SessionEvent(
                clock.millis(), sessionId, null, SessionAck(sequenceNum)
            )
        )
    )
}

fun generateErrorEvent(sessionId: String, errorMessage: String, errorType: String, clock: Clock):
        Record<String, FlowMapperEvent> {
    val errorEnvelope = ExceptionEnvelope(errorType, errorMessage)
    val sessionError = SessionError(errorEnvelope)
    return Record(
        FLOW_MAPPER_EVENT_TOPIC, sessionId, FlowMapperEvent(
            MessageDirection.OUTBOUND, SessionEvent(
                clock.millis(), sessionId, null, sessionError
            )
        )
    )
}
