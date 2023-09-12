package net.corda.flow.pipeline.handlers.waiting.sessions

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.CounterPartyFlowInfo
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.flow.utils.emptyKeyValuePairList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@Suppress("MaxLineLength")
class CounterpartyFlowInfoWaitingForHandlerTest {

    private companion object {
        const val SESSION_ID = "session id"
        const val ANOTHER_SESSION_ID = "another session id"
    }

    private val checkpoint = mock<FlowCheckpoint>()
    private val sessionState = SessionState()
    private val anotherSessionState = SessionState()
    private val counterpartyFlowInfoWaitingForHandler = CounterpartyFlowInfoWaitingForHandler()

    val inputContext = buildFlowEventContext(
        checkpoint = checkpoint,
        inputEventPayload = SessionEvent().apply {
            sessionId = SESSION_ID
            payload = SessionData()
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
    fun `Returns error to flow when the session does not exist`() {
        whenever(checkpoint.getSessionState(sessionState.sessionId)).thenReturn(null)
        val continuation = counterpartyFlowInfoWaitingForHandler.runOrContinue(inputContext, CounterPartyFlowInfo(SESSION_ID))
        assertEquals(FlowContinuation.Error::class, continuation::class)
    }

    @Test
    fun `Flow Engine continues to wait when in CREATED state`() {
        whenever(checkpoint.getSessionState(sessionState.sessionId)).thenReturn(sessionState.apply {
            status = SessionStateType.CREATED
        })
        val continuation = counterpartyFlowInfoWaitingForHandler.runOrContinue(inputContext, CounterPartyFlowInfo(SESSION_ID))
        assertEquals(FlowContinuation.Continue::class, continuation::class)
    }

    @Test
    fun `Flow Engine returns error to flow when in ERROR state`() {
        whenever(checkpoint.getSessionState(sessionState.sessionId)).thenReturn(sessionState.apply {
            status = SessionStateType.ERROR
        })
        val continuation = counterpartyFlowInfoWaitingForHandler.runOrContinue(inputContext, CounterPartyFlowInfo(SESSION_ID))
        assertEquals(FlowContinuation.Error::class, continuation::class)
    }

    @Test
    fun `Flow Engine resumes the flow when the session is in a state which confirms initiation`() {
        whenever(checkpoint.getSessionState(sessionState.sessionId)).thenReturn(sessionState.apply {
            status = SessionStateType.CONFIRMED
            sessionProperties = emptyKeyValuePairList()
        })

        val continuation = counterpartyFlowInfoWaitingForHandler.runOrContinue(inputContext, CounterPartyFlowInfo(SESSION_ID))
        assertEquals(FlowContinuation.Run::class, continuation::class)
    }
}
