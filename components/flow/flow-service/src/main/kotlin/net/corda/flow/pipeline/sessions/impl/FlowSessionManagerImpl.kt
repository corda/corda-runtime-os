package net.corda.flow.pipeline.sessions.impl

import java.nio.ByteBuffer
import java.time.Instant
import net.corda.data.ExceptionEnvelope
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionConfirm
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.pipeline.sessions.FlowSessionStateException
import net.corda.flow.state.FlowCheckpoint
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
import net.corda.session.manager.Constants
import net.corda.session.manager.SessionManager
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("TooManyFunctions")
@Component(service = [FlowSessionManager::class])
class FlowSessionManagerImpl @Activate constructor(
    @Reference(service = SessionManager::class)
    private val sessionManager: SessionManager,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory,
) : FlowSessionManager {

    override fun getSessionErrorEventRecords(checkpoint: FlowCheckpoint, flowConfig: SmartConfig, instant: Instant):
            List<Record<*, FlowMapperEvent>> {
        return checkpoint.sessions
            .filter { it.status == SessionStateType.ERROR }
            .map { sessionState ->
                sessionManager.getMessagesToSend(
                    sessionState,
                    instant,
                    flowConfig,
                    checkpoint.flowKey.identity
                )
            }
            .flatMap { (_, events) -> events }
            .map { event -> flowRecordFactory.createFlowMapperEventRecord(event.sessionId, event) }
    }

    override fun sendInitMessage(
        checkpoint: FlowCheckpoint,
        sessionId: String,
        x500Name: MemberX500Name,
        contextUserProperties: KeyValuePairList,
        contextPlatformProperties: KeyValuePairList,
        sessionProperties: KeyValuePairList,
        instant: Instant
    ): SessionState {
        val payload = SessionInit.newBuilder()
            .setFlowId(checkpoint.flowId)
            .setCpiId(checkpoint.flowStartContext.cpiId)
            .setPayload(ByteBuffer.wrap(byteArrayOf()))
            .setContextPlatformProperties(contextPlatformProperties)
            .setContextUserProperties(contextUserProperties)
            .setContextSessionProperties(sessionProperties)
            .build()
        val event = SessionEvent.newBuilder()
            .setSessionId(sessionId)
            .setMessageDirection(MessageDirection.OUTBOUND)
            .setTimestamp(instant)
            .setSequenceNum(null)
            .setInitiatingIdentity(checkpoint.holdingIdentity.toAvro())
            .setInitiatedIdentity(HoldingIdentity(x500Name.toString(), checkpoint.holdingIdentity.groupId))
            .setReceivedSequenceNum(0)
            .setOutOfOrderSequenceNums(listOf(0))
            .setPayload(payload)
            .build()

        return sessionManager.processMessageToSend(
            key = checkpoint.flowId,
            sessionState = null,
            event = event,
            instant = instant,
            maxMsgSize = checkpoint.maxMessageSize
        )
    }

    override fun sendConfirmMessage(
        checkpoint: FlowCheckpoint,
        sessionId: String,
        contextSessionProperties: KeyValuePairList,
        instant: Instant,
    ): SessionState {
        return sendSessionMessageToExistingSession(
            checkpoint,
            sessionId,
            payload = SessionConfirm(contextSessionProperties),
            instant
        )
    }

    override fun sendDataMessages(
        checkpoint: FlowCheckpoint,
        sessionToPayload: Map<String, ByteArray>,
        instant: Instant,
    ): List<SessionState> {
        validateSessionStates(checkpoint, sessionToPayload.keys, Operation.SENDING)
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
        val errorMessage = throwable.message ?: "No exception message provided."
        return sessionIds.map { sessionId ->
            sendSessionMessageToExistingSession(
                checkpoint,
                sessionId,
                payload = SessionError(ExceptionEnvelope(throwable::class.qualifiedName, errorMessage)),
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

    override fun getSessionsWithNextMessageClose(
        checkpoint: FlowCheckpoint,
        sessionIds: List<String>
    ): List<SessionState> {
        return sessionIds.mapNotNull { sessionId ->
            val sessionState = getAndRequireSession(checkpoint, sessionId)
            val receivedEventsState = sessionState.receivedEventsState
            val lastProcessedSequenceNum = receivedEventsState.lastProcessedSequenceNum
            receivedEventsState.undeliveredMessages.firstOrNull()?.let { message ->
                if (message.sequenceNum <= lastProcessedSequenceNum && message.payload is SessionClose) {
                    sessionState
                } else null
            }
        }
    }

    override fun acknowledgeReceivedEvents(eventsToAcknowledge: List<Pair<SessionState, SessionEvent>>) {
        for ((sessionState, eventToAcknowledgeProcessingOf) in eventsToAcknowledge) {
            sessionManager.acknowledgeReceivedEvent(sessionState, eventToAcknowledgeProcessingOf.sequenceNum)
        }
    }

    override fun hasReceivedEvents(checkpoint: FlowCheckpoint, sessionIds: List<String>): Boolean {
        validateSessionStates(checkpoint, sessionIds, Operation.RECEIVING)
        return getReceivedEvents(checkpoint, sessionIds).size == sessionIds.size
    }

    override fun getSessionsWithStatus(
        checkpoint: FlowCheckpoint,
        sessionIds: List<String>,
        status: SessionStateType
    ): List<SessionState> {
        return sessionIds
            .map { sessionId -> getAndRequireSession(checkpoint, sessionId) }
            .filter { sessionState -> sessionState.status == status }
    }

    override fun doAllSessionsHaveStatusIn(
        checkpoint: FlowCheckpoint,
        sessionIds: List<String>,
        statuses: List<SessionStateType>
    ): Boolean {
        return sessionIds
            .map { sessionId -> getAndRequireSession(checkpoint, sessionId) }
            .count { sessionState -> statuses.contains(sessionState.status) } == sessionIds.size
    }

    private enum class Operation { SENDING, RECEIVING }

    /**
     * Validation only differs in that receiving messages can be more tolerant to sessions which are in the closing down
     * state, before they are actually closed.
     */
    private fun validateSessionStates(
        checkpoint: FlowCheckpoint,
        sessionIds: Collection<String>,
        operation: Operation
    ) {
        val validStatuses = when (operation) {
            Operation.SENDING -> setOf(SessionStateType.CREATED, SessionStateType.CONFIRMED)
            Operation.RECEIVING -> setOf(SessionStateType.CREATED, SessionStateType.CONFIRMED, SessionStateType.CLOSING)
        }

        val sessions = sessionIds.associateWith { checkpoint.getSessionState(it) }
        val missingSessionStates = sessions.filter { it.value == null }.map { it.key }.toList()
        val invalidSessions = sessions
            .map { it.value }
            .filterNotNull()
            .filterNot { validStatuses.contains(it.status) }
            .toList()

        if (missingSessionStates.isEmpty() && invalidSessions.isEmpty()) {
            return
        }

        val sessionsToReport = missingSessionStates.map { "'${it}'=MISSING" } +
                invalidSessions.map { "'${it.sessionId}'=${it.status}" }

        throw FlowSessionStateException(
            "${missingSessionStates.size + invalidSessions.size} of ${sessionIds.size} " +
                    "sessions are invalid [${sessionsToReport.joinToString(", ")}]"
        )
    }

    private fun sendSessionMessageToExistingSession(
        checkpoint: FlowCheckpoint,
        sessionId: String,
        payload: Any,
        instant: Instant
    ): SessionState {
        val sessionState = getAndRequireSession(checkpoint, sessionId)
        val (initiatingIdentity, initiatedIdentity) = getInitiatingAndInitiatedParties(
            sessionState, checkpoint.holdingIdentity.toAvro()
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
            instant = instant,
            maxMsgSize = checkpoint.maxMessageSize
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
        return checkpoint.getSessionState(sessionId) ?: throw FlowSessionStateException(
            "Session: $sessionId does not exist when executing session operation that requires an existing session"
        )
    }
}
