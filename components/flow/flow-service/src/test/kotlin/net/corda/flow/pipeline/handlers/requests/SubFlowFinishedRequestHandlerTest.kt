package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.FlowStackItem
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.fiber.FlowIORequest
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Suppress("MaxLineLength")
class SubFlowFinishedRequestHandlerTest {

    private companion object {
        const val FLOW_NAME = "flow name"
        const val SESSION_ID_1 = "s1"
        const val SESSION_ID_2 = "s2"
        val sessions = listOf(SESSION_ID_1, SESSION_ID_2)
    }

    private val sessionState1 = SessionState().apply { this.sessionId = SESSION_ID_1 }
    private val sessionState2 = SessionState().apply { this.sessionId = SESSION_ID_2 }
    private val flowStackItem = FlowStackItem()
    private val ioRequest = FlowIORequest.SubFlowFinished(flowStackItem)
    private val record = Record("","", FlowEvent())
    private val testContext = RequestHandlerTestContext(Any())
    private val flowSessionManager = testContext.flowSessionManager
    private val handler = SubFlowFinishedRequestHandler(flowSessionManager, testContext.recordFactory)
    
    @BeforeEach
    fun setup() {
        whenever(flowSessionManager.sendCloseMessages(any(), eq(sessions), any())).thenReturn(listOf(sessionState1, sessionState2))
    }

    @Test
    fun `post processing publishes wakeup event`() {
        val eventRecord = Record("","", FlowEvent())

        whenever(testContext
            .recordFactory
            .createFlowEventRecord(eq(testContext.flowId), any<Wakeup>() )
        ).thenReturn(eventRecord)

        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)
        assertThat(outputContext.outputRecords).containsOnly(eventRecord)
    }
    
    @Test
    fun `Returns an updated WaitingFor of SessionConfirmation (Close) when the flow is an initiating flow and has sessions to close`() {
        whenever(flowSessionManager.areAllSessionsInStatuses(eq(testContext.flowCheckpoint), eq(sessions), any())).thenReturn(false)
        val result = handler.getUpdatedWaitingFor(
            testContext.flowEventContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(true)
                    .setSessionIds(sessions)
                    .build()
            )
        )

        assertEquals(SessionConfirmation(sessions, SessionConfirmationType.CLOSE), result.value)
    }

    @Test
    fun `Returns an updated WaitingFor of Wakeup when the flow is not an initiating flow`() {
        val result = handler.getUpdatedWaitingFor(
            testContext.flowEventContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(false)
                    .setSessionIds(sessions)
                    .build()
            )
        )

        assertEquals(net.corda.data.flow.state.waiting.Wakeup(), result.value)
    }

    @Test
    fun `Returns an updated WaitingFor of Wakeup when the flow is an initiating flow and has no sessions to close`() {
        val result = handler.getUpdatedWaitingFor(
            testContext.flowEventContext,
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
        whenever(flowSessionManager.areAllSessionsInStatuses(eq(testContext.flowCheckpoint), eq(sessions), any())).thenReturn(true)

        val result = handler.getUpdatedWaitingFor(
            testContext.flowEventContext,
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
    fun `Sends session close messages and does not create a Wakeup record when the flow is an initiating flow and has sessions to close`() {
        whenever(flowSessionManager.areAllSessionsInStatuses(eq(testContext.flowCheckpoint), eq(sessions), any())).thenReturn(false)

        val outputContext = handler.postProcess(
            testContext.flowEventContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(true)
                    .setSessionIds(sessions)
                    .build()
            )
        )

        verify(testContext.flowCheckpoint).putSessionState(sessionState1)
        verify(testContext.flowCheckpoint).putSessionState(sessionState2)
        verify(testContext.flowSessionManager).sendCloseMessages(eq(testContext.flowCheckpoint), eq(sessions), any())
        verify(testContext.recordFactory, never()).createFlowEventRecord(eq(testContext.flowId), any<Wakeup>())
        assertThat(outputContext.outputRecords).hasSize(0)
    }

    @Test
    fun `Does not send session close messages and creates a Wakeup record when the flow is not an initiating flow`() {
        whenever(testContext
            .recordFactory
            .createFlowEventRecord(eq(testContext.flowId), any<Wakeup>())
        ).thenReturn(record)

        val outputContext = handler.postProcess(
            testContext.flowEventContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(false)
                    .setSessionIds(emptyList())
                    .build()
            )
        )

        verify(testContext.flowCheckpoint, never()).putSessionState(sessionState1)
        verify(testContext.flowCheckpoint, never()).putSessionState(sessionState2)
        verify(testContext.flowSessionManager, never()).sendCloseMessages(eq(testContext.flowCheckpoint), eq(sessions), any())
        verify(testContext.recordFactory).createFlowEventRecord(eq(testContext.flowId), any<Wakeup>())
        assertThat(outputContext.outputRecords).containsOnly(record)
    }

    @Test
    fun `Does not send session close messages and creates a Wakeup record when the flow is an initiating flow and has no sessions to close`() {
        whenever(testContext
            .recordFactory
            .createFlowEventRecord(eq(testContext.flowId), any<Wakeup>() )
        ).thenReturn(record)

        val outputContext = handler.postProcess(
            testContext.flowEventContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(true)
                    .setSessionIds(emptyList())
                    .build()
            )
        )

        verify(testContext.flowCheckpoint, never()).putSessionState(sessionState1)
        verify(testContext.flowCheckpoint, never()).putSessionState(sessionState2)
        verify(testContext.flowSessionManager, never()).sendCloseMessages(eq(testContext.flowCheckpoint), eq(sessions), any())
        verify(testContext.recordFactory).createFlowEventRecord(eq(testContext.flowId), any<Wakeup>())
        assertThat(outputContext.outputRecords).containsOnly(record)
    }

    @Test
    fun `Does not send session close messages and creates a Wakeup record when the flow is an initiating flow and has already closed sessions`() {
        whenever(flowSessionManager.areAllSessionsInStatuses(eq(testContext.flowCheckpoint), eq(sessions), any())).thenReturn(true)

        whenever(testContext
            .recordFactory
            .createFlowEventRecord(eq(testContext.flowId), any<Wakeup>() )
        ).thenReturn(record)

        val outputContext = handler.postProcess(
            testContext.flowEventContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(true)
                    .setSessionIds(emptyList())
                    .build()
            )
        )

        verify(testContext.flowCheckpoint, never()).putSessionState(sessionState1)
        verify(testContext.flowCheckpoint, never()).putSessionState(sessionState2)
        verify(testContext.flowSessionManager, never()).sendCloseMessages(eq(testContext.flowCheckpoint), eq(sessions), any())
        verify(testContext.recordFactory).createFlowEventRecord(eq(testContext.flowId), any<Wakeup>())
        assertThat(outputContext.outputRecords).containsOnly(record)
    }
}