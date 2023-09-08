package net.corda.flow.pipeline.handlers.requests.sessions.service

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.flow.RequestHandlerTestContext
import net.corda.messaging.api.records.Record
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
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
    private val handler = CloseSessionService(
        testContext.flowSessionManager
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
                any()
            )
        ).thenReturn(record)
    }

    @Test
    fun `handleCloseForSessions calls flow manager api`() {
        whenever(testContext.flowSessionManager.getInitiatingAndInitiatedSessions(any()))
            .thenReturn(Pair(listOf(sessionId1), listOf(sessionId2)))
        whenever(testContext.flowSessionManager.sendCloseMessages(any(), any(), any()))
            .thenReturn(listOf(sessionState1, sessionState2))
        whenever(testContext.flowSessionManager.getSessionsWithStatuses(any(), any(), any()))
            .thenReturn(listOf(sessionState1, sessionState2))
        whenever(testContext.flowSessionManager.getRequireCloseTrueAndFalse(any(), any()))
            .thenReturn(Pair(listOf(sessionId1), listOf(sessionId2)))
        whenever(testContext.flowSessionManager.updateStatus(any(), any(), any()))
            .thenReturn(listOf(sessionState1, sessionState2))

        handler.handleCloseForSessions(testContext.flowCheckpoint, sessions)

        verify(testContext.flowSessionManager).getInitiatingAndInitiatedSessions(any())
        verify(testContext.flowSessionManager).sendCloseMessages(any(), any(), any())
        verify(testContext.flowSessionManager, times(1)).getSessionsWithStatuses(any(), any(), any())
        verify(testContext.flowSessionManager).getRequireCloseTrueAndFalse(any(), any())
        verify(testContext.flowSessionManager, times(3)).updateStatus(any(), any(), any())
    }

    @Test
    fun `getSessionsToCloseForWaitingFor calls flow manager api`() {
        whenever(testContext.flowSessionManager.getInitiatingAndInitiatedSessions(any()))
            .thenReturn(Pair(listOf(sessionId1), listOf(sessionId2)))
        whenever(testContext.flowSessionManager.getRequireCloseTrueAndFalse(any(), any()))
            .thenReturn(Pair(listOf(sessionId1), listOf(sessionId2)))

        handler.getSessionsToCloseForWaitingFor(testContext.flowCheckpoint, sessions)

        verify(testContext.flowSessionManager).getInitiatingAndInitiatedSessions(any())
        verify(testContext.flowSessionManager).getRequireCloseTrueAndFalse(any(), any())
    }
}