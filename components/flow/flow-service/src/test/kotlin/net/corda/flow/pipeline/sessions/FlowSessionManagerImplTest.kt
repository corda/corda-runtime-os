package net.corda.flow.pipeline.sessions

import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStackItem
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.state.FlowStack
import net.corda.session.manager.SessionManager
import net.corda.test.flow.util.buildSessionEvent
import net.corda.test.flow.util.buildSessionState
import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant

class FlowSessionManagerImplTest {

    private companion object {
        const val FLOW_ID = "flow id"
        const val SESSION_ID = "session id"
        const val ANOTHER_SESSION_ID = "another session id"
        const val CPI_ID = "cpi id"
        const val INITIATING_FLOW_NAME = "Initiating flow"
        val X500_NAME = MemberX500Name(
            commonName = "Alice",
            organisation = "Alice Corp",
            locality = "LDN",
            country = "GB"
        )
        val HOLDING_IDENTITY = HoldingIdentity("x500 name", "group id")
        val COUNTERPARTY_HOLDING_IDENTITY = HoldingIdentity(X500_NAME.toString(), "flow-worker-dev")
        val FLOW_KEY = FlowKey(FLOW_ID, HOLDING_IDENTITY)
    }

    private val sessionState = buildSessionState(
        SessionStateType.CONFIRMED,
        0,
        mutableListOf(),
        0,
        mutableListOf(),
        sessionId = SESSION_ID,
        counterpartyIdentity = COUNTERPARTY_HOLDING_IDENTITY
    )

    private val anotherSessionState = buildSessionState(
        SessionStateType.CONFIRMED,
        0,
        mutableListOf(),
        0,
        mutableListOf(),
        sessionId = ANOTHER_SESSION_ID,
        counterpartyIdentity = COUNTERPARTY_HOLDING_IDENTITY
    )

    private val sessionManager = mock<SessionManager>()
    private val checkpoint = mock<FlowCheckpoint>()
    private val flowStack = mock<FlowStack>()

    private val flowSessionManager = FlowSessionManagerImpl(sessionManager)

    @BeforeEach
    fun setup() {
        whenever(checkpoint.flowId).thenReturn(FLOW_ID)
        whenever(checkpoint.flowKey).thenReturn(FLOW_KEY)
        whenever(checkpoint.getSessionState(SESSION_ID)).thenReturn(sessionState)
        whenever(checkpoint.getSessionState(ANOTHER_SESSION_ID)).thenReturn(anotherSessionState)
    }

    @Test
    fun `sendInitMessage creates a SessionInit message and processes it`() {
        whenever(sessionManager.processMessageToSend(any(), eq(null), any(), any())).then {
            SessionState().apply {
                sendEventsState = SessionProcessState(
                    1,
                    sessionState.sendEventsState.undeliveredMessages.plus(it.getArgument(2) as SessionEvent)
                )
            }
        }

        whenever(flowStack.peek()).thenReturn(
            FlowStackItem.newBuilder().setFlowName(INITIATING_FLOW_NAME).setIsInitiatingFlow(true)
                .setSessionIds(emptyList()).build()
        )
        whenever(checkpoint.flowStartContext).thenReturn(FlowStartContext().apply {
            cpiId = CPI_ID
        })
        whenever(checkpoint.flowStack).thenReturn(flowStack)

        val instant = Instant.now()

        val expectedSessionInit = SessionInit.newBuilder()
            .setFlowName(INITIATING_FLOW_NAME)
            .setFlowId(FLOW_ID)
            .setCpiId(CPI_ID)
            .setPayload(ByteBuffer.wrap(byteArrayOf()))
            .build()
        val expectedSessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            SESSION_ID,
            sequenceNum = null,
            payload = expectedSessionInit,
            timestamp = instant,
            initiatingIdentity = HOLDING_IDENTITY,
            initiatedIdentity = COUNTERPARTY_HOLDING_IDENTITY
        )

        val sessionState = flowSessionManager.sendInitMessage(checkpoint, SESSION_ID, X500_NAME, instant)

        verify(sessionManager).processMessageToSend(eq(FLOW_ID), eq(null), any(), eq(instant))
        assertEquals(expectedSessionEvent, sessionState.sendEventsState.undeliveredMessages.single())
    }

    @Test
    fun `sendDataMessages creates SessionData messages and processes them`() {
        whenever(checkpoint.sessions).thenReturn(listOf(sessionState, anotherSessionState))

        whenever(sessionManager.processMessageToSend(any(), eq(sessionState), any(), any())).then {
            SessionState().apply {
                sendEventsState = SessionProcessState(
                    1,
                    sessionState.sendEventsState.undeliveredMessages.plus(it.getArgument(2) as SessionEvent)
                )
            }
        }
        whenever(sessionManager.processMessageToSend(any(), eq(anotherSessionState), any(), any())).then {
            SessionState().apply {
                sendEventsState = SessionProcessState(
                    1,
                    anotherSessionState.sendEventsState.undeliveredMessages.plus(it.getArgument(2) as SessionEvent)
                )
            }
        }

        val instant = Instant.now()

        val payload = byteArrayOf(1)
        val anotherPayload = byteArrayOf(2)

        val expectedSessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            SESSION_ID,
            sequenceNum = null,
            payload = SessionData(ByteBuffer.wrap(payload)),
            timestamp = instant,
            initiatingIdentity = HOLDING_IDENTITY,
            initiatedIdentity = COUNTERPARTY_HOLDING_IDENTITY
        )
        val anotherExpectedSessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            ANOTHER_SESSION_ID,
            sequenceNum = null,
            payload = SessionData(ByteBuffer.wrap(anotherPayload)),
            timestamp = instant,
            initiatingIdentity = HOLDING_IDENTITY,
            initiatedIdentity = COUNTERPARTY_HOLDING_IDENTITY
        )

        val sessionStates = flowSessionManager.sendDataMessages(
            checkpoint,
            mapOf(SESSION_ID to payload, ANOTHER_SESSION_ID to anotherPayload),
            instant
        )

        verify(sessionManager).processMessageToSend(eq(FLOW_ID), eq(sessionState), any(), eq(instant))
        verify(sessionManager).processMessageToSend(eq(FLOW_ID), eq(anotherSessionState), any(), eq(instant))
        assertEquals(expectedSessionEvent, sessionStates[0].sendEventsState.undeliveredMessages.single())
        assertEquals(anotherExpectedSessionEvent, sessionStates[1].sendEventsState.undeliveredMessages.single())
    }

    @Test
    fun `sendDataMessages does nothing when there are no sessions passed in`() {
        val instant = Instant.now()
        flowSessionManager.sendDataMessages(checkpoint, emptyMap(), instant)
        verify(sessionManager, never()).processMessageToSend(eq(FLOW_ID), any(), any(), eq(instant))
    }

    @Test
    fun `sendDataMessages throws an error when the checkpoint does not contain a passed in session`() {
        whenever(checkpoint.getSessionState(ANOTHER_SESSION_ID)).thenReturn(null)

        val instant = Instant.now()

        assertThrows<FlowProcessingException> {
            flowSessionManager.sendDataMessages(
                checkpoint,
                mapOf(SESSION_ID to byteArrayOf(), ANOTHER_SESSION_ID to byteArrayOf()),
                instant
            )
        }
    }

    @Test
    fun `sendCloseMessages creates SessionClose messages and processes them`() {
        whenever(sessionManager.processMessageToSend(any(), eq(sessionState), any(), any())).then {
            SessionState().apply {
                sendEventsState = SessionProcessState(
                    1,
                    sessionState.sendEventsState.undeliveredMessages.plus(it.getArgument(2) as SessionEvent)
                )
            }
        }
        whenever(sessionManager.processMessageToSend(any(), eq(anotherSessionState), any(), any())).then {
            SessionState().apply {
                sendEventsState = SessionProcessState(
                    1,
                    anotherSessionState.sendEventsState.undeliveredMessages.plus(it.getArgument(2) as SessionEvent)
                )
            }
        }

        val instant = Instant.now()

        val expectedSessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            SESSION_ID,
            sequenceNum = null,
            payload = SessionClose(),
            timestamp = instant,
            initiatingIdentity = HOLDING_IDENTITY,
            initiatedIdentity = COUNTERPARTY_HOLDING_IDENTITY
        )
        val anotherExpectedSessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            ANOTHER_SESSION_ID,
            sequenceNum = null,
            payload = SessionClose(),
            timestamp = instant,
            initiatingIdentity = HOLDING_IDENTITY,
            initiatedIdentity = COUNTERPARTY_HOLDING_IDENTITY
        )

        val sessionStates = flowSessionManager.sendCloseMessages(
            checkpoint,
            listOf(SESSION_ID, ANOTHER_SESSION_ID),
            instant
        )

        verify(sessionManager).processMessageToSend(eq(FLOW_ID), eq(sessionState), any(), eq(instant))
        verify(sessionManager).processMessageToSend(eq(FLOW_ID), eq(anotherSessionState), any(), eq(instant))
        assertEquals(expectedSessionEvent, sessionStates[0].sendEventsState.undeliveredMessages.single())
        assertEquals(anotherExpectedSessionEvent, sessionStates[1].sendEventsState.undeliveredMessages.single())
    }

    @Test
    fun `sendCloseMessages does nothing when there are no sessions passed in`() {
        val instant = Instant.now()
        flowSessionManager.sendCloseMessages(checkpoint, emptyList(), instant)
        verify(sessionManager, never()).processMessageToSend(eq(FLOW_ID), any(), any(), eq(instant))
    }

    @Test
    fun `sendCloseMessages throws an error when the checkpoint does not contain a passed in session`() {
        whenever(checkpoint.getSessionState(ANOTHER_SESSION_ID)).thenReturn(null)

        val instant = Instant.now()

        assertThrows<FlowProcessingException> {
            flowSessionManager.sendCloseMessages(
                checkpoint,
                listOf(SESSION_ID, ANOTHER_SESSION_ID),
                instant
            )
        }
    }

    @Test
    fun `getReceivedEvents returns events for the passed in sessions`() {
        val sessionEvent = buildSessionEvent(MessageDirection.OUTBOUND, SESSION_ID, sequenceNum = null, payload = Unit)
        val anotherSessionEvent =
            buildSessionEvent(MessageDirection.OUTBOUND, ANOTHER_SESSION_ID, sequenceNum = null, payload = Unit)

        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(sessionEvent)
        whenever(sessionManager.getNextReceivedEvent(anotherSessionState)).thenReturn(anotherSessionEvent)

        val receivedEvents = flowSessionManager.getReceivedEvents(checkpoint, listOf(SESSION_ID, ANOTHER_SESSION_ID))

        assertEquals(listOf(sessionState to sessionEvent, anotherSessionState to anotherSessionEvent), receivedEvents)
    }

    @Test
    fun `getReceivedEvents does not return state event pairs when there is no next event`() {
        val sessionEvent = buildSessionEvent(MessageDirection.OUTBOUND, SESSION_ID, sequenceNum = null, payload = Unit)

        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(sessionEvent)
        whenever(sessionManager.getNextReceivedEvent(anotherSessionState)).thenReturn(null)

        val receivedEvents = flowSessionManager.getReceivedEvents(checkpoint, listOf(SESSION_ID, ANOTHER_SESSION_ID))

        assertEquals(listOf(sessionState to sessionEvent), receivedEvents)
    }

    @Test
    fun `getReceivedEvents does nothing when no sessions are passed in`() {
        whenever(checkpoint.sessions).thenReturn(listOf(sessionState, anotherSessionState))

        val receivedEvents = flowSessionManager.getReceivedEvents(checkpoint, emptyList())

        assertEquals(emptyList<Pair<SessionState, SessionEvent>>(), receivedEvents)
        verify(sessionManager, never()).getNextReceivedEvent(any())
    }

    @Test
    fun `getReceivedEvents throws an error when the checkpoint does not contain a passed in session`() {
        val sessionEvent = buildSessionEvent(MessageDirection.OUTBOUND, SESSION_ID, sequenceNum = null, payload = Unit)

        whenever(checkpoint.getSessionState(ANOTHER_SESSION_ID)).thenReturn(null)
        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(sessionEvent)

        assertThrows<FlowProcessingException> {
            flowSessionManager.getReceivedEvents(checkpoint, listOf(SESSION_ID, ANOTHER_SESSION_ID))
        }
    }

    @Test
    fun `acknowledgeReceivedEvents acknowledges the passed in events`() {
        val sessionEvent = buildSessionEvent(MessageDirection.OUTBOUND, SESSION_ID, sequenceNum = 1, payload = Unit)
        val anotherSessionEvent =
            buildSessionEvent(MessageDirection.OUTBOUND, ANOTHER_SESSION_ID, sequenceNum = 2, payload = Unit)
        flowSessionManager.acknowledgeReceivedEvents(
            listOf(
                sessionState to sessionEvent,
                anotherSessionState to anotherSessionEvent
            )
        )
        verify(sessionManager).acknowledgeReceivedEvent(sessionState, 1)
        verify(sessionManager).acknowledgeReceivedEvent(anotherSessionState, 2)
    }

    @Test
    fun `acknowledgeReceivedEvents does nothing when no events are passed in`() {
        flowSessionManager.acknowledgeReceivedEvents(emptyList())
        verify(sessionManager, never()).acknowledgeReceivedEvent(any(), any())
    }

    @Test
    fun `hasReceivedEvents returns true if an event for every passed in session has been received`() {
        val sessionEvent = buildSessionEvent(MessageDirection.OUTBOUND, SESSION_ID, sequenceNum = null, payload = Unit)
        val anotherSessionEvent =
            buildSessionEvent(MessageDirection.OUTBOUND, ANOTHER_SESSION_ID, sequenceNum = null, payload = Unit)

        whenever(checkpoint.sessions).thenReturn(listOf(sessionState, anotherSessionState))
        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(sessionEvent)
        whenever(sessionManager.getNextReceivedEvent(anotherSessionState)).thenReturn(anotherSessionEvent)

        assertTrue(flowSessionManager.hasReceivedEvents(checkpoint, listOf(SESSION_ID, ANOTHER_SESSION_ID)))
    }

    @Test
    fun `hasReceivedEvents returns false if any event for the passed in sessions has not been received`() {
        val sessionEvent = buildSessionEvent(MessageDirection.OUTBOUND, SESSION_ID, sequenceNum = null, payload = Unit)

        whenever(checkpoint.sessions).thenReturn(listOf(sessionState, anotherSessionState))
        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(sessionEvent)
        whenever(sessionManager.getNextReceivedEvent(anotherSessionState)).thenReturn(null)

        assertFalse(flowSessionManager.hasReceivedEvents(checkpoint, listOf(SESSION_ID, ANOTHER_SESSION_ID)))
    }

    @Test
    fun `hasReceivedEvents returns true if no sessions were passed in`() {
        assertTrue(flowSessionManager.hasReceivedEvents(checkpoint, listOf()))
    }

    @Test
    fun `areAllSessionsInStatuses returns true if all sessions are contained in the passed in statuses`() {
        sessionState.status = SessionStateType.CLOSED
        anotherSessionState.status = SessionStateType.CONFIRMED

        assertTrue(
            flowSessionManager.areAllSessionsInStatuses(
                checkpoint,
                listOf(SESSION_ID, ANOTHER_SESSION_ID),
                listOf(SessionStateType.CLOSED, SessionStateType.CONFIRMED)
            )
        )
    }

    @Test
    fun `areAllSessionsInStatuses returns false if any sessions are not contained in the passed in statuses`() {
        sessionState.status = SessionStateType.CLOSED
        anotherSessionState.status = SessionStateType.CREATED

        assertFalse(
            flowSessionManager.areAllSessionsInStatuses(
                checkpoint,
                listOf(SESSION_ID, ANOTHER_SESSION_ID),
                listOf(SessionStateType.CLOSED, SessionStateType.CONFIRMED)
            )
        )
    }

    @Test
    fun `areAllSessionsInStatuses returns false if no sessions are contained in the passed in statuses`() {
        sessionState.status = SessionStateType.WAIT_FOR_FINAL_ACK
        anotherSessionState.status = SessionStateType.CREATED

        assertFalse(
            flowSessionManager.areAllSessionsInStatuses(
                checkpoint,
                listOf(SESSION_ID, ANOTHER_SESSION_ID),
                listOf(SessionStateType.CLOSED, SessionStateType.CONFIRMED)
            )
        )
    }

    @Test
    fun `areAllSessionsInStatuses returns true if there are no sessions in the checkpoint`() {
        whenever(checkpoint.getSessionState(any())).thenReturn(null)

        assertTrue(
            flowSessionManager.areAllSessionsInStatuses(
                checkpoint,
                listOf(SESSION_ID, ANOTHER_SESSION_ID),
                listOf(SessionStateType.CLOSED, SessionStateType.CONFIRMED)
            )
        )
    }

    @Test
    fun `areAllSessionsInStatuses returns true if there are no sessions passed in`() {
        sessionState.status = SessionStateType.CLOSED
        anotherSessionState.status = SessionStateType.CONFIRMED

        assertTrue(
            flowSessionManager.areAllSessionsInStatuses(
                checkpoint,
                emptyList(),
                listOf(SessionStateType.CLOSED, SessionStateType.CONFIRMED)
            )
        )
    }

    @Test
    fun `areAllSessionsInStatuses returns false if there are no statuses passed in`() {
        assertFalse(
            flowSessionManager.areAllSessionsInStatuses(
                checkpoint,
                listOf(SESSION_ID, ANOTHER_SESSION_ID),
                emptyList()
            )
        )
    }
}