package net.corda.session.manager.impl.processor.helper

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionError
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.Companion.FLOW_MAPPER_EVENT_TOPIC
import java.time.Instant

fun generateAckRecord(sequenceNum: Int, sessionId: String, instant: Instant): Record<String, FlowMapperEvent> {
    return Record(
        FLOW_MAPPER_EVENT_TOPIC, sessionId, FlowMapperEvent(
            SessionEvent(
                MessageDirection.OUTBOUND, instant.toEpochMilli(), sessionId, null, SessionAck(sequenceNum)
            )
        )
    )
}

fun generateOutBoundRecord(payload: SessionEvent, sessionId: String): Record<String, FlowMapperEvent> {
    return Record(FLOW_MAPPER_EVENT_TOPIC, sessionId, FlowMapperEvent(payload))
}

fun generateErrorEvent(sessionId: String, errorMessage: String, errorType: String, instant: Instant):
        Record<String, FlowMapperEvent> {
    val errorEnvelope = ExceptionEnvelope(errorType, errorMessage)
    val sessionError = SessionError(errorEnvelope)
    return Record(
        FLOW_MAPPER_EVENT_TOPIC, sessionId, FlowMapperEvent(
            SessionEvent(
                MessageDirection.OUTBOUND,
                instant.toEpochMilli(), sessionId, null, sessionError
            )
        )
    )
}
