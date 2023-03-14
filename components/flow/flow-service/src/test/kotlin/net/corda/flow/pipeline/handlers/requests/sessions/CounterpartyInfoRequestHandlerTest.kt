package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.sessions.FlowSessionStateException
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CounterpartyInfoRequestHandlerTest {
    private val sessionId1 = "s1"
    val record = Record("", "", FlowEvent())
    private val sessionState1 = SessionState().apply { this.sessionId = sessionId1 }
    private val testContext = RequestHandlerTestContext(Any())

    private val ioRequest = FlowIORequest.CounterPartyFlowInfo(
        FlowIORequest.SessionInfo(sessionId1, testContext.counterparty)

    )
    private val handler =
        CounterPartyInfoRequestHandler(testContext.initiateFlowReqService)


    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        val flowCheckpoint = testContext.flowCheckpoint
        whenever(flowCheckpoint.getSessionState(sessionId1)).thenReturn(sessionState1)
    }

    @Test
    fun `Waiting for CounterPartyFlowInfo`() {
        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest)
        assertThat(waitingFor.value).isInstanceOf(net.corda.data.flow.state.waiting.CounterPartyFlowInfo()::class.java)
    }

    @Test
    fun `Initiates flows not initiated yet`() {
        handler.postProcess(testContext.flowEventContext, ioRequest)
        verify(testContext.initiateFlowReqService).initiateFlowsNotInitiated(any(), any())
    }

    @Test
    fun `Throws exception when any of the sessions are invalid`() {
        whenever(testContext.initiateFlowReqService.initiateFlowsNotInitiated(any(), any()))
            .thenThrow(FlowSessionStateException(""))

        assertThrows<FlowPlatformException> { handler.postProcess(testContext.flowEventContext, ioRequest) }
    }
}