package net.corda.session.mapper.service.executor

import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CleanupProcessorTest {

    private val stateManager = mock<StateManager>()

    @Test
    fun `when execute cleanup provided with some ids to clean up, state manager is called to delete them`() {
        val keys = listOf("key1", "key2", "key3")
        val states = keys.map {
            State(it, byteArrayOf())
        }
        val keyToStateMap = keys.zip(states).toMap()
        val event = ExecuteCleanup(keys)
        val record = Record(Schemas.Flow.FLOW_MAPPER_CLEANUP_TOPIC, "foo", event)
        whenever(stateManager.get(keys)).thenReturn(keyToStateMap)
        whenever(stateManager.delete(states)).thenReturn(mapOf())
        CleanupProcessor(stateManager).onNext(listOf(record))
        verify(stateManager).get(keys)
        verify(stateManager).delete(keyToStateMap.values)
    }
}