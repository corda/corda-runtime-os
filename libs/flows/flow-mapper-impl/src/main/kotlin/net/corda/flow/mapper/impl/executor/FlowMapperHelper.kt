package net.corda.flow.mapper.impl.executor

import java.nio.ByteBuffer
import java.util.UUID
import net.corda.data.CordaAvroSerializer
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.schema.Schemas
import net.corda.schema.configuration.FlowConfig.SESSION_P2P_TTL
import net.corda.session.manager.Constants.Companion.FLOW_SESSION_SUBSYSTEM
import net.corda.session.manager.Constants.Companion.INITIATED_SESSION_ID_SUFFIX
import java.time.Instant

/**
 * Generate and return random ID for flowId
 * @return a new flow id
 */
fun generateFlowId(): String {
    return UUID.randomUUID().toString()
}

/**
 * Inbound records should be directed to the flow event topic.
 * Outbound records should be directed to the p2p out topic.
 * @return the output topic based on [messageDirection].
 */
fun getSessionEventOutputTopic(messageDirection: MessageDirection): String {
    return if (messageDirection == MessageDirection.INBOUND) {
        Schemas.Flow.FLOW_EVENT_TOPIC
    } else {
        Schemas.P2P.P2P_OUT_TOPIC
    }
}

/**
 * Get the source and destination holding identity from the [sessionEvent].
 * @param sessionEvent Session event to extract identities from
 * @return Source and destination identities for a SessionEvent message.
 */
private fun getSourceAndDestinationIdentity(sessionEvent: SessionEvent): Pair<HoldingIdentity, HoldingIdentity> {
    return if (sessionEvent.sessionId.contains(INITIATED_SESSION_ID_SUFFIX)) {
        Pair(sessionEvent.initiatedIdentity, sessionEvent.initiatingIdentity)
    } else {
        Pair(sessionEvent.initiatingIdentity, sessionEvent.initiatedIdentity)
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

