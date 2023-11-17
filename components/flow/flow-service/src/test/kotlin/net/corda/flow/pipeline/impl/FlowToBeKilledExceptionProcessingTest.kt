package net.corda.flow.pipeline.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FlowToBeKilledExceptionProcessingTest {
    private companion object {
        const val FLOW_ID = "flowId"
    }

    private val flowRecordFactory = mock<FlowRecordFactory>()
    private val flowMessageFactory = mock<FlowMessageFactory>()
    private val flowSessionManager = mock<FlowSessionManager>()
    private val flowConfig = ConfigFactory.empty().withValue(
        FlowConfig.PROCESSING_MAX_RETRY_WINDOW_DURATION, ConfigValueFactory.fromAnyRef(20000L)
    )
    private val smartFlowConfig = SmartConfigFactory.createWithoutSecurityServices().create(flowConfig)
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
        flowMessageFactory, flowRecordFactory, flowSessionManager, flowFiberCache, checkpointCleanupHandler
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
        val cleanupRecords = listOf(cleanupRecord)
        whenever(checkpointCleanupHandler.cleanupCheckpoint(any(), any(), any())).thenReturn(cleanupRecords)

        val response = target.process(exception, testContext)

        assertThat(response.outputRecords).contains(cleanupRecord)
    }

    @Test
    fun `processing FlowMarkedForKillException when checkpoint does not exist only outputs flow killed status record`() {
        whenever(checkpoint.doesExist).thenReturn(false)
        val inputEventPayload = StartFlow(FlowStartContext().apply { statusKey = flowKey }, "")
        val testContext = buildFlowEventContext(checkpoint, inputEventPayload)
        val exception = FlowMarkedForKillException("reasoning")
        whenever(flowRecordFactory.createFlowStatusRecord(any())).thenReturn(flowKilledStatusRecord)

        val response = target.process(exception, testContext)

        assertThat(response.outputRecords).hasSize(1).contains(flowKilledStatusRecord)
    }

    @Test
    fun `error processing FlowMarkedForKillException falls back to null state record, empty response events and marked for DLQ`() {
        val testContext = buildFlowEventContext(checkpoint, Any())
        val exception = FlowMarkedForKillException("reasoning")
        whenever(checkpoint.doesExist).thenReturn(true)
        // simulating exception thrown during processing
        whenever(
            checkpointCleanupHandler.cleanupCheckpoint(
                any(),
                any(),
                any()
            )
        ).thenThrow(IllegalArgumentException("some error message while sending errors to peers"))

        val response = target.process(exception, testContext)

        assertThat(response.outputRecords).isEmpty()
        assertThat(response.sendToDlq).isTrue
    }
}
