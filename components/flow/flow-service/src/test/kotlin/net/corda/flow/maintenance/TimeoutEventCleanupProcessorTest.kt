package net.corda.flow.maintenance

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.data.flow.FlowTimeout
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.state.impl.FlowCheckpointFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.Instant

class TimeoutEventCleanupProcessorTest {
    private val checkpointCleanupHandler = mock<CheckpointCleanupHandler>()
    private val stateManager = mock<StateManager>()
    private val avroDeserializer = mock<CordaAvroDeserializer<Checkpoint>>()
    private val flowCheckpointFactory = mock<FlowCheckpointFactory>()
    private val config = mock<SmartConfig>()

    private val inputRecords = listOf(
        buildRecord("foo"),
        buildRecord("bar")
    )

    @BeforeEach
    fun setup() {
        val checkpoint = mock<Checkpoint>().apply {
            whenever(flowId).thenReturn("flowID")
        }
        whenever(avroDeserializer.deserialize(any())).thenReturn(checkpoint)
        whenever(flowCheckpointFactory.create(any(), any(), any())).thenReturn(mock())
        whenever(checkpointCleanupHandler.cleanupCheckpoint(any(), any(), any())).thenReturn(listOf(mock()))
    }

    @Test
    fun `when timeout processor receives events with states output records are generated`() {
        whenever(stateManager.get(any())).thenReturn(inputRecords.associate { it.key to buildState(it.key) })
        whenever(stateManager.delete(any())).thenReturn(mapOf())
        val processor = TimeoutEventCleanupProcessor(
            checkpointCleanupHandler,
            stateManager,
            avroDeserializer,
            flowCheckpointFactory,
            config
        )
        val output = processor.onNext(inputRecords)
        assertThat(output.size).isEqualTo(2)
    }

    @Test
    fun `when timeout processor fails to delete a state no records are output`() {
        whenever(stateManager.get(any())).thenReturn(inputRecords.associate { it.key to buildState(it.key) })
        whenever(stateManager.delete(any())).thenReturn(inputRecords.associate { it.key to buildState(it.key) })
        val processor = TimeoutEventCleanupProcessor(
            checkpointCleanupHandler,
            stateManager,
            avroDeserializer,
            flowCheckpointFactory,
            config
        )
        val output = processor.onNext(inputRecords)
        assertThat(output).isEmpty()
    }

    @Test
    fun `when state manager does not have states available no records are output`() {
        whenever(stateManager.get(any())).thenReturn(mapOf())
        whenever(stateManager.delete(any())).thenReturn(mapOf())
        val processor = TimeoutEventCleanupProcessor(
            checkpointCleanupHandler,
            stateManager,
            avroDeserializer,
            flowCheckpointFactory,
            config
        )
        val output = processor.onNext(inputRecords)
        assertThat(output).isEmpty()
    }

    @Test
    fun `when avro deserializer fails to deserialize no records are output`() {
        whenever(stateManager.get(any())).thenReturn(inputRecords.associate { it.key to buildState(it.key) })
        whenever(avroDeserializer.deserialize(any())).thenReturn(null)
        whenever(stateManager.delete(any())).thenReturn(mapOf())
        val processor = TimeoutEventCleanupProcessor(
            checkpointCleanupHandler,
            stateManager,
            avroDeserializer,
            flowCheckpointFactory,
            config
        )
        val output = processor.onNext(inputRecords)
        assertThat(output).isEmpty()
    }

    private fun buildRecord(key: String) : Record<String, FlowTimeout> {
        return Record("timeout", key, FlowTimeout(key, Instant.now()))
    }

    private fun buildState(key: String) : State {
        return State(key, byteArrayOf())
    }
}