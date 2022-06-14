package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.sessions.FlowSessionStateException
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CloseSessionsRequestHandlerTest {
    private val sessionId1 = "s1"
    private val sessionId2 = "s2"
    private val sessions = listOf(sessionId1, sessionId2)
    private val sessionState1 = SessionState().apply { this.sessionId = sessionId1 }
    private val sessionState2 = SessionState().apply { this.sessionId = sessionId2 }
    private val record = Record("", "", FlowEvent())
    private val testContext = RequestHandlerTestContext(Any())
    private val ioRequest = FlowIORequest.CloseSessions(sessions.toSet())
    private val handler = CloseSessionsRequestHandler(testContext.flowSessionManager, testContext.flowRecordFactory)

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        val flowCheckpoint = testContext.flowCheckpoint

        whenever(flowCheckpoint.getSessionState(sessionId1)).thenReturn(sessionState1)
        whenever(flowCheckpoint.getSessionState(sessionId2)).thenReturn(sessionState2)

        whenever(
            testContext.flowSessionManager.sendCloseMessages(
                eq(testContext.flowCheckpoint),
                eq(sessions),
                any()
            )
        ).thenReturn(listOf(sessionState1, sessionState2))

        whenever(testContext.flowRecordFactory.createFlowEventRecord(any(), any())).thenReturn(record)
    }

    @Test
    fun `Returns an updated WaitingFor of SessionsConfirmation (Close) when there are sessions to close`() {
        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest)

        val result = waitingFor.value as SessionConfirmation
        assertThat(result.sessionIds).containsOnly(sessionId1, sessionId2)
        assertThat(result.type).isEqualTo(SessionConfirmationType.CLOSE)
    }

    @Test
    fun `Returns an updated WaitingFor of SessionsConfirmation (Close) that excludes errored sessions`() {
        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.ERROR
            )
        ).thenReturn(listOf(sessionState2))

        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest)

        val result = waitingFor.value as SessionConfirmation
        assertThat(result.sessionIds).containsOnly(sessionId1)
        assertThat(result.type).isEqualTo(SessionConfirmationType.CLOSE)
    }

    @Test
    fun `Returns an updated WaitingFor of Wakeup when there are no sessions to close`() {
        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, FlowIORequest.CloseSessions(setOf()))
        assertInstanceOf(Wakeup::class.java, waitingFor.value)
    }

    @Test
    fun `Returns an updated WaitingFor of Wakeup when all the sessions are errored`() {
        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.ERROR
            )
        ).thenReturn(listOf(sessionState1, sessionState2))

        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest)
        assertInstanceOf(Wakeup::class.java, waitingFor.value)
    }

    @Test
    fun `Throws exception when updating WaitingFor when session does not exist within checkpoint`() {
        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.ERROR
            )
        ).thenThrow(IllegalArgumentException("Session does not exist"))

        assertThrows<FlowFatalException> { handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest) }
    }

    @Test
    fun `Sends close events and updates the checkpoint with session state when sessions are not closed or errored`() {
        whenever(
            testContext.flowSessionManager.doAllSessionsHaveStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.CLOSED
            )
        ).thenReturn(false)

        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.ERROR
            )
        ).thenReturn(emptyList())

        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)

        verify(testContext.flowSessionManager).sendCloseMessages(eq(testContext.flowCheckpoint), eq(sessions), any())
        verify(testContext.flowCheckpoint).putSessionState(sessionState1)
        verify(testContext.flowCheckpoint).putSessionState(sessionState2)
        assertThat(outputContext.outputRecords).hasSize(0)
    }

    @Test
    fun `Errored sessions do not send close events`() {
        whenever(
            testContext.flowSessionManager.doAllSessionsHaveStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.CLOSED
            )
        ).thenReturn(false)

        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.ERROR
            )
        ).thenReturn(listOf(sessionState2))

        whenever(
            testContext.flowSessionManager.sendCloseMessages(
                eq(testContext.flowCheckpoint),
                eq(listOf(sessionId1)),
                any()
            )
        ).thenReturn(listOf(sessionState1))

        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)

        verify(testContext.flowSessionManager).sendCloseMessages(eq(testContext.flowCheckpoint), eq(listOf(sessionId1)), any())
        verify(testContext.flowCheckpoint).putSessionState(sessionState1)
        verify(testContext.flowCheckpoint, never()).putSessionState(sessionState2)
        assertThat(outputContext.outputRecords).hasSize(0)
    }

    @Test
    fun `Creates Wakeup record when all the sessions are errored`() {
        whenever(
            testContext.flowSessionManager.doAllSessionsHaveStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.CLOSED
            )
        ).thenReturn(false)

        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.ERROR
            )
        ).thenReturn(listOf(sessionState1, sessionState2))

        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)

        assertThat(outputContext.outputRecords).containsOnly(record)
    }

    @Test
    fun `Creates Wakeup record when all the sessions are closed`() {
        whenever(
            testContext.flowSessionManager.doAllSessionsHaveStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.CLOSED
            )
        ).thenReturn(true)

        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.ERROR
            )
        ).thenReturn(emptyList())

        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)

        assertThat(outputContext.outputRecords).containsOnly(record)
    }

    @Test
    fun `Throws exception when session does not exist within checkpoint`() {
        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.ERROR
            )
        ).thenThrow(FlowSessionStateException("Session does not exist"))

        assertThrows<FlowFatalException> { handler.postProcess(testContext.flowEventContext, ioRequest) }
    }
}