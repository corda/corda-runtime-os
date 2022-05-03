package net.corda.flow.pipeline.converters

import net.corda.data.flow.state.Checkpoint
import net.corda.flow.pipeline.converters.impl.FlowEventContextConverterImpl
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
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
        assertThat(result.updatedState).isSameAs(avroCheckpoint)
        assertThat(result.responseEvents).isSameAs(records)
    }
}