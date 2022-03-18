package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.fiber.FlowIORequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
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
    private val sessionEvent1 = SessionEvent().apply { this.sessionId = sessionId1 }
    private val sessionEvent2 = SessionEvent().apply { this.sessionId = sessionId2 }
    private val testContext = RequestHandlerTestContext(Any())
    private val ioRequest = FlowIORequest.CloseSessions(setOf(sessionId1,sessionId2))
    private val handler = CloseSessionsRequestHandler(testContext.sessionManager, testContext.sessionEventFactory)


    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        val flowCheckpoint = testContext.flowCheckpoint
        val sessionEventFactory = testContext.sessionEventFactory

        whenever(flowCheckpoint.getSessionState(sessionId1)).thenReturn(sessionState1)
        whenever(flowCheckpoint.getSessionState(sessionId2)).thenReturn(sessionState2)

        whenever(sessionEventFactory.create(
            eq(sessionId1),
            any(),
            argThat{ this is SessionClose}
        )).thenReturn(sessionEvent1)
        whenever(sessionEventFactory.create(
            eq(sessionId2),
            any(),
            argThat{ this is SessionClose}
        )).thenReturn(sessionEvent2)

        whenever(testContext.sessionManager.processMessageToSend(
            eq(testContext.flowId),
            eq(sessionState1),
            eq(sessionEvent1),
            any()
        )).thenReturn(sessionState1)
        whenever(testContext.sessionManager.processMessageToSend(
            eq(testContext.flowId),
            eq(sessionState2),
            eq(sessionEvent2),
            any()
        )).thenReturn(sessionState2)
    }

    @Test
    fun `Returns an updated WaitingFor for close session confirmation`() {
        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest)

        val result = waitingFor.value as SessionConfirmation
        assertThat(result.sessionIds).containsOnly(sessionId1,sessionId2)
        assertThat(result.type).isEqualTo(SessionConfirmationType.CLOSE)
    }

    @Test
    fun `test close events sent to session manager for all open sessions and checkpoint updated with session state`() {
        handler.postProcess(testContext.flowEventContext, ioRequest)
        verify(testContext.flowCheckpoint).putSessionState(sessionState1)
        verify(testContext.flowCheckpoint).putSessionState(sessionState2)
    }

    @Test
    fun `test does not add an output record`() {
        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)
        assertThat(outputContext.outputRecords).hasSize(0)
    }
}