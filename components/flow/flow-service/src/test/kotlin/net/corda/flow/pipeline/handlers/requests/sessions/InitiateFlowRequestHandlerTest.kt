package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.test.flow.util.buildSessionState
import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class InitiateFlowRequestHandlerTest {

    private companion object {
        const val SESSION_ID = "session id"

        val X500_NAME = MemberX500Name(
            commonName = "Alice",
            organisation = "Alice Corp",
            locality = "LDN",
            country = "GB"
        )
    }

    private val sessionState = buildSessionState(
        SessionStateType.CONFIRMED, 0, mutableListOf(), 0, mutableListOf(), sessionId = SESSION_ID
    )

    private val flowSessionManager = mock<FlowSessionManager>()

    private val closeSessionsRequestHandler = InitiateFlowRequestHandler(flowSessionManager)

    @Test
    fun `Returns an updated WaitingFor of SessionConfirmation (Initiate)`() {
        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint = Checkpoint(), inputEventPayload = Unit)

        val result = closeSessionsRequestHandler.getUpdatedWaitingFor(
            inputContext,
            FlowIORequest.InitiateFlow(X500_NAME, SESSION_ID)
        )

        assertEquals(SessionConfirmation(listOf(SESSION_ID), SessionConfirmationType.INITIATE), result.value)
    }

    @Test
    fun `Sends a session init message`() {
        val checkpoint = Checkpoint()

        whenever(flowSessionManager.sendInitMessage(eq(checkpoint), eq(SESSION_ID), eq(X500_NAME), any())).thenReturn(sessionState)

        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint = Checkpoint(), inputEventPayload = Unit)

        closeSessionsRequestHandler.postProcess(inputContext, FlowIORequest.InitiateFlow(X500_NAME, SESSION_ID))

        verify(flowSessionManager).sendInitMessage(eq(checkpoint), eq(SESSION_ID), eq(X500_NAME), any())
    }

    @Test
    fun `Adds a new session to the flow's checkpoint`() {
        val checkpoint = Checkpoint().apply {
            sessions = mutableListOf()
        }

        whenever(flowSessionManager.sendInitMessage(eq(checkpoint), eq(SESSION_ID), eq(X500_NAME), any())).thenReturn(sessionState)

        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint, inputEventPayload = Unit)

        val outputContext = closeSessionsRequestHandler.postProcess(
            inputContext,
            FlowIORequest.InitiateFlow(X500_NAME, SESSION_ID)
        )

        assertEquals(1, outputContext.checkpoint?.sessions?.size)
        assertEquals(sessionState, outputContext.checkpoint?.sessions?.single())
    }

    @Test
    fun `Throws an exception if the flow has no checkpoint`() {
        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint = null, inputEventPayload = Unit)
        assertThrows<FlowProcessingException> {
            closeSessionsRequestHandler.postProcess(
                inputContext,
                FlowIORequest.InitiateFlow(X500_NAME, SESSION_ID)
            )
        }
    }
}