package net.corda.flow.state

import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.data.flow.state.checkpoint.FlowState
import net.corda.data.flow.state.external.ExternalEventState
import net.corda.data.flow.state.external.ExternalEventStateStatus
import net.corda.data.flow.state.external.ExternalEventStateType
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.*
import net.corda.flow.state.impl.FlowStateManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.nio.ByteBuffer
import java.time.Instant


class FlowStateManagerTest {
    @Suppress("LongParameterList")
    private fun createFlowState(
        key: FlowKey = FlowKey(FLOW_ID_1, BOB_X500_HOLDING_IDENTITY),
        holdingIdentity: HoldingIdentity = BOB_X500_HOLDING_IDENTITY,
        stackItems: List<FlowStackItem> = listOf(),
        sessionStates: List<SessionState> = listOf(),
        newFiber: ByteBuffer = ByteBuffer.wrap(byteArrayOf()),
        suspendedOn: String = "foo",
        waitingFor: WaitingFor = WaitingFor(Wakeup()),
        suspendCount: Int = 0,
        externalEventState: ExternalEventState? = null
    ): FlowState {

        val startContext = FlowStartContext().apply {
            statusKey = key
            identity = holdingIdentity
        }

        return FlowState().apply {
            flowStartContext = startContext
            flowStackItems = stackItems.toMutableList()
            sessions = sessionStates.toMutableList()
            fiber = newFiber
            this.suspendedOn = suspendedOn
            this.waitingFor = waitingFor
            this.suspendCount = suspendCount
            this.externalEventState = externalEventState
        }
    }

    @Test
    fun `Rollback successfully restores sessions to initial state after adding additional sessions`() {
        val initialSessions = listOf(SessionState().apply {
            sessionId = "foo"
        })

        val flowState = createFlowState(sessionStates = initialSessions)
        val sut = FlowStateManager(flowState)

        assertIterableEquals(initialSessions, sut.sessions)

        val newSession = SessionState().apply { sessionId = "bar" }
        sut.putSessionState(newSession)

        assertIterableEquals(initialSessions + newSession, sut.sessions)

        sut.rollback()
        assertIterableEquals(initialSessions, sut.sessions)
    }

    @Test
    fun `Rollback successfully restores sessions to initial state after modifying existing session`() {
        val flowState = createFlowState(sessionStates = listOf(
            SessionState().apply { sessionId = "foo" }
        ))

        val sut = FlowStateManager(flowState)

        sut.getSessionState("foo").apply { this?.sessionId = "bar" }

        assertIterableEquals(
            listOf(SessionState().apply {sessionId = "bar"}),
            sut.sessions
        )

        sut.rollback()

        assertIterableEquals(
            listOf(SessionState().apply {sessionId = "foo"}),
            sut.sessions
        )
    }

    @Test
    fun `External event state is successfully retained through a rollback`() {
        val mockExternalEventState = ExternalEventState().apply {
            requestId = REQUEST_ID_1
            status = ExternalEventStateStatus(ExternalEventStateType.OK, null)
            eventToSend = mock()
            retries = 0
            factoryClassName = "none"
            sendTimestamp = Instant.now()
            response = ExternalEventResponse()
        }

        val stateBuilder = ExternalEventState.newBuilder(mockExternalEventState)

        val flowState = createFlowState(sessionStates = listOf(
            SessionState().apply{ sessionId = SESSION_ID_1 }
        )).apply { externalEventState = stateBuilder.build() }

        val sut = FlowStateManager(flowState)

        assertEquals(
            stateBuilder.build().apply { requestId = REQUEST_ID_1 },
            sut.externalEventState
        )

        sut.externalEventState?.apply { requestId = REQUEST_ID_2 }

        sut.rollback()

        assertEquals(
            stateBuilder.build().apply { requestId = REQUEST_ID_2 },
            sut.externalEventState
        )
    }

    @Test
    fun `Valid FlowState successfully converted to avro`() {
        val flowState = createFlowState(sessionStates = listOf(
            SessionState().apply {sessionId = "foo"}
        ))

        val sut = FlowStateManager(flowState)

        assertEquals(flowState, sut.toAvro())
    }
}
