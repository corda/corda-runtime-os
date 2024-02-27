package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.output.FlowStatus
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.SESSION_ID_1
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowProcessingExceptionTypes.FLOW_FAILED
import net.corda.flow.pipeline.sessions.FlowSessionStateException
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowFailedRequestHandlerTest {

    private companion object {
        val FLOW_KEY = FlowKey(SESSION_ID_1, BOB_X500_HOLDING_IDENTITY)

        const val SESSION_ID_2 = "s2"
        const val SESSION_ID_3 = "s3"
        val SESSION_IDS = listOf(SESSION_ID_1, SESSION_ID_2, SESSION_ID_3)
    }

    private val sessionState1 = SessionState().apply { this.sessionId = SESSION_ID_1 }
    private val sessionState2 = SessionState().apply { this.sessionId = SESSION_ID_2; this.status = SessionStateType.CLOSED }
    private val sessionState3 = SessionState().apply { this.sessionId = SESSION_ID_3; this.status = SessionStateType.ERROR }
    private val record = Record("", "", FlowEvent())

    private val testContext = RequestHandlerTestContext(Any())
    private val flowError = Exception("error message")
    private val ioRequest = FlowIORequest.FlowFailed(flowError)
    private val handler = FlowFailedRequestHandler(
        testContext.flowMessageFactory,
        testContext.flowRecordFactory,
        testContext.flowSessionManager
    )

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        val flowCheckpoint = testContext.flowCheckpoint

        whenever(flowCheckpoint.getSessionState(SESSION_ID_1)).thenReturn(sessionState1)
        whenever(flowCheckpoint.getSessionState(SESSION_ID_2)).thenReturn(sessionState2)
        whenever(flowCheckpoint.getSessionState(SESSION_ID_3)).thenReturn(sessionState3)

        whenever(testContext.flowRecordFactory.createFlowEventRecord(any(), any())).thenReturn(record)
        whenever(testContext.flowRecordFactory.createFlowStatusRecord(any())).thenReturn(Record("", FLOW_KEY, null))

        whenever(testContext.flowCheckpoint.flowKey).thenReturn(FLOW_KEY)
    }

    @Test
    fun `Updates the waiting for to nothing`() {
        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest)
        assertThat(waitingFor?.value).isNull()
    }

    @Test
    fun `post processing marks context as deleted`() {
        val flowStatus = FlowStatus()
        whenever(testContext.flowCheckpoint.flowKey).thenReturn(FLOW_KEY)
        whenever(testContext.flowMessageFactory.createFlowFailedStatusMessage(
            testContext.flowCheckpoint,
            FLOW_FAILED,
            "error message"
        )).thenReturn(flowStatus)
        handler.postProcess(testContext.flowEventContext, ioRequest)
        verify(testContext.flowCheckpoint).markDeleted()
    }

    @Test
    fun `post processing publishes status update and does not schedule flow cleanup`() {
        val statusRecord = Record("", FLOW_KEY, FlowStatus())
        val cleanupRecord = Record("", FLOW_KEY.toString(), FlowMapperEvent())
        val flowStatus = FlowStatus()

        whenever(testContext.flowMessageFactory.createFlowFailedStatusMessage(
            testContext.flowCheckpoint,
            FLOW_FAILED,
            "error message"
        )).thenReturn(flowStatus)

        whenever(testContext.flowRecordFactory.createFlowStatusRecord(flowStatus)).thenReturn(statusRecord)
        whenever(testContext.flowRecordFactory.createFlowMapperEventRecord(eq(FLOW_KEY.toString()), any<ScheduleCleanup>()))
            .thenReturn(cleanupRecord)
        whenever(testContext.flowCheckpoint.flowKey).thenReturn(FLOW_KEY)

        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)
        assertThat(outputContext.outputRecords).containsOnly(statusRecord)
    }

    @Test
    fun `Sends session error messages to non-closed and non-errored sessions`() {
        val statusRecord = Record("", FLOW_KEY, FlowStatus())
        val cleanupRecord = Record("", FLOW_KEY.toString(), FlowMapperEvent())
        val flowStatus = FlowStatus()

        whenever(
            testContext.flowSessionManager.getSessionsWithStatuses(
                testContext.flowCheckpoint,
                SESSION_IDS,
                setOf(SessionStateType.ERROR, SessionStateType.CLOSED)
            )
        ).thenReturn(listOf(sessionState1))
        whenever(
            testContext.flowSessionManager.sendErrorMessages(
                eq(testContext.flowCheckpoint),
                eq(listOf(SESSION_ID_2, SESSION_ID_3)),
                eq(flowError),
                any()
            )
        ).thenReturn(listOf(sessionState3))

        whenever(testContext.flowMessageFactory.createFlowFailedStatusMessage(
            testContext.flowCheckpoint,
            FLOW_FAILED,
            "error message"
        )).thenReturn(flowStatus)

        whenever(testContext.flowRecordFactory.createFlowStatusRecord(flowStatus)).thenReturn(statusRecord)
        whenever(testContext.flowRecordFactory.createFlowMapperEventRecord(eq(FLOW_KEY.toString()), any<ScheduleCleanup>()))
            .thenReturn(cleanupRecord)
        whenever(testContext.flowCheckpoint.flowKey).thenReturn(FLOW_KEY)

        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)
        assertThat(outputContext.outputRecords).containsOnly(statusRecord)
    }

    @Test
    fun `Throws exception when session does not exist within checkpoint`() {
        whenever(
            testContext.flowSessionManager.getSessionsWithStatuses(
                eq(testContext.flowCheckpoint),
                any(),
                ArgumentMatchers.anySet()
            )
        ).thenThrow(FlowSessionStateException("Session does not exist"))

        assertThrows<FlowFatalException> { handler.postProcess(testContext.flowEventContext, ioRequest) }
    }
}