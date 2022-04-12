package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStackItem
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.test.flow.util.buildSessionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@Suppress("MaxLineLength")
class SubFlowFinishedRequestHandlerTest {

    private companion object {
        const val FLOW_NAME = "flow name"
        const val FLOW_ID = "flow id"
        const val SESSION_ID = "session id"
        const val ANOTHER_SESSION_ID = "another session id"
        val HOLDING_IDENTITY = HoldingIdentity("x500 name", "group id")
        val FLOW_KEY = FlowKey(FLOW_ID, HOLDING_IDENTITY)
        val sessions = listOf(SESSION_ID, ANOTHER_SESSION_ID)
    }

    private val sessionState = buildSessionState(
        SessionStateType.CONFIRMED, 0, mutableListOf(), 0, mutableListOf(), sessionId = SESSION_ID
    )

    private val anotherSessionState = buildSessionState(
        SessionStateType.CONFIRMED, 0, mutableListOf(), 0, mutableListOf(), sessionId = ANOTHER_SESSION_ID
    )

    private val updatedSessionState = buildSessionState(
        SessionStateType.CONFIRMED, 0, mutableListOf(), 1, mutableListOf(), sessionId = SESSION_ID
    )

    private val anotherUpdatedSessionState = buildSessionState(
        SessionStateType.CONFIRMED, 0, mutableListOf(), 1, mutableListOf(), sessionId = ANOTHER_SESSION_ID
    )

    private val flowSessionManager = mock<FlowSessionManager>().apply {
        whenever(sendCloseMessages(any(), eq(sessions), any())).thenReturn(listOf(updatedSessionState, anotherUpdatedSessionState))
    }

    private val subFlowFinishedRequestHandler = SubFlowFinishedRequestHandler(flowSessionManager)

    @Test
    fun `Returns an updated WaitingFor of SessionConfirmation (Close) when the flow is an initiating flow and has sessions to close`() {
        val checkpoint = Checkpoint().apply {
            flowKey = FLOW_KEY
            sessions = listOf(sessionState, anotherSessionState)
        }

        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint, inputEventPayload = Unit)

        val result = subFlowFinishedRequestHandler.getUpdatedWaitingFor(
            inputContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(true)
                    .setSessionIds(sessions.toList())
                    .build()
            )
        )

        assertEquals(SessionConfirmation(sessions.toList(), SessionConfirmationType.CLOSE), result.value)
    }

    @Test
    fun `Returns an updated WaitingFor of Wakeup when the flow is not an initiating flow`() {
        val checkpoint = Checkpoint().apply {
            flowKey = FLOW_KEY
            sessions = listOf(sessionState, anotherSessionState)
        }

        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint, inputEventPayload = Unit)

        val result = subFlowFinishedRequestHandler.getUpdatedWaitingFor(
            inputContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(false)
                    .setSessionIds(sessions.toList())
                    .build()
            )
        )

        assertEquals(net.corda.data.flow.state.waiting.Wakeup(), result.value)
    }

    @Test
    fun `Returns an updated WaitingFor of Wakeup when the flow is an initiating flow and has no sessions to close`() {
        val checkpoint = Checkpoint().apply {
            flowKey = FLOW_KEY
            sessions = listOf(sessionState, anotherSessionState)
        }

        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint, inputEventPayload = Unit)

        val result = subFlowFinishedRequestHandler.getUpdatedWaitingFor(
            inputContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(true)
                    .setSessionIds(emptyList())
                    .build()
            )
        )

        assertEquals(net.corda.data.flow.state.waiting.Wakeup(), result.value)
    }

    @Test
    fun `Returns an updated WaitingFor of Wakeup when the flow is an initiating flow and has already closed sessions`() {
        val checkpoint = Checkpoint().apply {
            flowKey = FLOW_KEY
            sessions = listOf(sessionState, anotherSessionState)
        }

        whenever(flowSessionManager.areAllSessionsInStatuses(eq(checkpoint), eq(sessions), any())).thenReturn(true)

        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint, inputEventPayload = Unit)

        val result = subFlowFinishedRequestHandler.getUpdatedWaitingFor(
            inputContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(true)
                    .setSessionIds(emptyList())
                    .build()
            )
        )

        assertEquals(net.corda.data.flow.state.waiting.Wakeup(), result.value)
    }

    @Test
    fun `Updates the checkpoint's sessions with session close messages to send when the flow is an initiating flow and has sessions to close`() {
        val checkpoint = Checkpoint().apply {
            flowKey = FLOW_KEY
            sessions = listOf(sessionState, anotherSessionState)
        }
        val checkpointCopy = Checkpoint().apply {
            flowKey = checkpoint.flowKey
            fiber = checkpoint.fiber
            sessions = checkpoint.sessions
        }

        whenever(flowSessionManager.areAllSessionsInStatuses(eq(checkpointCopy), eq(sessions), any())).thenReturn(false)

        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint = checkpointCopy, inputEventPayload = Unit)

        val outputContext = subFlowFinishedRequestHandler.postProcess(
            inputContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(true)
                    .setSessionIds(sessions.toList())
                    .build()
            )
        )

        assertNotEquals(checkpoint, outputContext.checkpoint)
        assertNotEquals(sessionState, outputContext.checkpoint?.sessions?.get(0))
        assertNotEquals(anotherSessionState, outputContext.checkpoint?.sessions?.get(1))
    }

    @Test
    fun `Does not schedule any events when the flow is an initiating flow and has sessions to close`() {
        val checkpoint = Checkpoint().apply {
            flowKey = FLOW_KEY
            sessions = listOf(sessionState, anotherSessionState)
        }
        val checkpointCopy = Checkpoint().apply {
            flowKey = checkpoint.flowKey
            fiber = checkpoint.fiber
            sessions = checkpoint.sessions
        }

        whenever(flowSessionManager.areAllSessionsInStatuses(eq(checkpointCopy), eq(sessions), any())).thenReturn(false)

        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint = checkpointCopy, inputEventPayload = Unit)

        val outputContext = subFlowFinishedRequestHandler.postProcess(
            inputContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(true)
                    .setSessionIds(sessions.toList())
                    .build()
            )
        )

        assertEquals(emptyList<Record<FlowKey, FlowEvent>>(), outputContext.outputRecords)
    }

    @Test
    fun `Schedules a wakeup event when the flow is not an initiating flow`() {
        val checkpoint = Checkpoint().apply {
            flowKey = FLOW_KEY
            sessions = listOf(sessionState, anotherSessionState)
        }
        val checkpointCopy = Checkpoint().apply {
            flowKey = checkpoint.flowKey
            fiber = checkpoint.fiber
            sessions = checkpoint.sessions
        }

        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint = checkpointCopy, inputEventPayload = Unit)

        val outputContext = subFlowFinishedRequestHandler.postProcess(
            inputContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(false)
                    .setSessionIds(emptyList())
                    .build()
            )
        )

        assertEquals(
            listOf(Record(Schemas.Flow.FLOW_EVENT_TOPIC, FLOW_KEY, FlowEvent(FLOW_KEY, Wakeup()))),
            outputContext.outputRecords
        )
    }

    @Test
    fun `Does not update the checkpoint when the flow is not an initiating flow`() {
        val checkpoint = Checkpoint().apply {
            flowKey = FLOW_KEY
            sessions = listOf(sessionState, anotherSessionState)
        }
        val checkpointCopy = Checkpoint().apply {
            flowKey = checkpoint.flowKey
            fiber = checkpoint.fiber
            sessions = checkpoint.sessions
        }

        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint = checkpointCopy, inputEventPayload = Unit)

        val outputContext = subFlowFinishedRequestHandler.postProcess(
            inputContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(false)
                    .setSessionIds(emptyList())
                    .build()
            )
        )

        assertEquals(checkpoint, outputContext.checkpoint)
    }

    @Test
    fun `Schedules a wakeup event when the flow is an initiating flow and has no sessions to close`() {
        val checkpoint = Checkpoint().apply {
            flowKey = FLOW_KEY
            sessions = listOf(sessionState, anotherSessionState)
        }
        val checkpointCopy = Checkpoint().apply {
            flowKey = checkpoint.flowKey
            fiber = checkpoint.fiber
            sessions = checkpoint.sessions
        }

        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint = checkpointCopy, inputEventPayload = Unit)

        val outputContext = subFlowFinishedRequestHandler.postProcess(
            inputContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(true)
                    .setSessionIds(emptyList())
                    .build()
            )
        )

        assertEquals(
            listOf(Record(Schemas.Flow.FLOW_EVENT_TOPIC, FLOW_KEY, FlowEvent(FLOW_KEY, Wakeup()))),
            outputContext.outputRecords
        )
    }

    @Test
    fun `Does not update the checkpoint when the flow is an initiating flow and has no sessions to close`() {
        val checkpoint = Checkpoint().apply {
            flowKey = FLOW_KEY
            sessions = listOf(sessionState, anotherSessionState)
        }
        val checkpointCopy = Checkpoint().apply {
            flowKey = checkpoint.flowKey
            fiber = checkpoint.fiber
            sessions = checkpoint.sessions
        }

        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint = checkpointCopy, inputEventPayload = Unit)

        val outputContext = subFlowFinishedRequestHandler.postProcess(
            inputContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(false)
                    .setSessionIds(emptyList())
                    .build()
            )
        )

        assertEquals(checkpoint, outputContext.checkpoint)
    }

    @Test
    fun `Schedules a wakeup event when the flow is an initiating flow and has already closed sessions`() {
        val checkpoint = Checkpoint().apply {
            flowKey = FLOW_KEY
            sessions = listOf(sessionState, anotherSessionState)
        }
        val checkpointCopy = Checkpoint().apply {
            flowKey = checkpoint.flowKey
            fiber = checkpoint.fiber
            sessions = checkpoint.sessions
        }

        whenever(flowSessionManager.areAllSessionsInStatuses(eq(checkpointCopy), eq(sessions), any())).thenReturn(true)

        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint = checkpointCopy, inputEventPayload = Unit)

        val outputContext = subFlowFinishedRequestHandler.postProcess(
            inputContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(true)
                    .setSessionIds(emptyList())
                    .build()
            )
        )

        assertEquals(
            listOf(Record(Schemas.Flow.FLOW_EVENT_TOPIC, FLOW_KEY, FlowEvent(FLOW_KEY, Wakeup()))),
            outputContext.outputRecords
        )
    }

    @Test
    fun `Does not update the checkpoint when the flow is an initiating flow and has already closed sessions`() {
        val checkpoint = Checkpoint().apply {
            flowKey = FLOW_KEY
            sessions = listOf(sessionState, anotherSessionState)
        }
        val checkpointCopy = Checkpoint().apply {
            flowKey = checkpoint.flowKey
            fiber = checkpoint.fiber
            sessions = checkpoint.sessions
        }

        whenever(flowSessionManager.areAllSessionsInStatuses(eq(checkpointCopy), eq(sessions), any())).thenReturn(true)

        val inputContext: FlowEventContext<Any> = buildFlowEventContext(checkpoint = checkpointCopy, inputEventPayload = Unit)

        val outputContext = subFlowFinishedRequestHandler.postProcess(
            inputContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(true)
                    .setSessionIds(emptyList())
                    .build()
            )
        )

        assertEquals(checkpoint, outputContext.checkpoint)
    }
}