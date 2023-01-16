package net.corda.flow.pipeline.impl

import java.util.stream.Stream
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.data.flow.state.external.ExternalEventState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.ALICE_X500_HOLDING_IDENTITY
import net.corda.flow.FLOW_ID_1
import net.corda.flow.REQUEST_ID_1
import net.corda.flow.external.events.impl.ExternalEventManager
import net.corda.flow.pipeline.FlowTerminatedContext
import net.corda.flow.pipeline.FlowTerminatedContext.Companion.TERMINATION_REASON_KEY
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.messaging.api.records.Record
import net.corda.session.manager.SessionManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.provider.Arguments
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowGlobalPostProcessorImplTest {

    private companion object {
        const val SESSION_ID_1 = "s1"
        const val SESSION_ID_2 = "s2"
        const val SESSION_ID_3 = "s3"
        const val SESSION_ID_4 = "s4"
        const val SESSION_ID_5 = "s5"
        const val SESSION_ID_6 = "s6"

        @JvmStatic
        fun sessionStatuses(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(SessionStateType.CLOSED, SessionStateType.CONFIRMED),
                Arguments.of(SessionStateType.ERROR, SessionStateType.CONFIRMED),
                Arguments.of(SessionStateType.CLOSED, SessionStateType.ERROR),
            )
        }
    }

    private val sessionState1 = SessionState().apply {
        this.sessionId = SESSION_ID_1
        this.hasScheduledCleanup = false
    }
    private val sessionState2 = SessionState().apply {
        this.sessionId = SESSION_ID_2
        this.hasScheduledCleanup = false
    }
    private val sessionState3 = SessionState().apply {
        sessionId = SESSION_ID_3
        hasScheduledCleanup = true
        status = SessionStateType.CLOSED
    }
    private val sessionState4 = SessionState().apply {
        sessionId = SESSION_ID_4
        hasScheduledCleanup = false
        status = SessionStateType.CONFIRMED
    }
    private val sessionState5 = SessionState().apply {
        sessionId = SESSION_ID_5
        hasScheduledCleanup = false
        status = SessionStateType.CREATED
    }
    private val sessionState6 = SessionState().apply {
        sessionId = SESSION_ID_6
        hasScheduledCleanup = false
        status = SessionStateType.WAIT_FOR_FINAL_ACK
    }
    private val sessionEvent1 = SessionEvent().apply {
        this.sessionId = SESSION_ID_1
        this.sequenceNum = 1
    }
    private val sessionEvent2 = SessionEvent().apply {
        this.sessionId = SESSION_ID_1
        this.sequenceNum = 2
    }
    private val sessionEvent3 = SessionEvent().apply {
        this.sessionId = SESSION_ID_2
        this.sequenceNum = 1
    }
    private val sessionRecord1 = Record("t", SESSION_ID_1, FlowMapperEvent(sessionEvent1))
    private val sessionRecord2 = Record("t", SESSION_ID_1, FlowMapperEvent(sessionEvent2))
    private val sessionRecord3 = Record("t", SESSION_ID_2, FlowMapperEvent(sessionEvent3))
    private val scheduleCleanupRecord1 = Record("t", SESSION_ID_1, FlowMapperEvent(ScheduleCleanup(1000)))
    private val scheduleCleanupRecord2 = Record("t", SESSION_ID_2, FlowMapperEvent(ScheduleCleanup(1000)))
    private val scheduleCleanupRecord4 = Record("t", SESSION_ID_4, FlowMapperEvent(ScheduleCleanup(1000)))
    private val scheduleCleanupRecord5 = Record("t", SESSION_ID_5, FlowMapperEvent(ScheduleCleanup(1000)))
    private val scheduleCleanupRecord6 = Record("t", SESSION_ID_6, FlowMapperEvent(ScheduleCleanup(1000)))
    private val externalEventRecord = Record("t", "key", byteArrayOf(1, 2, 3))
    private val sessionManager = mock<SessionManager>()
    private val externalEventManager = mock<ExternalEventManager>()
    private val flowRecordFactory = mock<FlowRecordFactory>()
    private val flowMessageFactory = mock<FlowMessageFactory>()
    private val flowKey = FlowKey("id", HoldingIdentity("x500", "grp1"))
    private val checkpoint = mock<FlowCheckpoint> {
        whenever(it.flowKey).thenReturn(flowKey)
    }
    private val testContext = buildFlowEventContext(checkpoint, Any())
    private val flowGlobalPostProcessor = FlowGlobalPostProcessorImpl(
        externalEventManager,
        sessionManager,
        flowMessageFactory,
        flowRecordFactory
    )
    private val flowTerminationDetails = mapOf(TERMINATION_REASON_KEY to "Flow killed reasoning.")
    private val flowKilledStatus = FlowStatus().apply {
        key = checkpoint.flowKey
        flowId = checkpoint.flowId
        flowStatus = FlowStates.KILLED
        processingTerminationDetails = flowTerminationDetails
    }
    private val flowKilledStatusRecord = Record("s", flowKey, flowKilledStatus)

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        whenever(checkpoint.sessions).thenReturn(listOf(sessionState1, sessionState2))
        whenever(checkpoint.flowKey).thenReturn(FlowKey(FLOW_ID_1, ALICE_X500_HOLDING_IDENTITY))
        whenever(checkpoint.doesExist).thenReturn(true)
        whenever(
            sessionManager.getMessagesToSend(
                eq(sessionState1),
                any(),
                eq(testContext.config),
                eq(ALICE_X500_HOLDING_IDENTITY)
            )
        ).thenReturn(sessionState1 to listOf(sessionEvent1, sessionEvent2))
        whenever(
            sessionManager.getMessagesToSend(
                eq(sessionState2),
                any(),
                eq(testContext.config),
                eq(ALICE_X500_HOLDING_IDENTITY)
            )
        ).thenReturn(sessionState2 to listOf(sessionEvent3))

        whenever(flowRecordFactory.createFlowMapperEventRecord(sessionEvent1.sessionId, sessionEvent1)).thenReturn(
            sessionRecord1
        )
        whenever(flowRecordFactory.createFlowMapperEventRecord(sessionEvent2.sessionId, sessionEvent2)).thenReturn(
            sessionRecord2
        )
        whenever(flowRecordFactory.createFlowMapperEventRecord(sessionEvent3.sessionId, sessionEvent3)).thenReturn(
            sessionRecord3
        )
        whenever(flowRecordFactory.createFlowMapperEventRecord(eq(SESSION_ID_1), any<ScheduleCleanup>())).thenReturn(
            scheduleCleanupRecord1
        )
        whenever(flowRecordFactory.createFlowMapperEventRecord(eq(SESSION_ID_2), any<ScheduleCleanup>())).thenReturn(
            scheduleCleanupRecord2
        )
        whenever(flowMessageFactory.createFlowKilledStatusMessage(any(), any())).thenReturn(
            flowKilledStatus
        )
        whenever(flowRecordFactory.createFlowStatusRecord(flowKilledStatus)).thenReturn(
            flowKilledStatusRecord
        )
    }

    @Test
    fun `Adds output records containing session events to send to peers`() {
        val outputContext = flowGlobalPostProcessor.postProcess(testContext)

        assertThat(outputContext.outputRecords).containsOnly(sessionRecord1, sessionRecord2, sessionRecord3)
    }

    @Test
    fun `Updates session states`() {
        flowGlobalPostProcessor.postProcess(testContext)
        verify(checkpoint).putSessionState(sessionState1)
        verify(checkpoint).putSessionState(sessionState2)
    }

    @Test
    fun `Does not update sessions when the checkpoint has been deleted`() {
        whenever(checkpoint.doesExist).thenReturn(false)
        flowGlobalPostProcessor.postProcess(testContext)
        verify(checkpoint, never()).putSessionState(sessionState1)
        verify(checkpoint, never()).putSessionState(sessionState2)
    }

    @Test
    fun `Adds output records containing schedule cleanup events when there are CLOSED sessions`() {
        sessionState1.status = SessionStateType.CLOSED
        sessionState2.status = SessionStateType.CONFIRMED

        val outputContext = flowGlobalPostProcessor.postProcess(testContext)

        assertThat(outputContext.outputRecords).contains(scheduleCleanupRecord1)
        assertThat(outputContext.outputRecords).doesNotContain(scheduleCleanupRecord2)
        assertTrue(sessionState1.hasScheduledCleanup)
        assertFalse(sessionState2.hasScheduledCleanup)
    }

    @Test
    fun `Adds output records containing schedule cleanup events when there are ERRORed sessions`() {
        sessionState1.status = SessionStateType.ERROR
        sessionState2.status = SessionStateType.CONFIRMED

        val outputContext = flowGlobalPostProcessor.postProcess(testContext)

        assertThat(outputContext.outputRecords).contains(scheduleCleanupRecord1)
        assertThat(outputContext.outputRecords).doesNotContain(scheduleCleanupRecord2)
        assertTrue(sessionState1.hasScheduledCleanup)
        assertFalse(sessionState2.hasScheduledCleanup)
    }

    @Test
    fun `Adds output records containing schedule cleanup events when there are CLOSED and ERRORed sessions`() {
        sessionState1.status = SessionStateType.CLOSED
        sessionState2.status = SessionStateType.ERROR

        val outputContext = flowGlobalPostProcessor.postProcess(testContext)

        assertThat(outputContext.outputRecords).contains(scheduleCleanupRecord1, scheduleCleanupRecord2)
        assertTrue(sessionState1.hasScheduledCleanup)
        assertTrue(sessionState2.hasScheduledCleanup)
    }

    @Test
    fun `Adds no output records containing schedule cleanup events when there are no CLOSED or ERRORed or sessions`() {
        sessionState1.status = SessionStateType.WAIT_FOR_FINAL_ACK
        sessionState2.status = SessionStateType.CONFIRMED

        val outputContext = flowGlobalPostProcessor.postProcess(testContext)

        assertThat(outputContext.outputRecords).doesNotContain(scheduleCleanupRecord1, scheduleCleanupRecord2)
        assertFalse(sessionState1.hasScheduledCleanup)
        assertFalse(sessionState2.hasScheduledCleanup)
    }

    @Test
    fun `Adds output records containing schedule cleanup events when when the checkpoint has been deleted`() {
        whenever(checkpoint.doesExist).thenReturn(false)
        sessionState1.status = SessionStateType.CLOSED
        sessionState2.status = SessionStateType.ERROR

        val outputContext = flowGlobalPostProcessor.postProcess(testContext)

        assertThat(outputContext.outputRecords).contains(scheduleCleanupRecord1, scheduleCleanupRecord2)
        assertTrue(sessionState1.hasScheduledCleanup)
        assertTrue(sessionState2.hasScheduledCleanup)
    }

    @Test
    fun `Clears pending platform errors`() {
        flowGlobalPostProcessor.postProcess(testContext)

        verify(checkpoint).clearPendingPlatformError()
    }

    @Test
    fun `Adds external event record when there is an external event to send`() {
        val externalEventState = ExternalEventState()
        val updatedExternalEventState = ExternalEventState().apply { REQUEST_ID_1 }

        whenever(checkpoint.externalEventState).thenReturn(externalEventState)
        whenever(externalEventManager.getEventToSend(eq(externalEventState), any(), eq(testContext.config)))
            .thenReturn(updatedExternalEventState to externalEventRecord)

        val outputContext = flowGlobalPostProcessor.postProcess(testContext)

        assertThat(outputContext.outputRecords).contains(externalEventRecord)
        verify(checkpoint).externalEventState = updatedExternalEventState
    }

    @Test
    fun `Does not add an external event record when there is no external event to send`() {
        val externalEventState = ExternalEventState()
        val updatedExternalEventState = ExternalEventState().apply { REQUEST_ID_1 }

        whenever(checkpoint.externalEventState).thenReturn(externalEventState)
        whenever(externalEventManager.getEventToSend(eq(externalEventState), any(), eq(testContext.config)))
            .thenReturn(updatedExternalEventState to null)

        val outputContext = flowGlobalPostProcessor.postProcess(testContext)

        assertThat(outputContext.outputRecords).doesNotContain(externalEventRecord)
        verify(checkpoint).externalEventState = updatedExternalEventState
    }

    @Test
    fun `Does not add an external event record when there is no external event state`() {
        whenever(checkpoint.externalEventState).thenReturn(null)

        val outputContext = flowGlobalPostProcessor.postProcess(testContext)

        assertThat(outputContext.outputRecords).doesNotContain(externalEventRecord)
        verify(externalEventManager, never()).getEventToSend(any(), any(), any())
        verify(checkpoint, never()).externalEventState = any()
    }

    @Test
    fun `When flow is to be killed, all necessary sessions scheduled for cleanup and flow killed status created`() {
        val externalEventState = ExternalEventState()
        val testContext = buildFlowEventContext(
            checkpoint,
            Any(),
            flowTerminatedContext = FlowTerminatedContext(
                FlowTerminatedContext.TerminationStatus.TO_BE_KILLED,
                flowTerminationDetails
            )
        )

        sessionState1.status = SessionStateType.CLOSING
        sessionState1.hasScheduledCleanup = true
        sessionState2.status = SessionStateType.ERROR
        sessionState2.hasScheduledCleanup = true

        val flowSessions = listOf(sessionState1, sessionState2, sessionState3, sessionState4, sessionState5, sessionState6)
        whenever(checkpoint.sessions).thenReturn(flowSessions)
        whenever(checkpoint.externalEventState).thenReturn(externalEventState)
        whenever(checkpoint.inRetryState).thenReturn(true)
        whenever(flowRecordFactory.createFlowMapperEventRecord(eq("s1"), any())).thenReturn(scheduleCleanupRecord1)
        whenever(flowRecordFactory.createFlowMapperEventRecord(eq("s4"), any())).thenReturn(scheduleCleanupRecord4)
        whenever(flowRecordFactory.createFlowMapperEventRecord(eq("s5"), any())).thenReturn(scheduleCleanupRecord5)
        whenever(flowRecordFactory.createFlowMapperEventRecord(eq("s6"), any())).thenReturn(scheduleCleanupRecord6)


        val outputContext = flowGlobalPostProcessor.postProcess(testContext)

        // s2 status is ERROR and s3 status is CLOSED therefore are not appearing in this list
        assertThat(outputContext.outputRecords)
            .withFailMessage("Output records should contain cleanup records for flow sessions that aren't CLOSED or ERRORED")
            .contains(
                scheduleCleanupRecord1,
                scheduleCleanupRecord4,
                scheduleCleanupRecord5,
                scheduleCleanupRecord6,
            )
        assertThat(outputContext.outputRecords)
            .withFailMessage("Output records should contain the flow killed status record")
            .contains(flowKilledStatusRecord)
        assertThat(outputContext.outputRecords)
            .withFailMessage("The external event record, retry record, and all other records should have been discarded")
            .hasSize(5)
        assertTrue(flowSessions.all { it.hasScheduledCleanup })
    }
}
