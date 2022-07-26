package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.Wakeup
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
class SubFlowFailedRequestHandlerTest {

    private companion object {
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

    private val flowError = Exception()
    private val flowStackItem = FlowStackItem.newBuilder()
        .setFlowName("FLOW_NAME")
        .setIsInitiatingFlow(true)
        .setSessionIds(sessions)
        .build()
    private val ioRequest = FlowIORequest.SubFlowFailed(flowError, flowStackItem)
    private val record = Record("", "", FlowEvent())
    private val testContext = RequestHandlerTestContext(Any())
    private val handler = SubFlowFailedRequestHandler(testContext.flowSessionManager, testContext.flowRecordFactory)

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        val flowCheckpoint = testContext.flowCheckpoint

        whenever(flowCheckpoint.getSessionState(SESSION_ID_1)).thenReturn(sessionState1)
        whenever(flowCheckpoint.getSessionState(SESSION_ID_2)).thenReturn(sessionState2)
        whenever(flowCheckpoint.getSessionState(SESSION_ID_3)).thenReturn(sessionState3)

        whenever(testContext.flowRecordFactory.createFlowEventRecord(any(), any())).thenReturn(record)
    }

    @Test
    fun `Updates the waiting for to Wakeup`() {
        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest)
        assertEquals(Wakeup(), waitingFor.value)
    }

    @ParameterizedTest(name = "Sends session error messages and creates a Wakeup record when the flow has no closed or errored sessions (isInitiatingFlow={0})")
    @MethodSource("isInitiatingFlow")
    fun `Sends session error messages and creates a Wakeup record when the flow has no closed or errored sessions`(isInitiatingFlow: Boolean) {
        flowStackItem.isInitiatingFlow = isInitiatingFlow
        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.ERROR
            )
        ).thenReturn(emptyList())
        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.CLOSED
            )
        ).thenReturn(emptyList())
        whenever(
            testContext.flowSessionManager.sendErrorMessages(
                eq(testContext.flowCheckpoint),
                eq(sessions),
                eq(flowError),
                any()
            )
        ).thenReturn(listOf(sessionState1, sessionState2, sessionState3))

        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)
        assertThat(outputContext.outputRecords).containsOnly(record)
        verify(testContext.flowCheckpoint).putSessionState(sessionState1)
        verify(testContext.flowCheckpoint).putSessionState(sessionState2)
        verify(testContext.flowCheckpoint).putSessionState(sessionState3)
        verify(testContext.flowSessionManager).sendErrorMessages(eq(testContext.flowCheckpoint), eq(sessions), eq(flowError), any())
    }

    @ParameterizedTest(name = "Sends session error messages to non-closed and non-errored sessions and creates a Wakeup record when the flow has sessions to error (isInitiatingFlow={0})")
    @MethodSource("isInitiatingFlow")
    fun `Sends session error messages to non-closed and non-errored sessions and creates a Wakeup record when the flow has sessions to error`(isInitiatingFlow: Boolean) {
        flowStackItem.isInitiatingFlow = isInitiatingFlow
        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.ERROR
            )
        ).thenReturn(listOf(sessionState1))
        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                sessions,
                SessionStateType.CLOSED
            )
        ).thenReturn(listOf(sessionState2))
        whenever(
            testContext.flowSessionManager.sendErrorMessages(
                eq(testContext.flowCheckpoint),
                eq(listOf(SESSION_ID_3)),
                eq(flowError),
                any()
            )
        ).thenReturn(listOf(sessionState3))

        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)
        assertThat(outputContext.outputRecords).containsOnly(record)
        verify(testContext.flowCheckpoint, never()).putSessionState(sessionState1)
        verify(testContext.flowCheckpoint, never()).putSessionState(sessionState2)
        verify(testContext.flowCheckpoint).putSessionState(sessionState3)
        verify(testContext.flowSessionManager).sendErrorMessages(
            eq(testContext.flowCheckpoint),
            eq(listOf(SESSION_ID_3)),
            eq(flowError),
            any()
        )
    }

    @ParameterizedTest(name = "Sends no session error messages and creates a Wakeup record when the flow has no sessions to error (isInitiatingFlow={0})")
    @MethodSource("isInitiatingFlow")
    fun `Sends no session error messages and creates a Wakeup record when the flow has no sessions to error`(isInitiatingFlow: Boolean) {
        flowStackItem.isInitiatingFlow = isInitiatingFlow
        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                emptyList(),
                SessionStateType.ERROR
            )
        ).thenReturn(emptyList())
        whenever(
            testContext.flowSessionManager.getSessionsWithStatus(
                testContext.flowCheckpoint,
                emptyList(),
                SessionStateType.CLOSED
            )
        ).thenReturn(emptyList())
        whenever(
            testContext.flowSessionManager.sendErrorMessages(
                eq(testContext.flowCheckpoint),
                eq(emptyList()),
                eq(flowError),
                any()
            )
        ).thenReturn(emptyList())

        flowStackItem.sessionIds = emptyList()
        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)
        assertThat(outputContext.outputRecords).containsOnly(record)
        verify(testContext.flowCheckpoint, never()).putSessionState(any())
        verify(testContext.flowSessionManager).sendErrorMessages(
            eq(testContext.flowCheckpoint),
            eq(emptyList()),
            eq(flowError),
            any()
        )
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

        assertThrows<FlowFatalException> { handler.postProcess(testContext.flowEventContext, ioRequest) }
    }
}

