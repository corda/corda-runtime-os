package net.corda.flow.rest.impl

import net.corda.data.flow.output.FlowStates
import net.corda.data.rest.ExecuteFlowStatusCleanup
import net.corda.data.rest.FlowStatusRecord
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.ScheduledTask.SCHEDULED_TASK_TOPIC_FLOW_STATUS_PROCESSOR
import net.corda.schema.Schemas.ScheduledTask.SCHEDULE_TASK_NAME_FLOW_STATUS_CLEANUP
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FlowStatusCleanupProcessorTest {
    private lateinit var flowStatusCleanupProcessor: FlowStatusCleanupProcessor
    private val config: SmartConfig = mock()

    private val inputRecords = listOf(
        Record(SCHEDULED_TASK_TOPIC_FLOW_STATUS_PROCESSOR, SCHEDULE_TASK_NAME_FLOW_STATUS_CLEANUP, ScheduledTaskTrigger())
    )

    @Test
    fun `Test onNext returns an empty list when nothing is returned from the StateManager`() {
        val stateManager: StateManager = mock {
            whenever(it.findUpdatedBetweenWithMetadataMatchingAny(any(), any())).thenReturn(emptyMap())
        }

        flowStatusCleanupProcessor = FlowStatusCleanupProcessor(config, stateManager)
        val output = flowStatusCleanupProcessor.onNext(inputRecords)

        assertThat(output).isEqualTo(emptyList<Record<*, *>>())
    }

    @Test
    fun `Test onNext returns a record containing the expected batch of records`() {
        val stateManager: StateManager = mock {
            whenever(it.findUpdatedBetweenWithMetadataMatchingAny(any(), any())).thenReturn(getStates(3))
        }

        flowStatusCleanupProcessor = FlowStatusCleanupProcessor(config, stateManager)
        val output = flowStatusCleanupProcessor.onNext(inputRecords)

        assertThat(output.size).isEqualTo(1)
        assertThat(output[0].value)
            .isEqualTo(
                ExecuteFlowStatusCleanup(
                    listOf(
                        FlowStatusRecord("key0", 0),
                        FlowStatusRecord("key1", 0),
                        FlowStatusRecord("key2", 0)
                    )
                )
            )
    }

    @Test
    fun `Test onNext returns multiple batches when the number of records is greater than the configured batch size`() {
        val stateManager: StateManager = mock {
            whenever(it.findUpdatedBetweenWithMetadataMatchingAny(any(), any())).thenReturn(getStates(3))
        }

        flowStatusCleanupProcessor = FlowStatusCleanupProcessor(config, stateManager, batchSize = 2)

        val output = flowStatusCleanupProcessor.onNext(inputRecords)

        assertThat(output.size).isEqualTo(2)
        assertThat(output.map { it.value }).isEqualTo(
            listOf(
                ExecuteFlowStatusCleanup(
                    listOf(
                        FlowStatusRecord("key0", 0),
                        FlowStatusRecord("key1", 0)
                    )
                ),
                ExecuteFlowStatusCleanup(
                    listOf(
                        FlowStatusRecord("key2", 0)
                    )
                )
            )
        )
    }

    private fun getStates(count: Int, flowState: FlowStates = FlowStates.COMPLETED) =
        (0 until count).associate { i ->
            "key$i" to State(
                key = "key$i",
                value = "value".toByteArray(),
                metadata = Metadata(mapOf("flowStatus" to flowState.name))
            )
        }
}
