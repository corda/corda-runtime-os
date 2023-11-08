package net.corda.flow.pipeline.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.cache.FlowFiberCache
import net.corda.flow.maintenance.CheckpointCleanupHandler
import net.corda.flow.pipeline.exceptions.FlowMarkedForKillException
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.FlowConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowToBeKilledExceptionProcessingTest {

    private companion object {
        const val SESSION_ID_1 = "s1"
        const val SESSION_ID_2 = "s2"
        const val SESSION_ID_3 = "s3"
        const val SESSION_ID_4 = "s4"
        const val SESSION_ID_5 = "s5"
        const val FLOW_ID = "flowId"
    }

    private val flowRecordFactory = mock<FlowRecordFactory>()
    private val flowMessageFactory = mock<FlowMessageFactory>()
    private val flowSessionManager = mock<FlowSessionManager>()
    private val flowConfig = ConfigFactory.empty().withValue(
        FlowConfig.PROCESSING_MAX_RETRY_WINDOW_DURATION, ConfigValueFactory.fromAnyRef(20000L)
    )
    private val smartFlowConfig = SmartConfigFactory.createWithoutSecurityServices().create(flowConfig)

    // starting session states
    private val sessionState1 = createSessionState(SESSION_ID_1, true, SessionStateType.CLOSING)
    private val sessionState2 = createSessionState(SESSION_ID_2, true, SessionStateType.ERROR)
    private val sessionState3 = createSessionState(SESSION_ID_3, true, SessionStateType.CLOSED)
    private val sessionState4 = createSessionState(SESSION_ID_4, false, SessionStateType.CONFIRMED)
    private val sessionState5 = createSessionState(SESSION_ID_5, false, SessionStateType.CREATED)

    // session states which have had their state updated to ERROR
    private val erroredSessionState1 = createSessionState(SESSION_ID_1, false, SessionStateType.ERROR)
    private val erroredSessionState4 = createSessionState(SESSION_ID_4, false, SessionStateType.ERROR)
    private val erroredSessionState5 = createSessionState(SESSION_ID_5, false, SessionStateType.ERROR)

    private val allFlowSessions = listOf(sessionState1, sessionState2, sessionState3, sessionState4, sessionState5)
    private val activeFlowSessionIds = listOf(SESSION_ID_1, SESSION_ID_4, SESSION_ID_5)
    private val allFlowSessionsAfterErrorsSent = listOf(
        erroredSessionState1, sessionState2, sessionState3, erroredSessionState4, erroredSessionState5
    )

    private fun createSessionState(sessionId: String, hasScheduledCleanup: Boolean, status: SessionStateType): SessionState =
        SessionState().apply {
            this.sessionId = sessionId
            this.hasScheduledCleanup = hasScheduledCleanup
            this.status = status
        }

    private val flowKey = FlowKey("id", HoldingIdentity("x500", "grp1"))
    private val checkpoint = mock<FlowCheckpoint> {
        whenever(it.flowKey).thenReturn(flowKey)
    }
    private val flowKilledStatus = FlowStatus().apply {
        key = checkpoint.flowKey
        flowId = checkpoint.flowId
        flowStatus = FlowStates.KILLED
        processingTerminatedReason = "reason"
    }
    private val flowKilledStatusRecord = Record("s", flowKey, flowKilledStatus)
    private val flowFiberCache = mock<FlowFiberCache>()
    private val checkpointCleanupHandler = mock<CheckpointCleanupHandler>()

    private val target = FlowEventExceptionProcessorImpl(
        flowMessageFactory,
        flowRecordFactory,
        flowSessionManager,
        flowFiberCache,
        checkpointCleanupHandler
    )

    @BeforeEach
    fun setup() {
        target.configure(smartFlowConfig)
        whenever(checkpoint.flowId).thenReturn(FLOW_ID)
    }

    @Test
    fun `processing FlowMarkedForKillException calls checkpoint cleanup handler and copies cleanup records to context`() {
        val testContext = buildFlowEventContext(checkpoint, Any())
        val exception = FlowMarkedForKillException("reasoning")

        whenever(checkpoint.doesExist).thenReturn(true)

        val flowMapperEvent = mock<FlowMapperEvent>()
        val cleanupRecord = Record(Schemas.Flow.FLOW_MAPPER_SESSION_OUT, "key", flowMapperEvent)
        val cleanupRecords = listOf (cleanupRecord)
        whenever(checkpointCleanupHandler.cleanupCheckpoint(any(), any(), any())).thenReturn(cleanupRecords)

        val response = target.process(exception, testContext)

        assertThat(response.outputRecords)
            .contains(cleanupRecord)
    }

    @Test
    fun `processing FlowMarkedForKillException when checkpoint does not exist only outputs flow killed status record`() {
        whenever(checkpoint.doesExist).thenReturn(false)

        val inputEventPayload = StartFlow(FlowStartContext().apply {statusKey = flowKey}, "")

        val testContext = buildFlowEventContext(checkpoint, inputEventPayload)
        val exception = FlowMarkedForKillException("reasoning")

        whenever(flowRecordFactory.createFlowStatusRecord(any())).thenReturn(flowKilledStatusRecord)

        val response = target.process(exception, testContext)

        assertThat(response.outputRecords)
            .hasSize(1)
            .contains(flowKilledStatusRecord)
    }

    @Test
    fun `error processing FlowMarkedForKillException falls back to null state record, empty response events and marked for DLQ`() {
        val testContext = buildFlowEventContext(checkpoint, Any())
        val exception = FlowMarkedForKillException("reasoning")


        whenever(checkpoint.doesExist).thenReturn(true)

        // The first call returns all sessions.
        whenever(checkpoint.sessions)
            .thenReturn(allFlowSessions)
            .thenReturn(allFlowSessionsAfterErrorsSent)

        // simulating exception thrown during processing of the flow session
        whenever(flowSessionManager.sendErrorMessages(eq(checkpoint), eq(activeFlowSessionIds), eq(exception), any()))
            .thenThrow(IllegalArgumentException("some error message while sending errors to peers"))

        val response = target.process(exception, testContext)

        verify(response.checkpoint).markDeleted()
        assertThat(response.outputRecords).isEmpty()
        assertThat(response.sendToDlq).isTrue
    }
}
