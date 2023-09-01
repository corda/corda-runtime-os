package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.session.SessionState
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
    private val handler = CloseSessionsRequestHandler(
        testContext.flowRecordFactory,
        testContext.closeSessionService
    )

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        val flowCheckpoint = testContext.flowCheckpoint

        whenever(flowCheckpoint.getSessionState(sessionId1)).thenReturn(sessionState1)
        whenever(flowCheckpoint.getSessionState(sessionId2)).thenReturn(sessionState2)

        whenever(
            testContext.flowRecordFactory.createFlowEventRecord(
                any(),
                any())
        ).thenReturn(record)
    }

    @Test
    fun `Returns an updated WaitingFor of SessionsConfirmation (Close) when there are sessions to close`() {
        whenever(testContext.closeSessionService.getSessionsToCloseForWaitingFor(testContext.flowCheckpoint, sessions))
            .thenReturn(sessions)

        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest)
        val result = waitingFor.value as SessionConfirmation

        assertThat(result.sessionIds).containsOnly(sessionId1, sessionId2)
        assertThat(result.type).isEqualTo(SessionConfirmationType.CLOSE)
    }

    @Test
    fun `Returns an updated WaitingFor of Wakeup when there are no sessions to close`() {
        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, FlowIORequest.CloseSessions(setOf()))

        assertInstanceOf(Wakeup::class.java, waitingFor.value)
    }

    @Test
    fun `Throws exception when updating WaitingFor when session does not exist within checkpoint`() {
        whenever(testContext.closeSessionService.getSessionsToCloseForWaitingFor(testContext.flowCheckpoint, sessions)
        ).thenThrow(IllegalArgumentException("Session does not exist"))

        assertThrows<FlowFatalException> { handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest) }
    }

    @Test
    fun `Sends close events and updates the checkpoint with session state when sessions are not closed or errored`() {
        whenever(testContext.closeSessionService.handleCloseForSessions(testContext.flowCheckpoint, sessions))
            .thenReturn(sessions)

        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)

        verify(testContext.closeSessionService).handleCloseForSessions(testContext.flowCheckpoint, sessions)
        assertThat(outputContext.outputRecords).hasSize(0)
    }

    @Test
    fun `Creates Wakeup record when all the sessions are closed`() {
        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)

        verify(testContext.closeSessionService).handleCloseForSessions(testContext.flowCheckpoint, sessions)
        assertThat(outputContext.outputRecords).containsOnly(record)
    }

    @Test
    fun `Throws exception when session does not exist within checkpoint`() {
        whenever(testContext.closeSessionService.handleCloseForSessions(testContext.flowCheckpoint, sessions)
        ).thenThrow(FlowSessionStateException("Session does not exist"))

        assertThrows<FlowFatalException> { handler.postProcess(testContext.flowEventContext, ioRequest) }
    }
}