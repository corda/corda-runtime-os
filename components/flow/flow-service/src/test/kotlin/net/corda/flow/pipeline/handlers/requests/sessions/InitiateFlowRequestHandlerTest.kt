package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.FlowStackItem
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.ALICE_X500_NAME
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

class InitiateFlowRequestHandlerTest {

    private val sessionId1 = "s1"
    private val sessionState1 = SessionState().apply { this.sessionId = sessionId1 }
    private val sessionEvent1 = SessionEvent().apply { this.sessionId = sessionId1 }
    private val firstFlowStackItem = FlowStackItem().apply { flowName = "flow name" }
    private val testContext = RequestHandlerTestContext(Any())
    private val ioRequest = FlowIORequest.InitiateFlow(ALICE_X500_NAME, sessionId1)
    private val handler = InitiateFlowRequestHandler(testContext.sessionManager, testContext.sessionEventFactory)

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        val sessionEventFactory = testContext.sessionEventFactory

        whenever(sessionEventFactory.create(
            eq(sessionId1),
            any(),
            argThat { matchExpectedPayload(this) }
        )).thenReturn(sessionEvent1)

        whenever(
            testContext.sessionManager.processMessageToSend(
                eq(testContext.flowId),
                eq(null),
                eq(sessionEvent1),
                any()
            )
        ).thenReturn(sessionState1)

        whenever(testContext.flowStack.peekFirst()).thenReturn(firstFlowStackItem)

        testContext.flowStartContext.cpiId = "cpi 1"
    }

    @Test
    fun `Returns an updated WaitingFor for init session confirmation`() {
        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest)

        val result = waitingFor.value as SessionConfirmation
        assertThat(result.sessionIds).containsOnly(sessionId1)
        assertThat(result.type).isEqualTo(SessionConfirmationType.INITIATE)
    }

    @Test
    fun `test session init event sent to session manager and checkpoint updated with session state`() {
        handler.postProcess(testContext.flowEventContext, ioRequest)
        verify(testContext.flowCheckpoint).putSessionState(sessionState1)
    }

    @Test
    fun `test does not add an output record`() {
        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)
        assertThat(outputContext.outputRecords).hasSize(0)
    }

    private fun matchExpectedPayload(payload: Any): Boolean {
        val sessionInitPayload = payload as SessionInit

        val expectedInitiatedIdentity = HoldingIdentity(ioRequest.x500Name.toString(), "flow-worker-dev")
        assertThat(sessionInitPayload.flowName).isEqualTo(firstFlowStackItem.flowName)
        assertThat(sessionInitPayload.flowId).isEqualTo(testContext.flowId)
        assertThat(sessionInitPayload.cpiId).isEqualTo(testContext.flowStartContext.cpiId)
        assertThat(sessionInitPayload.initiatingIdentity).isEqualTo(testContext.holdingIdentity)
        assertThat(sessionInitPayload.initiatedIdentity).isEqualTo(expectedInitiatedIdentity)

        return true
    }
}