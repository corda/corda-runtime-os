package net.corda.flow.pipeline.converters

import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.pipeline.converters.impl.FlowEventContextConverterImpl
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.state.impl.CheckpointMetadataKeys.STATE_META_CHECKPOINT_TERMINATED_KEY
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.libs.statemanager.api.Metadata
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FlowEventContextConverterImplTest {

    @Test
    fun `convert context to state and event response`(){
        val avroCheckpoint = Checkpoint()
        val checkpoint = mock<FlowCheckpoint>()
        val records = listOf<Record<*, *>>()
        val context= buildFlowEventContext(
            checkpoint= checkpoint,
            inputEventPayload = Any(),
            outputRecords = records,
            sendToDlq = true)
        val converter = FlowEventContextConverterImpl()

        whenever(checkpoint.toAvro()).thenReturn(avroCheckpoint)

        val result = converter.convert(context)

        assertThat(result.markForDLQ).isTrue
        assertThat(result.updatedState?.value).isSameAs(avroCheckpoint)
        assertThat(result.responseEvents).isSameAs(records)
    }


    @Test
    fun `terminated checkpoint has additional termination meta`(){
        val avroCheckpoint = Checkpoint()
        val checkpoint = mock<FlowCheckpoint>()
        val records = listOf<Record<*, *>>()
        val context= buildFlowEventContext(
            checkpoint= checkpoint,
            inputEventPayload = Any(),
            outputRecords = records,
            sendToDlq = false)
        val converter = FlowEventContextConverterImpl()

        whenever(checkpoint.toAvro()).thenReturn(avroCheckpoint)
        whenever(checkpoint.isCompleted).thenReturn(true)

        val result = converter.convert(context)

        val expectedMeta = Metadata(mapOf(STATE_META_CHECKPOINT_TERMINATED_KEY to true))
        assertThat(result.markForDLQ).isFalse()
        assertThat(result.updatedState?.value).isSameAs(avroCheckpoint)
        assertThat(result.responseEvents).isSameAs(records)
        assertThat(result.updatedState?.metadata).isEqualTo(expectedMeta)
    }
}