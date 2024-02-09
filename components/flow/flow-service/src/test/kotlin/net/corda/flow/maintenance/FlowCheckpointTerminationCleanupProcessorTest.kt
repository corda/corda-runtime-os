package net.corda.flow.maintenance

import net.corda.data.flow.FlowCheckpointTermination
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowCheckpointTerminationCleanupProcessorTest {

    private val stateManager = mock<StateManager>()

    @Test
    fun `when checkpoint termination provided with some ids to clean up, state manager is called to delete them`() {
        val keys = listOf("key1", "key2", "key3")
        val states = keys.map {
            State(it, byteArrayOf())
        }
        val keyToStateMap = keys.zip(states).toMap()
        val event = FlowCheckpointTermination(keys)
        val record = Record(Schemas.Flow.FLOW_CHECKPOINT_TERMINATION, "foo", event)
        whenever(stateManager.get(keys)).thenReturn(keyToStateMap)
        whenever(stateManager.delete(states)).thenReturn(mapOf())
        FlowCheckpointTerminationCleanupProcessor(stateManager).onNext(listOf(record))
        verify(stateManager).get(keys)
        verify(stateManager).delete(keyToStateMap.values)
    }
}