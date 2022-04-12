package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.fiber.FlowIORequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class CloseSessionsRequestHandlerTest {
    private val nowUtc = Instant.ofEpochMilli(1)
    private val sessionId1 = "s1"
    private val sessionId2 = "s2"
    private val sessionState1 = SessionState().apply { this.sessionId = sessionId1 }
    private val sessionState2 = SessionState().apply { this.sessionId = sessionId2 }
    private val testContext = RequestHandlerTestContext(Any())
    private val ioRequest = FlowIORequest.CloseSessions(setOf(sessionId1, sessionId2))
    private val handler = CloseSessionsRequestHandler(testContext.flowSessionManager, testContext.recordFactory)

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        val flowCheckpoint = testContext.flowCheckpoint

        whenever(flowCheckpoint.getSessionState(sessionId1)).thenReturn(sessionState1)
        whenever(flowCheckpoint.getSessionState(sessionId2)).thenReturn(sessionState2)

        whenever(
            testContext.flowSessionManager.areAllSessionsInStatuses(
                flowCheckpoint,
                ioRequest.sessions.toList(),
                listOf(SessionStateType.CLOSED, SessionStateType.WAIT_FOR_FINAL_ACK)
            )
        ).thenReturn(false)

        whenever(
            testContext.flowSessionManager.sendCloseMessages(
                testContext.flowCheckpoint,
                eq(ioRequest.sessions.toList()),
                any()
            )
        ).thenReturn(listOf(sessionState1, sessionState2))
    }

    @Test
    fun `Returns an updated WaitingFor for close session confirmation`() {
        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest)

        val result = waitingFor.value as SessionConfirmation
        assertThat(result.sessionIds).containsOnly(sessionId1, sessionId2)
        assertThat(result.type).isEqualTo(SessionConfirmationType.CLOSE)
    }

    @Test
    fun `Close events sent to session manager for all open sessions and checkpoint updated with session state`() {
        handler.postProcess(testContext.flowEventContext, ioRequest)
        verify(testContext.flowSessionManager).sendCloseMessages(eq(testContext.flowCheckpoint), eq(ioRequest.sessions.toList()), any())
        verify(testContext.flowCheckpoint).putSessionState(sessionState1)
        verify(testContext.flowCheckpoint).putSessionState(sessionState2)
    }

//    @Test
//    fun `Creates a Wakeup record if all the sessions are already closed or waiting for final acknowledgement`() {
//        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)
//        assertThat(outputContext.outputRecords).hasSize(0)
//    }
//
//    @Test
//    fun `Does not create a Wakeup record if any of the sessions are not closed or waiting for final acknowledgement`() {
//        whenever(
//            testContext.flowSessionManager.areAllSessionsInStatuses(
//                testContext.flowCheckpoint,
//                ioRequest.sessions.toList(),
//                listOf(SessionStateType.CLOSED, SessionStateType.WAIT_FOR_FINAL_ACK)
//            )
//        ).thenReturn(false)
//    }
}