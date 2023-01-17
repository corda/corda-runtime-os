package net.corda.flow.pipeline.impl

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
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KillFlowContextProcessorImplTest {

    private companion object {
        const val SESSION_ID_1 = "s1"
        const val SESSION_ID_2 = "s2"
        const val SESSION_ID_3 = "s3"
        const val SESSION_ID_4 = "s4"
        const val SESSION_ID_5 = "s5"
        const val SESSION_ID_6 = "s6"
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
    private val flowRecordFactory = mock<FlowRecordFactory>()
    private val flowMessageFactory = mock<FlowMessageFactory>()
    private val flowKey = FlowKey("id", HoldingIdentity("x500", "grp1"))
    private val checkpoint = mock<FlowCheckpoint> {
        whenever(it.flowKey).thenReturn(flowKey)
    }
    private val flowTerminationDetails = mapOf("reason" to "Flow killed reasoning.")
    private val flowKilledStatus = FlowStatus().apply {
        key = checkpoint.flowKey
        flowId = checkpoint.flowId
        flowStatus = FlowStates.KILLED
        processingTerminationDetails = flowTerminationDetails
    }
    private val flowKilledStatusRecord = Record("s", flowKey, flowKilledStatus)
    private val killFlowContextProcessor = KillFlowContextProcessorImpl(flowMessageFactory, flowRecordFactory)

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        whenever(checkpoint.sessions).thenReturn(listOf(sessionState1, sessionState2))
        whenever(checkpoint.flowKey).thenReturn(FlowKey(FLOW_ID_1, ALICE_X500_HOLDING_IDENTITY))
        whenever(checkpoint.doesExist).thenReturn(true)

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
    fun `createKillFlowContext creates scheduled cleanup for all sessions`() {
        val testContext = buildFlowEventContext(
            checkpoint,
            Any()
        )

        sessionState1.status = SessionStateType.CLOSING
        sessionState1.hasScheduledCleanup = true
        sessionState2.status = SessionStateType.ERROR
        sessionState2.hasScheduledCleanup = true

        val flowSessions = listOf(sessionState1, sessionState2, sessionState3, sessionState4, sessionState5, sessionState6)
        whenever(checkpoint.sessions).thenReturn(flowSessions)
        whenever(flowRecordFactory.createFlowMapperEventRecord(eq("s1"), any())).thenReturn(scheduleCleanupRecord1)
        whenever(flowRecordFactory.createFlowMapperEventRecord(eq("s4"), any())).thenReturn(scheduleCleanupRecord4)
        whenever(flowRecordFactory.createFlowMapperEventRecord(eq("s5"), any())).thenReturn(scheduleCleanupRecord5)
        whenever(flowRecordFactory.createFlowMapperEventRecord(eq("s6"), any())).thenReturn(scheduleCleanupRecord6)

        val outputContext = killFlowContextProcessor.createKillFlowContext(testContext, flowTerminationDetails)

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
        assertTrue(flowSessions.all { it.hasScheduledCleanup })
    }

    @Test
    fun `createKillFlowContext removes retries and external events`() {
        val externalEventState = ExternalEventState()
        val testContext = buildFlowEventContext(
            checkpoint,
            Any()
        )

        whenever(checkpoint.sessions).thenReturn(emptyList())
        whenever(checkpoint.externalEventState).thenReturn(externalEventState)
        whenever(checkpoint.inRetryState).thenReturn(true)

        val outputContext = killFlowContextProcessor.createKillFlowContext(testContext, flowTerminationDetails)

        assertThat(outputContext.outputRecords)
            .withFailMessage("Output records should contain the flow killed status record")
            .contains(flowKilledStatusRecord)
        assertThat(outputContext.outputRecords)
            .withFailMessage("The external event record, retry record, and all other records should have been discarded")
            .hasSize(1)
    }
}
