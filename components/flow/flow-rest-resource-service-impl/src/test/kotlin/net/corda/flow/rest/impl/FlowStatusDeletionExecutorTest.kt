package net.corda.flow.rest.impl

import net.corda.data.rest.ExecuteFlowStatusCleanup
import net.corda.data.rest.FlowStatusRecord
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Rest.REST_FLOW_STATUS_CLEANUP_TOPIC
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class FlowStatusDeletionExecutorTest {
    private lateinit var flowStatusDeletionExecutor: FlowStatusDeletionExecutor
    private lateinit var stateManager: StateManager
    private lateinit var stateCaptor: KArgumentCaptor<List<State>>

    @BeforeEach
    fun setup() {
        stateManager = mock()
        flowStatusDeletionExecutor = FlowStatusDeletionExecutor(stateManager)
        stateCaptor = argumentCaptor<List<State>>()
    }

    @Test
    fun `onNext with a single ExecuteFlowStatusCleanup record containing 0 keys does not call delete`() {
        val inputRecords = getCleanupRecords(1, 0)
        flowStatusDeletionExecutor.onNext(inputRecords)

        verify(stateManager, never()).delete(any())
    }

    @Test
    fun `onNext with a single ExecuteFlowStatusCleanup record containing 3 keys calls delete once with 3 keys`() {
        val inputRecords = getCleanupRecords(1, 3)
        flowStatusDeletionExecutor.onNext(inputRecords)

        verify(stateManager, times(1)).delete(stateCaptor.capture())

        val capturedStates = stateCaptor.firstValue

        assertThat(capturedStates).hasSize(3)
        assertThat(capturedStates.map { it.key })
            .containsExactly("record_key_0", "record_key_1", "record_key_2")
    }

    @Test
    fun `onNext with a multiple ExecuteFlowStatusCleanup records calls delete multiple times`() {
        val inputRecords = getCleanupRecords(3, 3)
        flowStatusDeletionExecutor.onNext(inputRecords)

        verify(stateManager, times(3)).delete(stateCaptor.capture())

        val allCapturedStates = stateCaptor.allValues

        assertThat(allCapturedStates).hasSize(3)
        allCapturedStates.forEach { capturedStates ->
            assertThat(capturedStates.map { it.key }).containsExactly("record_key_0", "record_key_1", "record_key_2")
        }
    }

    private fun getFlowStatusRecords(count: Int) =
        (0 until count).map { i ->
            FlowStatusRecord("record_key_$i", 0)
        }

    private fun getCleanupRecords(recordCount: Int, statusCountPerRecord: Int) =
        (0 until recordCount).map { i ->
            Record(
                REST_FLOW_STATUS_CLEANUP_TOPIC,
                "cleanup_key_$i",
                ExecuteFlowStatusCleanup(getFlowStatusRecords(statusCountPerRecord))
            )
        }
}
