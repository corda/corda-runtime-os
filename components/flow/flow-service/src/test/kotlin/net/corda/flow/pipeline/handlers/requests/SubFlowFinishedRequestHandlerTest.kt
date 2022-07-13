package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.sessions.FlowSessionStateException
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.stream.Stream

@Suppress("MaxLineLength")
class SubFlowFinishedRequestHandlerTest {

    private companion object {
        const val FLOW_NAME = "flow name"
        const val SESSION_ID_1 = "s1"
        const val SESSION_ID_2 = "s2"
        const val SESSION_ID_3 = "s3"
        val sessions = listOf(SESSION_ID_1, SESSION_ID_2, SESSION_ID_3)

        @JvmStatic
        fun isInitiatingFlow(): Stream<Arguments> {
            return Stream.of(Arguments.of(true), Arguments.of(false))
        }
    }

    private val sessionState1 = SessionState().apply { this.sessionId = SESSION_ID_1 }
    private val sessionState2 = SessionState().apply { this.sessionId = SESSION_ID_2 }
    private val sessionState3 = SessionState().apply { this.sessionId = SESSION_ID_3 }
    private val sessionStates = listOf(sessionState1, sessionState2, sessionState3)

    private val record = Record("", "", FlowEvent())
    private val testContext = RequestHandlerTestContext(Any())
    private val flowSessionManager = testContext.flowSessionManager
    private val handler = SubFlowFinishedRequestHandler(flowSessionManager, testContext.flowRecordFactory)

    @BeforeEach
    fun setup() {
        whenever(
            testContext
                .flowRecordFactory
                .createFlowEventRecord(eq(testContext.flowId), any<Wakeup>())
        ).thenReturn(record)
    }

    @ParameterizedTest(name = "Returns an updated WaitingFor of SessionConfirmation (Close) when the flow has sessions to close (isInitiatingFlow={0})")
    @MethodSource("isInitiatingFlow")
    fun `Returns an updated WaitingFor of SessionConfirmation (Close) when the flow has sessions to close`(isInitiatingFlow: Boolean) {
        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.ERROR
            )
        ).thenReturn(emptyList())

        val result = handler.getUpdatedWaitingFor(
            testContext.flowEventContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(isInitiatingFlow)
                    .setSessionIds(sessions)
                    .build()
            )
        )

        assertEquals(SessionConfirmation(sessions, SessionConfirmationType.CLOSE), result.value)
    }

    @ParameterizedTest(name = "Returns an updated WaitingFor of SessionConfirmation (Close) that filters out errored sessions when the flow has sessions to close (isInitiatingFlow={0})")
    @MethodSource("isInitiatingFlow")
    fun `Returns an updated WaitingFor of SessionConfirmation (Close) that filters out errored sessions when the flow has sessions to close`(isInitiatingFlow: Boolean) {
        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.ERROR
            )
        ).thenReturn(listOf(sessionState1))

        val result = handler.getUpdatedWaitingFor(
            testContext.flowEventContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(isInitiatingFlow)
                    .setSessionIds(sessions)
                    .build()
            )
        )

        assertEquals(SessionConfirmation(listOf(SESSION_ID_2, SESSION_ID_3), SessionConfirmationType.CLOSE), result.value)
    }

    @ParameterizedTest(name = "Returns an updated WaitingFor of Wakeup when the flow has only errored sessions (isInitiatingFlow={0})")
    @MethodSource("isInitiatingFlow")
    fun `Returns an updated WaitingFor of Wakeup when the flow has only errored sessions`(isInitiatingFlow: Boolean) {
        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.ERROR
            )
        ).thenReturn(sessionStates)

        val result = handler.getUpdatedWaitingFor(
            testContext.flowEventContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(isInitiatingFlow)
                    .setSessionIds(sessions)
                    .build()
            )
        )

        assertEquals(net.corda.data.flow.state.waiting.Wakeup(), result.value)
    }

    @ParameterizedTest(name = "Returns an updated WaitingFor of Wakeup when the flow  has no sessions to close (isInitiatingFlow={0})")
    @MethodSource("isInitiatingFlow")
    fun `Returns an updated WaitingFor of Wakeup when the flow  has no sessions to close`(isInitiatingFlow: Boolean) {
        val result = handler.getUpdatedWaitingFor(
            testContext.flowEventContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(isInitiatingFlow)
                    .setSessionIds(emptyList())
                    .build()
            )
        )

        assertEquals(net.corda.data.flow.state.waiting.Wakeup(), result.value)
    }

    @ParameterizedTest(name = "Returns an updated WaitingFor of SessionConfirmation (Close) containing the flow stack item's sessions when the flow has already closed sessions (isInitiatingFlow={0})")
    @MethodSource("isInitiatingFlow")
    fun `Returns an updated WaitingFor of SessionConfirmation (Close) containing the flow stack item's sessions when the flow has already closed sessions`(isInitiatingFlow: Boolean) {
        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.ERROR
            )
        ).thenReturn(emptyList())
        whenever(flowSessionManager.sendCloseMessages(eq(testContext.flowCheckpoint), eq(sessions), any()))
            .thenReturn(sessionStates)
        whenever(
            flowSessionManager.doAllSessionsHaveStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.CLOSED
            )
        ).thenReturn(true)

        val result = handler.getUpdatedWaitingFor(
            testContext.flowEventContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(isInitiatingFlow)
                    .setSessionIds(sessions)
                    .build()
            )
        )

        assertEquals(SessionConfirmation(sessions, SessionConfirmationType.CLOSE), result.value)
    }

    @Test
    fun `Throws exception when updating WaitingFor when session does not exist within checkpoint`() {
        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.ERROR
            )
        ).thenThrow(FlowSessionStateException("Session does not exist"))

        assertThrows<FlowFatalException> {
            handler.getUpdatedWaitingFor(
                testContext.flowEventContext,
                FlowIORequest.SubFlowFinished(
                    FlowStackItem.newBuilder()
                        .setFlowName(FLOW_NAME)
                        .setIsInitiatingFlow(true)
                        .setSessionIds(sessions)
                        .build()
                )
            )
        }
    }

    @ParameterizedTest(name = "Sends session close messages to non-errored sessions and does not create a Wakeup record when the flow has sessions to close (isInitiatingFlow={0})")
    @MethodSource("isInitiatingFlow")
    fun `Sends session close messages to non-errored sessions and does not create a Wakeup record when the flow has sessions to close`(isInitiatingFlow: Boolean) {
        val nonErroredSessions = listOf(SESSION_ID_2, SESSION_ID_3)
        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.ERROR
            )
        ).thenReturn(listOf(sessionState1))
        whenever(flowSessionManager.sendCloseMessages(eq(testContext.flowCheckpoint), eq(nonErroredSessions), any()))
            .thenReturn(listOf(sessionState2, sessionState3))
        whenever(
            flowSessionManager.doAllSessionsHaveStatus(
                testContext.flowCheckpoint,
                nonErroredSessions,
                SessionStateType.CLOSED
            )
        ).thenReturn(false)

        val outputContext = handler.postProcess(
            testContext.flowEventContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(isInitiatingFlow)
                    .setSessionIds(sessions)
                    .build()
            )
        )

        verify(testContext.flowCheckpoint, never()).putSessionState(sessionState1)
        verify(testContext.flowCheckpoint).putSessionState(sessionState2)
        verify(testContext.flowCheckpoint).putSessionState(sessionState3)
        verify(testContext.flowSessionManager).sendCloseMessages(eq(testContext.flowCheckpoint), eq(nonErroredSessions), any())
        verify(testContext.flowRecordFactory, never()).createFlowEventRecord(eq(testContext.flowId), any<Wakeup>())
        assertThat(outputContext.outputRecords).hasSize(0)
    }

    @ParameterizedTest(name = "Does not send session close messages and creates a Wakeup record when the flow has no sessions to close (isInitiatingFlow={0})")
    @MethodSource("isInitiatingFlow")
    fun `Does not send session close messages and creates a Wakeup record when the flow has no sessions to close`(isInitiatingFlow: Boolean) {
        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                emptyList(),
                SessionStateType.ERROR
            )
        ).thenReturn(emptyList())
        whenever(flowSessionManager.sendCloseMessages(eq(testContext.flowCheckpoint), eq(emptyList()), any())).thenReturn(emptyList())
        whenever(
            flowSessionManager.doAllSessionsHaveStatus(
                testContext.flowCheckpoint,
                emptyList(),
                SessionStateType.CLOSED
            )
        ).thenReturn(true)

        val outputContext = handler.postProcess(
            testContext.flowEventContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(isInitiatingFlow)
                    .setSessionIds(emptyList())
                    .build()
            )
        )

        verify(testContext.flowCheckpoint, never()).putSessionState(sessionState1)
        verify(testContext.flowCheckpoint, never()).putSessionState(sessionState2)
        verify(testContext.flowCheckpoint, never()).putSessionState(sessionState3)
        verify(testContext.flowSessionManager).sendCloseMessages(eq(testContext.flowCheckpoint), eq(emptyList()), any())
        verify(testContext.flowRecordFactory).createFlowEventRecord(eq(testContext.flowId), any<Wakeup>())
        assertThat(outputContext.outputRecords).containsOnly(record)
    }

    @ParameterizedTest(name = "Does not send session close messages and creates a Wakeup record when the flow has only closed sessions (isInitiatingFlow={0})")
    @MethodSource("isInitiatingFlow")
    fun `Does not send session close messages and creates a Wakeup record when the flow has only closed sessions`(isInitiatingFlow: Boolean) {
        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.ERROR
            )
        ).thenReturn(emptyList())
        whenever(
            flowSessionManager.doAllSessionsHaveStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.CLOSED
            )
        ).thenReturn(true)
        whenever(flowSessionManager.sendCloseMessages(eq(testContext.flowCheckpoint), eq(sessions), any()))
            .thenReturn(sessionStates)

        val outputContext = handler.postProcess(
            testContext.flowEventContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(isInitiatingFlow)
                    .setSessionIds(sessions)
                    .build()
            )
        )

        verify(testContext.flowCheckpoint).putSessionState(sessionState1)
        verify(testContext.flowCheckpoint).putSessionState(sessionState2)
        verify(testContext.flowCheckpoint).putSessionState(sessionState3)
        verify(testContext.flowSessionManager).sendCloseMessages(eq(testContext.flowCheckpoint), eq(sessions), any())
        verify(testContext.flowRecordFactory).createFlowEventRecord(eq(testContext.flowId), any<Wakeup>())
        assertThat(outputContext.outputRecords).containsOnly(record)
    }

    @ParameterizedTest(name = "Does not send session close messages and creates a Wakeup record when the flow has only errored sessions (isInitiatingFlow={0})")
    @MethodSource("isInitiatingFlow")
    fun `Does not send session close messages and creates a Wakeup record when the flow has only errored sessions`(isInitiatingFlow: Boolean) {
        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.ERROR
            )
        ).thenReturn(sessionStates)
        whenever(flowSessionManager.sendCloseMessages(eq(testContext.flowCheckpoint), eq(emptyList()), any())).thenReturn(emptyList())
        whenever(
            flowSessionManager.doAllSessionsHaveStatus(
                testContext.flowCheckpoint,
                emptyList(),
                SessionStateType.CLOSED
            )
        ).thenReturn(true)

        val outputContext = handler.postProcess(
            testContext.flowEventContext,
            FlowIORequest.SubFlowFinished(
                FlowStackItem.newBuilder()
                    .setFlowName(FLOW_NAME)
                    .setIsInitiatingFlow(isInitiatingFlow)
                    .setSessionIds(sessions)
                    .build()
            )
        )

        verify(testContext.flowCheckpoint, never()).putSessionState(sessionState1)
        verify(testContext.flowCheckpoint, never()).putSessionState(sessionState2)
        verify(testContext.flowCheckpoint, never()).putSessionState(sessionState3)
        verify(testContext.flowSessionManager).sendCloseMessages(eq(testContext.flowCheckpoint), eq(emptyList()), any())
        verify(testContext.flowRecordFactory).createFlowEventRecord(eq(testContext.flowId), any<Wakeup>())
        assertThat(outputContext.outputRecords).containsOnly(record)
    }

    @Test
    fun `Throws exception when session does not exist within checkpoint`() {
        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.ERROR
            )
        ).thenThrow(FlowSessionStateException("Session does not exist"))

        assertThrows<FlowFatalException> {
            handler.postProcess(
                testContext.flowEventContext,
                FlowIORequest.SubFlowFinished(
                    FlowStackItem.newBuilder()
                        .setFlowName(FLOW_NAME)
                        .setIsInitiatingFlow(true)
                        .setSessionIds(sessions)
                        .build()
                )
            )
        }
    }
}