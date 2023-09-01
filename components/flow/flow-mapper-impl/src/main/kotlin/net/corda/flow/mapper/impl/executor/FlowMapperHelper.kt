package net.corda.flow.mapper.impl.executor

import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.flow.utils.isInitiatedParty
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig.SESSION_P2P_TTL
import net.corda.session.manager.Constants.Companion.FLOW_SESSION_SUBSYSTEM
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID

/**
 * Generate and return random ID for flowId
 * @return a new flow id
 */
fun generateFlowId(): String {
    return UUID.randomUUID().toString()
}

/**
 * Get the source and destination holding identity from the [sessionEvent].
 * @param sessionEvent Session event to extract identities from
 * @return Source and destination identities for a SessionEvent message.
 */
fun getSourceAndDestinationIdentity(sessionEvent: SessionEvent): Pair<HoldingIdentity, HoldingIdentity> {
    return if (isInitiatedParty(sessionEvent)) {
        Pair(sessionEvent.initiatedIdentity, sessionEvent.initiatingIdentity)
    } else {
        Pair(sessionEvent.initiatingIdentity, sessionEvent.initiatedIdentity)
    }
}

/**
 * Get the destination holding identity from the [sessionEvent].
 * @param sessionEvent Session event to extract identity from
 * @return destination identity for a SessionEvent message.
 */
fun getDestinationIdentity(sessionEvent: SessionEvent): HoldingIdentity {
    return if (isInitiatedParty(sessionEvent)) {
        sessionEvent.initiatedIdentity
    } else {
        sessionEvent.initiatingIdentity
    }
}

/**
 * Generate an AppMessage to send to the P2P.out topic.
 * @param sessionEvent Flow event to send
 * @param sessionEventSerializer Serializer for session events
 * @param flowConfig config
 * @return AppMessage to send to the P2P.out topic with the serialized session event as payload
 */
fun generateAppMessage(
    sessionEvent: SessionEvent,
    sessionEventSerializer: CordaAvroSerializer<SessionEvent>,
    flowConfig: SmartConfig
): AppMessage {
    val (sourceIdentity, destinationIdentity) = getSourceAndDestinationIdentity(sessionEvent)
    val header = AuthenticatedMessageHeader(
        destinationIdentity,
        sourceIdentity,
        Instant.ofEpochMilli(sessionEvent.timestamp.toEpochMilli() + flowConfig.getLong(SESSION_P2P_TTL)),
        sessionEvent.sessionId + "-" + UUID.randomUUID(),
        "",
        FLOW_SESSION_SUBSYSTEM,
        MembershipStatusFilter.ACTIVE
    )
    return AppMessage(AuthenticatedMessage(header, ByteBuffer.wrap(sessionEventSerializer.serialize(sessionEvent))))
}

/**
 * Creates [Record] for P2P.out topic.
 * @param sessionEvent Flow event to send
 * @param payload Flow event payload
 * @param instant Instant
 * @param sessionEventSerializer Serializer for session events
 * @param flowConfig config
 * @param outputTopic topic where the record should be sent
 */
@Suppress("LongParameterList")
fun createOutboundRecord(
    sessionEvent: SessionEvent,
    payload: Any,
    instant: Instant,
    sessionEventSerializer: CordaAvroSerializer<SessionEvent>,
    flowConfig: SmartConfig,
    outputTopic: String
) : Record<*, *> {
    val sessionId = sessionEvent.sessionId
    return Record(
        outputTopic, sessionId, generateAppMessage(
            SessionEvent(
                MessageDirection.OUTBOUND,
                instant,
                sessionId,
                null,
                sessionEvent.initiatingIdentity,
                sessionEvent.initiatedIdentity,
                payload,
                sessionEvent.contextSessionProperties
            ),
            sessionEventSerializer,
            flowConfig
        )
    )
}
