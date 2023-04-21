package net.corda.flow.pipeline.handlers.waiting.sessions

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.CounterPartyFlowInfo
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@Suppress("MaxLineLength")
class CounterpartyFlowInfoWaitingForHandlerTest {

    private companion object {
        const val SESSION_ID = "session id"
        const val ANOTHER_SESSION_ID = "another session id"
        val sessions = listOf(SESSION_ID, ANOTHER_SESSION_ID)
    }

    private val checkpoint = mock<FlowCheckpoint>()
    private val sessionState = SessionState()
    private val anotherSessionState = SessionState()
    private val sessionConfirmationWaitingForHandler = CounterpartyFlowInfoWaitingForHandler()

    val inputContext = buildFlowEventContext(
        checkpoint = checkpoint,
        inputEventPayload = SessionEvent().apply {
            sessionId = SESSION_ID
            payload = Wakeup()
        }
    )

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        sessionState.sessionId = SESSION_ID
        anotherSessionState.sessionId = ANOTHER_SESSION_ID

        whenever(checkpoint.getSessionState(sessionState.sessionId)).thenReturn(sessionState)
        whenever(checkpoint.getSessionState(anotherSessionState.sessionId)).thenReturn(anotherSessionState)
    }

    @Test
    fun `Throws exception when the session does not exist`() {
        whenever(checkpoint.getSessionState(sessionState.sessionId)).thenReturn(null)
        assertThrows<CordaRuntimeException> {
            sessionConfirmationWaitingForHandler.runOrContinue(inputContext, CounterPartyFlowInfo(SESSION_ID))
        }
    }

    @Test
    fun `Flow Engine continues to wait when in CREATED state`() {
        whenever(checkpoint.getSessionState(sessionState.sessionId)).thenReturn(sessionState.apply {
            status = SessionStateType.CREATED
        })
        val continuation = sessionConfirmationWaitingForHandler.runOrContinue(inputContext, CounterPartyFlowInfo(SESSION_ID))
        assertEquals(FlowContinuation.Continue::class, continuation::class)
    }

    @Test
    fun `Flow Engine returns error to flow when in ERROR state`() {
        whenever(checkpoint.getSessionState(sessionState.sessionId)).thenReturn(sessionState.apply {
            status = SessionStateType.ERROR
        })
        val continuation = sessionConfirmationWaitingForHandler.runOrContinue(inputContext, CounterPartyFlowInfo(SESSION_ID))
        assertEquals(FlowContinuation.Error::class, continuation::class)
    }

    @Test
    fun `Flow Engine resumes the flow when the session is in a state which confirms initiation`() {
        whenever(checkpoint.getSessionState(sessionState.sessionId)).thenReturn(sessionState.apply {
            status = SessionStateType.CONFIRMED
            counterpartySessionProperties = emptyKeyValuePairList()
        })

        val continuation = sessionConfirmationWaitingForHandler.runOrContinue(inputContext, CounterPartyFlowInfo(SESSION_ID))
        assertEquals(FlowContinuation.Run::class, continuation::class)
    }
}
