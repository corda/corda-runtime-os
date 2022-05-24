package net.corda.flow.pipeline.sessions.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.pipeline.sessions.FlowSessionMissingException
import net.corda.flow.state.FlowCheckpoint
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
        protocolName: String,
        protocolVersions: List<Int>,
        instant: Instant
    ): SessionState {
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
            .setInitiatingIdentity(checkpoint.holdingIdentity)
            .setInitiatedIdentity(HoldingIdentity(x500Name.toString(), checkpoint.holdingIdentity.groupId))
            .setReceivedSequenceNum(0)
            .setOutOfOrderSequenceNums(listOf(0))
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
        instant: Instant
    ): List<SessionState> {
        return sessionToPayload.map { (sessionId, payload) ->
            sendSessionMessageToExistingSession(
                checkpoint,
                sessionId,
                payload = SessionData(ByteBuffer.wrap(payload)),
                instant
            )
        }
    }

    override fun sendCloseMessages(
        checkpoint: FlowCheckpoint,
        sessionIds: List<String>,
        instant: Instant
    ): List<SessionState> {
        return sessionIds.map { sessionId ->
            sendSessionMessageToExistingSession(
                checkpoint,
                sessionId,
                payload = SessionClose(),
                instant
            )
        }
    }

    override fun sendErrorMessages(
        checkpoint: FlowCheckpoint,
        sessionIds: List<String>,
        throwable: Throwable,
        instant: Instant
    ): List<SessionState> {
        return sessionIds.map { sessionId ->
            sendSessionMessageToExistingSession(
                checkpoint,
                sessionId,
                payload = SessionError(ExceptionEnvelope(throwable::class.qualifiedName, throwable.message)),
                instant
            )
        }
    }

    override fun getReceivedEvents(
        checkpoint: FlowCheckpoint,
        sessionIds: List<String>
    ): List<Pair<SessionState, SessionEvent>> {
        return sessionIds.mapNotNull { sessionId ->
            val sessionState = getAndRequireSession(checkpoint, sessionId)
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

    override fun getSessionsWithStatus(checkpoint: FlowCheckpoint, sessionIds: List<String>, status: SessionStateType): List<SessionState> {
        return sessionIds
            .map { sessionId -> getAndRequireSession(checkpoint, sessionId) }
            .filter { sessionState -> sessionState.status == status }
    }

    override fun doAllSessionsHaveStatus(
        checkpoint: FlowCheckpoint,
        sessionIds: List<String>,
        status: SessionStateType
    ): Boolean {
        return getSessionsWithStatus(checkpoint, sessionIds, status).size == sessionIds.size
    }

    private fun sendSessionMessageToExistingSession(
        checkpoint: FlowCheckpoint,
        sessionId: String,
        payload: Any,
        instant: Instant
    ): SessionState {
        val sessionState = getAndRequireSession(checkpoint, sessionId)
        val (initiatingIdentity, initiatedIdentity) = getInitiatingAndInitiatedParties(
            sessionState, checkpoint.holdingIdentity
        )
        return sessionManager.processMessageToSend(
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
                .setPayload(payload)
                .build(),
            instant = instant
        )
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

    private fun getAndRequireSession(checkpoint: FlowCheckpoint, sessionId: String): SessionState {
        return checkpoint.getSessionState(sessionId) ?: throw FlowSessionMissingException(
            "Session: $sessionId does not exist when executing session operation that requires an existing session"
        )
    }
}