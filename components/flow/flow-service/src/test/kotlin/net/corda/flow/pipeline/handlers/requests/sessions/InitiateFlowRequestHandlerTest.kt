package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStackItem
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.session.manager.SessionManager
import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant

class InitiateFlowRequestHandlerTest {

    private companion object {
        const val FLOW_ID = "flow id"
        const val SESSION_ID = "session id"
        const val CPI_ID = "cpi id"
        const val INITIATING_FLOW_NAME = "Initiating flow"

        val HOLDING_IDENTITY = HoldingIdentity("x500 name", "group id")
        val FLOW_KEY = FlowKey(FLOW_ID, HOLDING_IDENTITY)
        val X500_NAME = MemberX500Name(
            commonName = "Alice",
            organisation = "Alice Corp",
            locality = "LDN",
            country = "GB"
        )
    }

    private val sessionState = SessionState.newBuilder()
        .setSessionId(SESSION_ID)
        .setSessionStartTime(Instant.now())
        .setLastReceivedMessageTime(Instant.now())
        .setLastSentMessageTime(Instant.now())
        .setCounterpartyIdentity(HoldingIdentity("Alice", "group1"))
        .setIsInitiator(true)
        .setSendAck(true)
        .setReceivedEventsState(SessionProcessState(0, emptyList()))
        .setSendEventsState(SessionProcessState(0, emptyList()))
        .setStatus(SessionStateType.CONFIRMED)
        .build()

    private val sessionManager = mock<SessionManager>()

    private val closeSessionsRequestHandler = InitiateFlowRequestHandler(sessionManager)

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
    fun `Adds a new session to the flow's checkpoint and adds a session init to the session's undelivered messages`() {
        whenever(sessionManager.processMessageToSend(any(), eq(null), any(), any())).then {
            SessionState().apply {
                sendEventsState = SessionProcessState(
                    1,
                    sessionState.sendEventsState.undeliveredMessages.plus(it.getArgument(2) as SessionEvent)
                )
            }
        }

        val checkpoint = Checkpoint().apply {
            flowKey = FLOW_KEY
            flowStackItems = listOf(
                FlowStackItem.newBuilder().setFlowName(INITIATING_FLOW_NAME).setIsInitiatingFlow(true).setSessionIds(emptyList()).build()
            )
            flowStartContext = FlowStartContext().apply {
                cpiId = CPI_ID
            }
            fiber = ByteBuffer.wrap(byteArrayOf(1, 1, 1, 1))
            sessions = emptyList()
        }

        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint = checkpoint, inputEventPayload = Unit)

        val outputContext = closeSessionsRequestHandler.postProcess(
            inputContext,
            FlowIORequest.InitiateFlow(X500_NAME, SESSION_ID)
        )

        assertEquals(1, outputContext.checkpoint?.sessions?.size)
        assertTrue(outputContext.checkpoint?.sessions?.single()?.sendEventsState?.undeliveredMessages?.single()?.payload is SessionInit)
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
