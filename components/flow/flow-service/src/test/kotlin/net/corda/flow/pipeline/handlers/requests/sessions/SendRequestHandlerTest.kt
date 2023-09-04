package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.state.session.SessionState
import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.application.sessions.SessionInfo
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.sessions.FlowSessionStateException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SendRequestHandlerTest {
    private val sessionId1 = "s1"
    private val sessionId2 = "s2"
    private val payload1 = byteArrayOf(1)
    private val payload2 = byteArrayOf(2)
    private val sessionState1 = SessionState().apply { this.sessionId = sessionId1 }
    private val sessionState2 = SessionState().apply { this.sessionId = sessionId2 }
    private val testContext = RequestHandlerTestContext(Any())

    private val ioRequest = FlowIORequest.Send(
        mapOf(
            SessionInfo(sessionId1, testContext.counterparty) to payload1,
            SessionInfo(sessionId2, testContext.counterparty) to payload2
        )
    )
    private val handler =
        SendRequestHandler(testContext.flowSessionManager, testContext.initiateFlowReqService)


    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        val flowCheckpoint = testContext.flowCheckpoint

        whenever(flowCheckpoint.getSessionState(sessionId1)).thenReturn(sessionState1)
        whenever(flowCheckpoint.getSessionState(sessionId2)).thenReturn(sessionState2)

        whenever(testContext.flowSessionManager.sendDataMessages(any(), any(), any())).thenReturn(
            listOf(
                sessionState1,
                sessionState2
            )
        )
    }

    @Test
    fun `Waiting for Wakeup event`() {
        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest)
        assertThat(waitingFor.value).isInstanceOf(net.corda.data.flow.state.waiting.Wakeup()::class.java)
    }

    @Test
    fun `Sends session data messages if all the sessions have already received events`() {
        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)
        verify(testContext.flowCheckpoint).putSessionStates(listOf(sessionState1, sessionState2))
        verify(testContext.flowSessionManager).sendDataMessages(
            eq(testContext.flowCheckpoint),
            any(),
            any()
        )
        verify(testContext.initiateFlowReqService).generateSessions(any(), any(), anyBoolean())
        assertThat(outputContext.outputRecords).isEmpty()
    }

    @Test
    fun `Throws exception when any of the sessions are invalid`() {
        whenever(testContext.flowSessionManager.sendDataMessages(any(), any(), any()))
            .thenThrow(FlowSessionStateException(""))

        assertThrows<FlowPlatformException> { handler.postProcess(testContext.flowEventContext, ioRequest) }
    }
}