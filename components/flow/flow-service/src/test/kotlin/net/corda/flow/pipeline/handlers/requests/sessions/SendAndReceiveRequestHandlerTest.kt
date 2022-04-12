package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.session.SessionState
import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.fiber.FlowIORequest
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SendAndReceiveRequestHandlerTest {
    private val sessionId1 = "s1"
    private val sessionId2 = "s2"
    private val payload1 = byteArrayOf(1)
    private val payload2 = byteArrayOf(2)
    private val record = Record("","", FlowEvent())
    private val sessionState1 = SessionState().apply { this.sessionId = sessionId1 }
    private val sessionState2 = SessionState().apply { this.sessionId = sessionId2 }
    private val testContext = RequestHandlerTestContext(Any())
    private val ioRequest = FlowIORequest.SendAndReceive(mapOf(sessionId1 to payload1, sessionId2 to payload2))
    private val handler = SendAndReceiveRequestHandler(testContext.flowSessionManager, testContext.recordFactory)


    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        val flowCheckpoint = testContext.flowCheckpoint

        whenever(flowCheckpoint.getSessionState(sessionId1)).thenReturn(sessionState1)
        whenever(flowCheckpoint.getSessionState(sessionId2)).thenReturn(sessionState2)

        whenever(testContext.flowSessionManager.sendDataMessages(any(), any(), any())).thenReturn(listOf(sessionState1, sessionState2))
        whenever(testContext.recordFactory.createFlowEventRecord(eq(testContext.flowId), any())).thenReturn(record)
    }

    @Test
    fun `Waiting for session data event`() {
        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest)
        val result = waitingFor.value as net.corda.data.flow.state.waiting.SessionData
        assertThat(result.sessionIds).containsOnly(sessionId1, sessionId2)
    }

    @Test
    fun `Sends session data messages and creates a Wakeup record if all the sessions have already received events`() {
        whenever(testContext.flowSessionManager.hasReceivedEvents(testContext.flowCheckpoint, listOf(sessionId1, sessionId2))).thenReturn(true)
        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)
        verify(testContext.flowCheckpoint).putSessionState(sessionState1)
        verify(testContext.flowCheckpoint).putSessionState(sessionState2)
        verify(testContext.flowSessionManager).sendDataMessages(eq(testContext.flowCheckpoint), eq(ioRequest.sessionToPayload), any())
        verify(testContext.recordFactory).createFlowEventRecord(eq(testContext.flowId), any<Wakeup>())
        assertThat(outputContext.outputRecords).containsOnly(record)
    }

    @Test
    fun `Sends session data messages and does not create a Wakeup record if any the sessions have not already received events`() {
        whenever(testContext.flowSessionManager.hasReceivedEvents(testContext.flowCheckpoint, listOf(sessionId1, sessionId2))).thenReturn(false)
        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)
        verify(testContext).flowSessionManager.sendDataMessages(eq(testContext.flowCheckpoint), eq(ioRequest.sessionToPayload), any())
        verify(testContext.recordFactory).createFlowEventRecord(eq(testContext.flowId), any<Wakeup>())
        assertThat(outputContext.outputRecords).hasSize(0)
    }
}