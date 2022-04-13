package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.fiber.FlowIORequest
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
    private val handler = CloseSessionsRequestHandler(testContext.flowSessionManager, testContext.recordFactory)

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

        whenever(testContext.recordFactory.createFlowEventRecord(any(), any())).thenReturn(record)
    }

    @Test
    fun `Returns an updated WaitingFor for close session confirmation`() {
        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest)

        val result = waitingFor.value as SessionConfirmation
        assertThat(result.sessionIds).containsOnly(sessionId1, sessionId2)
        assertThat(result.type).isEqualTo(SessionConfirmationType.CLOSE)
    }

    @Test
    fun `Sends close events and updates the checkpoint with session state when sessions are not in closed statuses`() {
        whenever(
            testContext.flowSessionManager.areAllSessionsInStatuses(
                testContext.flowCheckpoint,
                sessions,
                listOf(SessionStateType.CLOSED, SessionStateType.WAIT_FOR_FINAL_ACK)
            )
        ).thenReturn(false)

        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)

        verify(testContext.flowSessionManager).sendCloseMessages(eq(testContext.flowCheckpoint), eq(sessions), any())
        verify(testContext.flowCheckpoint).putSessionState(sessionState1)
        verify(testContext.flowCheckpoint).putSessionState(sessionState2)
        assertThat(outputContext.outputRecords).hasSize(0)
    }

    @Test
    fun `Creates Wakeup record and does not send close events or update the checkpoint when all sessions are closed`() {
        whenever(
            testContext.flowSessionManager.areAllSessionsInStatuses(
                testContext.flowCheckpoint,
                sessions,
                listOf(SessionStateType.CLOSED, SessionStateType.WAIT_FOR_FINAL_ACK)
            )
        ).thenReturn(true)

        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)

        verify(testContext.flowSessionManager, never()).sendCloseMessages(
            eq(testContext.flowCheckpoint),
            eq(sessions),
            any()
        )
        verify(testContext.flowCheckpoint, never()).putSessionState(sessionState1)
        verify(testContext.flowCheckpoint, never()).putSessionState(sessionState2)
        assertThat(outputContext.outputRecords).containsOnly(record)
    }
}