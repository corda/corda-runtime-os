package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.flow.ALICE_X500_NAME
import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.fiber.FlowIORequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class InitiateFlowRequestHandlerTest {

    private val sessionId1 = "s1"
    private val sessionState1 = SessionState().apply { this.sessionId = sessionId1 }
    private val testContext = RequestHandlerTestContext(Any())
    private val ioRequest = FlowIORequest.InitiateFlow(ALICE_X500_NAME, sessionId1)
    private val handler = InitiateFlowRequestHandler(testContext.flowSessionManager)

    @Suppress("Unused")
    @BeforeEach
    fun setup() {

        whenever(
            testContext.flowSessionManager.sendInitMessage(
                eq(testContext.flowCheckpoint),
                eq(sessionId1),
                eq(ALICE_X500_NAME),
                any()
            )
        ).thenReturn(sessionState1)
    }

    @Test
    fun `Returns an updated WaitingFor for init session confirmation`() {
        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest)

        val result = waitingFor.value as SessionConfirmation
        assertThat(result.sessionIds).containsOnly(sessionId1)
        assertThat(result.type).isEqualTo(SessionConfirmationType.INITIATE)
    }

    @Test
    fun `Session init event sent to session manager and checkpoint updated with session state`() {
        handler.postProcess(testContext.flowEventContext, ioRequest)
        verify(testContext.flowCheckpoint).putSessionState(sessionState1)
    }

    @Test
    fun `Does not add an output record`() {
        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)
        assertThat(outputContext.outputRecords).hasSize(0)
    }
}