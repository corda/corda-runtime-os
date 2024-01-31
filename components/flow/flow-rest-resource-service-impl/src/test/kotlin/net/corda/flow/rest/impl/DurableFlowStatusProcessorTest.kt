package net.corda.flow.rest.impl

import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateOperationGroup
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.FLOW_STATUS_TOPIC
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class DurableFlowStatusProcessorTest {
    private lateinit var flowStatusProcessor: DurableFlowStatusProcessor
    private lateinit var stateManager: StateManager

    private val serializer = mock<CordaAvroSerializer<Any>>()

    @BeforeEach
    fun setup() {
        stateManager = getMockStateManager()

        whenever(serializer.serialize(any())).thenReturn("test".toByteArray())
        flowStatusProcessor = DurableFlowStatusProcessor(stateManager, serializer)
    }

    @Test
    fun `Test durable subscription key class is flow status key`() {
        assertThat(flowStatusProcessor.keyClass).isEqualTo(FlowKey::class.java)
    }

    @Test
    fun `Test durable subscription value class is flow status`() {
        assertThat(flowStatusProcessor.valueClass).isEqualTo(FlowStatus::class.java)
    }

    @Test
    fun `Test onNext creates a new record in the StateManager`() {
        val record = Record(FLOW_STATUS_TOPIC, FlowStatusLookupServiceImplTest.FLOW_KEY_1, createFlowStatus())
        val key = FlowStatusLookupServiceImplTest.FLOW_KEY_1.toString()

        flowStatusProcessor.onNext(listOf(record))

        val result = stateManager.get(setOf(key))

        assertThat(result.size).isEqualTo(1)
        assertThat(result.containsKey(key)).isTrue()
        assertThat(result[key]?.version).isEqualTo(0)
    }

    @Test
    fun `Test onNext creates multiple records in the StateManager on different keys`() {
        val record1 = Record(FLOW_STATUS_TOPIC, FlowStatusLookupServiceImplTest.FLOW_KEY_1, createFlowStatus())
        val record2 = Record(FLOW_STATUS_TOPIC, FlowStatusLookupServiceImplTest.FLOW_KEY_2, createFlowStatus())
        val key1 = FlowStatusLookupServiceImplTest.FLOW_KEY_1.toString()
        val key2 = FlowStatusLookupServiceImplTest.FLOW_KEY_2.toString()

        flowStatusProcessor.onNext(listOf(record1, record2))

        val result = stateManager.get(setOf(key1, key2))

        assertThat(result.size).isEqualTo(2)
        assertThat(result.containsKey(key1)).isTrue()
        assertThat(result.containsKey(key2)).isTrue()
        assertThat(result[key1]?.version).isEqualTo(0)
        assertThat(result[key2]?.version).isEqualTo(0)
    }

    @Test
    fun `Test onNext updates existing key if it already exists`() {
        val record1 = Record(
            FLOW_STATUS_TOPIC, FlowStatusLookupServiceImplTest.FLOW_KEY_1, createFlowStatus(
                FlowStates.START_REQUESTED))
        val record2 = Record(
            FLOW_STATUS_TOPIC, FlowStatusLookupServiceImplTest.FLOW_KEY_1, createFlowStatus(
                FlowStates.RUNNING))
        val key = FlowStatusLookupServiceImplTest.FLOW_KEY_1.toString()

        flowStatusProcessor.onNext(listOf(record1))

        val result1 = stateManager.get(setOf(key))

        assertThat(result1.size).isEqualTo(1)
        assertThat(result1.containsKey(key)).isTrue()
        assertThat(result1[key]?.version).isEqualTo(0)

        flowStatusProcessor.onNext(listOf(record2))

        val result2 = stateManager.get(setOf(key))

        assertThat(result2.size).isEqualTo(1)
        assertThat(result2.containsKey(key)).isTrue()
        assertThat(result2[key]?.version).isEqualTo(1)
    }

    @Test
    fun `Test onNext processes a create and an update in a single call`() {
        val record1 = Record(
            FLOW_STATUS_TOPIC, FlowStatusLookupServiceImplTest.FLOW_KEY_1, createFlowStatus(
                FlowStates.START_REQUESTED))
        val record2 = Record(
            FLOW_STATUS_TOPIC, FlowStatusLookupServiceImplTest.FLOW_KEY_1, createFlowStatus(
                FlowStates.RUNNING))
        val record3 = Record(FLOW_STATUS_TOPIC, FlowStatusLookupServiceImplTest.FLOW_KEY_2, createFlowStatus())

        val key1 = FlowStatusLookupServiceImplTest.FLOW_KEY_1.toString()
        val key2 = FlowStatusLookupServiceImplTest.FLOW_KEY_2.toString()

        // Persist our first record
        flowStatusProcessor.onNext(listOf(record1))

        val result1 = stateManager.get(setOf(key1))

        assertThat(result1.size).isEqualTo(1)
        assertThat(result1.containsKey(key1)).isTrue()
        assertThat(result1[key1]?.version).isEqualTo(0)

        // Persist a new record, and update our first
        flowStatusProcessor.onNext(listOf(record2, record3))

        val result2 = stateManager.get(setOf(key1, key2))

        assertThat(result2.size).isEqualTo(2)

        // Assert that our record on FLOW_KEY_1 has incremented its version
        assertThat(result2.containsKey(key1)).isTrue()
        assertThat(result2[key1]?.version).isEqualTo(1)

        // And that our record on FLOW_KEY_1 has been created
        assertThat(result2.containsKey(key2)).isTrue()
        assertThat(result2[key2]?.version).isEqualTo(0)
    }

    private fun createFlowStatus(flowStatus: FlowStates = FlowStates.START_REQUESTED) =
        FlowStatus().apply { initiatorType = FlowInitiatorType.RPC; this.flowStatus = flowStatus }

    private fun getMockStateManager(): StateManager {
        return object : StateManager {
            private val stateStore = mutableMapOf<String, State>()
            override val name = LifecycleCoordinatorName("MockStateManager")

            override fun create(states: Collection<State>): Set<String> {
                val failedKeys = mutableSetOf<String>()

                states.forEach { state ->
                    if (state.key in stateStore) {
                        failedKeys.add(state.key)
                    } else {
                        stateStore[state.key] = state.copy(modifiedTime = Instant.now())
                    }
                }

                return failedKeys
            }

            override fun get(keys: Collection<String>): Map<String, State> {
                return keys.mapNotNull { key -> stateStore[key]?.let { key to it } }.toMap()
            }

            override fun update(states: Collection<State>): Map<String, State?> {
                val failedUpdates = mutableMapOf<String, State?>()

                states.forEach { state ->
                    val currentState = stateStore[state.key]
                    if (currentState == null || currentState.version != state.version) {
                        // State does not exist or version mismatch
                        failedUpdates[state.key] = currentState
                    } else {
                        // Optimistic locking condition met
                        val updatedState = state.copy(version = currentState.version + 1, modifiedTime = Instant.now())
                        stateStore[state.key] = updatedState
                    }
                }

                return failedUpdates
            }

            override fun delete(states: Collection<State>): Map<String, State> {
                val failedDeletion = mutableMapOf<String, State>()

                states.forEach { state ->
                    val currentState = stateStore[state.key]
                    if (currentState != null && currentState.version == state.version) {
                        stateStore.remove(state.key)
                    } else {
                        currentState?.let { failedDeletion[state.key] = currentState }
                    }
                }

                return failedDeletion
            }

            override fun updatedBetween(interval: IntervalFilter): Map<String, State> {
                TODO("Not yet implemented")
            }

            override fun findByMetadataMatchingAll(filters: Collection<MetadataFilter>): Map<String, State> {
                TODO("Not yet implemented")
            }

            override fun findByMetadataMatchingAny(filters: Collection<MetadataFilter>): Map<String, State> {
                TODO("Not yet implemented")
            }

            override fun findUpdatedBetweenWithMetadataMatchingAll(
                intervalFilter: IntervalFilter,
                metadataFilters: Collection<MetadataFilter>
            ): Map<String, State> {
                TODO("Not yet implemented")
            }

            override fun findUpdatedBetweenWithMetadataMatchingAny(
                intervalFilter: IntervalFilter,
                metadataFilters: Collection<MetadataFilter>
            ): Map<String, State> {
                TODO("Not yet implemented")
            }

            override fun createOperationGroup(): StateOperationGroup {
                TODO("Not yet implemented")
            }

            override val isRunning = true

            override fun start() { }

            override fun stop() { }
        }
    }
}
