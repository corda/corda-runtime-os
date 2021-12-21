package net.corda.flow.manager.impl.pipeline

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.StateMachineState
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.requests.FlowIORequest
import net.corda.flow.manager.impl.FlowEventContext
import net.corda.flow.manager.impl.handlers.FlowProcessingException
import net.corda.flow.manager.impl.handlers.events.FlowEventHandler
import net.corda.flow.manager.impl.handlers.requests.FlowRequestHandler
import net.corda.flow.manager.impl.runner.FlowRunner
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class FlowEventPipelineImplTest {

    private val flowState = StateMachineState()
    private val checkpoint = Checkpoint().apply {
        flowState = this@FlowEventPipelineImplTest.flowState
        fiber = ByteBuffer.wrap(byteArrayOf(0, 0, 0, 0))
    }

    private val inputContext = FlowEventContext<Any>(checkpoint, FlowEvent(), "Original", emptyList())
    private val outputContext = FlowEventContext<Any>(checkpoint, FlowEvent(), "Updated", emptyList())

    private val flowEventHandler = mock<FlowEventHandler<Any>>().apply {
        whenever(preProcess(inputContext)).thenReturn(outputContext)
        whenever(postProcess(inputContext)).thenReturn(outputContext)
    }

    private val flowRequestHandler = mock<FlowRequestHandler<FlowIORequest.ForceCheckpoint>>().apply {
        whenever(postProcess(inputContext, FlowIORequest.ForceCheckpoint)).thenReturn(outputContext)
    }

    private val flowFiber = mock<FlowFiber<*>>().apply {
        whenever(waitForCheckpoint()).thenReturn(
            Pair(
                Checkpoint().apply {
                    flowState = StateMachineState()
                    fiber = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4))
                },
                FlowIORequest.ForceCheckpoint
            )
        )
    }

    private val flowRunner = mock<FlowRunner>().apply {
        whenever(runFlow(any(), any(), any())).thenReturn(flowFiber)
    }

    private val pipeline = FlowEventPipelineImpl(
        flowEventHandler,
        mapOf(FlowIORequest.ForceCheckpoint::class.java to flowRequestHandler),
        flowRunner,
        inputContext
    )

    @Test
    fun `eventPreProcessing calls the FlowEventHandler`() {
        assertEquals(outputContext, pipeline.eventPreProcessing().context)
        verify(flowEventHandler).preProcess(inputContext)
    }

    @Test
    fun `runOrContinue runs a flow when FlowContinuation#Run is returned by the FlowEventHandler`() {
        whenever(flowEventHandler.runOrContinue(inputContext)).thenReturn(FlowContinuation.Run(Unit))
        pipeline.runOrContinue()
        verify(flowRunner).runFlow(any(), any(), any())
        verify(flowEventHandler).runOrContinue(inputContext)
    }

    @Test
    fun `runOrContinue sets the output of the flow when FlowContinuation#Run is returned by the FlowEventHandler`() {
        val output = pipeline.copy(
            context = pipeline.context.copy(checkpoint = checkpoint.apply {
                fiber = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4))
            }),
            output = FlowIORequest.ForceCheckpoint
        )
        whenever(flowEventHandler.runOrContinue(inputContext)).thenReturn(FlowContinuation.Run(Unit))
        assertEquals(output, pipeline.runOrContinue())
        verify(flowRunner).runFlow(any(), any(), any())
        verify(flowEventHandler).runOrContinue(inputContext)
    }

    @Test
    fun `runOrContinue throws when there is no checkpoint and FlowContinuation#Run is returned by the FlowEventHandler`() {
        val context = inputContext.copy(checkpoint = null)
        val pipeline = this.pipeline.copy(context = context)
        whenever(flowEventHandler.runOrContinue(context)).thenReturn(FlowContinuation.Run(Unit))
        assertThrows(FlowProcessingException::class.java) {
            pipeline.runOrContinue()
        }
    }

    @Test
    fun `runOrContinue runs a flow when FlowContinuation#Error is returned by the FlowEventHandler`() {
        whenever(flowEventHandler.runOrContinue(inputContext)).thenReturn(FlowContinuation.Error(IllegalStateException("I'm broken")))
        pipeline.runOrContinue()
        verify(flowRunner).runFlow(any(), any(), any())
        verify(flowEventHandler).runOrContinue(inputContext)
    }

    @Test
    fun `runOrContinue sets the output of the flow when FlowContinuation#Error is returned by the FlowEventHandler`() {
        val output = pipeline.copy(
            context = pipeline.context.copy(checkpoint = checkpoint.apply {
                fiber = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4))
            }),
            output = FlowIORequest.ForceCheckpoint
        )
        whenever(flowEventHandler.runOrContinue(inputContext)).thenReturn(FlowContinuation.Error(IllegalStateException("I'm broken")))
        assertEquals(output, pipeline.runOrContinue())
        verify(flowRunner).runFlow(any(), any(), any())
        verify(flowEventHandler).runOrContinue(inputContext)
    }

    @Test
    fun `runOrContinue does not run a flow when FlowContinuation#Continue is returned by the FlowEventHandler`() {
        whenever(flowEventHandler.runOrContinue(inputContext)).thenReturn(FlowContinuation.Continue)
        assertEquals(pipeline, pipeline.runOrContinue())
        verify(flowRunner, never()).runFlow(any(), any(), any())
        verify(flowEventHandler).runOrContinue(inputContext)
    }

    @Test
    fun `runOrContinue throws when there is no checkpoint and FlowContinuation#Error is returned by the FlowEventHandler`() {
        val context = inputContext.copy(checkpoint = null)
        val pipeline = this.pipeline.copy(context = context)
        whenever(flowEventHandler.runOrContinue(context)).thenReturn(FlowContinuation.Error(IllegalStateException("I'm broken")))
        assertThrows(FlowProcessingException::class.java) {
            pipeline.runOrContinue()
        }
    }

    @Test
    fun `setCheckpointSuspendedOn sets the checkpoint's suspendedOn property when output is set`() {
        val pipeline = this.pipeline.copy(output = FlowIORequest.ForceCheckpoint)
        val output = inputContext.copy(Checkpoint().apply {
            fiber = ByteBuffer.wrap(byteArrayOf(0, 0, 0, 0))
            flowState = StateMachineState().apply {
                suspendedOn = FlowIORequest.ForceCheckpoint::class.qualifiedName
            }
        })
        assertEquals(output, pipeline.setCheckpointSuspendedOn().context)
    }

    @Test
    fun `setCheckpointSuspendedOn does not set the checkpoint's suspendedOn property when output is not set`() {
        val pipeline = this.pipeline.copy(
            context = inputContext.copy(
                checkpoint = Checkpoint().apply {
                    flowState = StateMachineState().apply {
                        suspendedOn = FlowIORequest.ForceCheckpoint::class.qualifiedName
                    }
                }),
            output = null
        )
        assertEquals(pipeline, pipeline.setCheckpointSuspendedOn())
    }

    @Test
    fun `requestPostProcessing calls the appropriate request handler when output is set`() {
        val pipeline = this.pipeline.copy(output = FlowIORequest.ForceCheckpoint)
        assertEquals(outputContext, pipeline.requestPostProcessing().context)
        verify(flowRequestHandler).postProcess(inputContext, FlowIORequest.ForceCheckpoint)
    }

    @Test
    fun `requestPostProcessing does not call a request handler when output is not set`() {
        val pipeline = this.pipeline.copy(output = null)
        assertEquals(pipeline, pipeline.requestPostProcessing())
        verify(flowRequestHandler, never()).postProcess(inputContext, FlowIORequest.ForceCheckpoint)
    }

    @Test
    fun `requestPostProcessing throws an exception if the appropriate request handler cannot be found`() {
        val pipeline = this.pipeline.copy(output = FlowIORequest.WaitForSessionConfirmations)
        assertThrows(FlowProcessingException::class.java) { pipeline.requestPostProcessing() }
    }

    @Test
    fun `eventPostProcessing calls the FlowEventHandler when output is set`() {
        val pipeline = this.pipeline.copy(output = FlowIORequest.ForceCheckpoint)
        assertEquals(outputContext, pipeline.eventPostProcessing().context)
        verify(flowEventHandler).postProcess(inputContext)
    }

    @Test
    fun `eventPostProcessing calls the FlowEventHandler when output is not set`() {
        val pipeline = this.pipeline.copy(output = null)
        assertEquals(outputContext, pipeline.eventPostProcessing().context)
        verify(flowEventHandler).postProcess(inputContext)
    }

    @Test
    fun `toStateAndEventResponse converts the context values into a response`() {
        val records = listOf(
            Record("topic", "key 1", "value 1"),
            Record("topic", "key 2", "value 2")
        )
        val pipeline = this.pipeline.copy(
            context = inputContext.copy(
                outputRecords = records
            )
        )
        assertEquals(StateAndEventProcessor.Response(pipeline.context.checkpoint, records), pipeline.toStateAndEventResponse())
    }

    @Test
    fun `toStateAndEventResponse returns no events if the context contained no output records`() {
        val pipeline = this.pipeline.copy(
            context = inputContext.copy(
                outputRecords = emptyList()
            )
        )
        assertEquals(StateAndEventProcessor.Response(pipeline.context.checkpoint, emptyList()), pipeline.toStateAndEventResponse())
    }

    @Test
    fun `toStateAndEventResponse returns a null checkpoint if the context's checkpoint was null`() {
        val pipeline = this.pipeline.copy(
            context = inputContext.copy(
                checkpoint = null
            )
        )
        assertEquals(StateAndEventProcessor.Response(null, emptyList()), pipeline.toStateAndEventResponse())
    }
}