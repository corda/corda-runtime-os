package net.corda.flow.pipeline.handlers.requests.sessions.service

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.handlers.requests.sessions.CloseSessionsRequestHandler
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class CloseSessionServiceTest {
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
    private val pair = Pair

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        val flowCheckpoint = testContext.flowCheckpoint

        whenever(flowCheckpoint.getSessionState(sessionId1)).thenReturn(sessionState1)
        whenever(flowCheckpoint.getSessionState(sessionId2)).thenReturn(sessionState2)

        whenever(
            testContext.flowRecordFactory.createFlowEventRecord(
                any(),
                any()
            )
        ).thenReturn(record)
    }

    @Test
    fun `getSessionsToCloseForWaitingFor calls flow manager api`() {
        whenever(testContext.flowSessionManager.getInitiatingAndInitiatedSessions(sessions))
            .thenReturn(Pair(listOf(sessionId1),listOf(sessionId2)))
        whenever(testContext.flowSessionManager.getRequireCloseTrueAndFalse(testContext.flowCheckpoint, sessions))
            .thenReturn(Pair(listOf(sessionId1),listOf(sessionId2)))

        val sessionsToClose = testContext.closeSessionService.getSessionsToCloseForWaitingFor(testContext.flowCheckpoint, sessions)

        //verify(testContext.flowSessionManager.getInitiatingAndInitiatedSessions(sessions))
        //verify(testContext.flowSessionManager.getRequireCloseTrueAndFalse(testContext.flowCheckpoint, sessions))
        assertThat(sessionsToClose).isNotEmpty
    }

    /**
     * Executes the close logic for the given [sessions].
     * @param checkpoint - the checkpoint
     * @param sessions - list of sessions IDs
     * @return List of sessions that are not CLOSED or ERROR
     */
    @Test
    fun handleCloseForSessions() {
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

        verify(testContext.flowSessionManager).sendCloseMessages(
            eq(testContext.flowCheckpoint),
            eq(listOf(sessionId1)),
            any()
        )
        verify(testContext.flowCheckpoint).putSessionStates(listOf(sessionState1))
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
}