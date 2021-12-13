package net.corda.flow.service

import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.flow.manager.FlowEventExecutor
import net.corda.flow.manager.FlowEventExecutorFactory
import net.corda.flow.manager.FlowMetaData
import net.corda.flow.manager.FlowMetaDataFactory
import net.corda.flow.manager.FlowResult
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowMessageProcessorTest {
    private val flowMetaDataFactory: FlowMetaDataFactory = mock()
    private val flowEventExecutorFactory: FlowEventExecutorFactory = mock()


    @Test
    fun `Event message is converted and dispatched`() {
        val state  = Checkpoint()
        val event = Record("t1", FlowKey(), FlowEvent() )
        val metaData: FlowMetaData = mock()
        val executor: FlowEventExecutor = mock()
        val executorResult = FlowResult(state,listOf())

        doReturn(metaData).whenever(flowMetaDataFactory).createFromEvent(any(), any())
        doReturn(executor).whenever(flowEventExecutorFactory).create(any())
        doReturn(executorResult).whenever(executor).execute()

        val flowMessageProcessor = FlowMessageProcessor(flowMetaDataFactory, flowEventExecutorFactory)
        val result = flowMessageProcessor.onNext(state, event)

        assertThat(result.updatedState).isSameAs(executorResult.checkpoint)
        assertThat(result.responseEvents).isSameAs(executorResult.events)

        verify(flowMetaDataFactory, times(1)).createFromEvent(state,event)
        verify(flowEventExecutorFactory, times(1)).create(metaData)
        verify(executor, times(1)).execute()
    }
}
