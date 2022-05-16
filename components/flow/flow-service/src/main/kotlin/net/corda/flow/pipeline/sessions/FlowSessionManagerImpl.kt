package net.corda.flow.pipeline.sessions

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.pipeline.sandbox.FlowProtocol
import net.corda.flow.state.FlowCheckpoint
import net.corda.layeredpropertymap.toWire
import net.corda.session.manager.Constants
import net.corda.session.manager.SessionManager
import net.corda.v5.base.types.MemberX500Name
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.ByteBuffer
import java.time.Instant

@Component(service = [FlowSessionManager::class])
class FlowSessionManagerImpl @Activate constructor(
    @Reference(service = SessionManager::class)
    private val sessionManager: SessionManager
) : FlowSessionManager {

    override fun sendInitMessage(
        checkpoint: FlowCheckpoint,
        sessionId: String,
        x500Name: MemberX500Name,
        flowProtocols: List<FlowProtocol>,
        headers: FlowSessionHeaders,
        instant: Instant
    ): SessionState {
        val protocolName = flowProtocols.first().protocol
        val protocolVersions = flowProtocols.map { it.version }
        val payload = SessionInit.newBuilder()
            .setProtocol(protocolName)
            .setVersions(protocolVersions)
            .setFlowId(checkpoint.flowId)
            .setCpiId(checkpoint.flowStartContext.cpiId)
            .setPayload(ByteBuffer.wrap(byteArrayOf()))
            .build()
        val event = SessionEvent.newBuilder()
            .setSessionId(sessionId)
            .setMessageDirection(MessageDirection.OUTBOUND)
            .setTimestamp(instant)
            .setSequenceNum(null)
            .setInitiatingIdentity(checkpoint.flowKey.identity)
            // TODO Need member lookup service to get the holding identity of the peer
            .setInitiatedIdentity(HoldingIdentity(x500Name.toString(), "flow-worker-dev"))
            .setReceivedSequenceNum(0)
            .setOutOfOrderSequenceNums(listOf(0))
            .setHeaders(headers.toWire())
            .setPayload(payload)
            .build()

        return sessionManager.processMessageToSend(
            key = checkpoint.flowId,
            sessionState = null,
            event = event,
            instant = instant
        )
    }

    override fun sendDataMessages(
        checkpoint: FlowCheckpoint,
        sessionToPayload: Map<String, ByteArray>,
        headers: FlowSessionHeaders,
        instant: Instant
    ): List<SessionState> {
        return sessionToPayload.map { (sessionId, payload) ->
            val sessionState = checkpoint.getSessionState(sessionId)
                ?: throw FlowProcessingException("No existing session when trying to send data message")
            val (initiatingIdentity, initiatedIdentity) = getInitiatingAndInitiatedParties(
                sessionState, checkpoint.flowKey.identity
            )
            sessionManager.processMessageToSend(
                key = checkpoint.flowId,
                sessionState = sessionState,
                event = SessionEvent.newBuilder()
                    .setSessionId(sessionId)
                    .setMessageDirection(MessageDirection.OUTBOUND)
                    .setTimestamp(instant)
                    .setInitiatingIdentity(initiatingIdentity)
                    .setInitiatedIdentity(initiatedIdentity)
                    .setSequenceNum(null)
                    .setReceivedSequenceNum(0)
                    .setOutOfOrderSequenceNums(listOf(0))
                    .setHeaders(headers.toWire())
                    .setPayload(SessionData(ByteBuffer.wrap(payload)))
                    .build(),
                instant = instant
            )
        }
    }

    override fun sendCloseMessages(
        checkpoint: FlowCheckpoint,
        sessionIds: List<String>,
        headers: FlowSessionHeaders,
        instant: Instant
    ): List<SessionState> {
        return sessionIds.map { sessionId ->
            val sessionState = checkpoint.getSessionState(sessionId)
                ?: throw FlowProcessingException("No existing session when trying to send close message")
            val (initiatingIdentity, initiatedIdentity) = getInitiatingAndInitiatedParties(
                sessionState, checkpoint.flowKey.identity
            )
            sessionManager.processMessageToSend(
                key = checkpoint.flowId,
                sessionState = sessionState,
                event = SessionEvent.newBuilder()
                    .setSessionId(sessionId)
                    .setMessageDirection(MessageDirection.OUTBOUND)
                    .setTimestamp(instant)
                    .setInitiatingIdentity(initiatingIdentity)
                    .setInitiatedIdentity(initiatedIdentity)
                    .setSequenceNum(null)
                    .setReceivedSequenceNum(0)
                    .setOutOfOrderSequenceNums(listOf(0))
                    .setHeaders(headers.toWire())
                    .setPayload(SessionClose())
                    .build(),
                instant = instant
            )
        }
    }

    override fun getReceivedEvents(
        checkpoint: FlowCheckpoint,
        sessionIds: List<String>
    ): List<Pair<SessionState, SessionEvent>> {
        return sessionIds.mapNotNull { sessionId ->
            val sessionState = checkpoint.getSessionState(sessionId) ?: throw FlowProcessingException("Session doesn't exist")
            sessionManager.getNextReceivedEvent(sessionState)?.let { sessionState to it }
        }
    }

    override fun acknowledgeReceivedEvents(eventsToAcknowledge: List<Pair<SessionState, SessionEvent>>) {
        for ((sessionState, eventToAcknowledgeProcessingOf) in eventsToAcknowledge) {
            sessionManager.acknowledgeReceivedEvent(sessionState, eventToAcknowledgeProcessingOf.sequenceNum)
        }
    }

    override fun hasReceivedEvents(checkpoint: FlowCheckpoint, sessionIds: List<String>): Boolean {
        return getReceivedEvents(checkpoint, sessionIds).size == sessionIds.size
    }

    override fun doAllSessionsHaveStatus(
        checkpoint: FlowCheckpoint,
        sessionIds: List<String>,
        status: SessionStateType
    ): Boolean {
        return sessionIds
            .mapNotNull { sessionId -> checkpoint.getSessionState(sessionId) }
            .map { sessionState -> sessionState.status }
            .all { sessionStatus -> sessionStatus == status }
    }

    private fun getInitiatingAndInitiatedParties(
        sessionState: SessionState?,
        checkpointIdentity: HoldingIdentity
    ): Pair<HoldingIdentity?, HoldingIdentity?> {
        return when {
            sessionState == null -> Pair(null, null)
            sessionState.sessionId.contains(Constants.INITIATED_SESSION_ID_SUFFIX) -> {
                Pair(sessionState.counterpartyIdentity, checkpointIdentity)
            }
            else -> Pair(checkpointIdentity, sessionState.counterpartyIdentity)
        }
    }
}