package net.corda.flow.pipeline.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.StateMachineState
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowGlobalPostProcessor
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.pipeline.handlers.events.FlowEventHandler
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.flow.pipeline.runner.FlowRunner
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.util.concurrent.Future
import java.util.stream.Stream

class FlowEventPipelineImplTest {

    private val flowState = StateMachineState().apply {
        waitingFor = WaitingFor(Wakeup())
    }
    private val checkpoint = Checkpoint().apply {
        flowState = this@FlowEventPipelineImplTest.flowState
        fiber = ByteBuffer.wrap(byteArrayOf(0, 0, 0, 0))
    }

    private val inputContext = FlowEventContext<Any>(checkpoint, FlowEvent(), "Original", emptyList())
    private val outputContext = FlowEventContext<Any>(checkpoint, FlowEvent(), "Updated", emptyList())

    private val flowEventHandler = mock<FlowEventHandler<Any>>().apply {
        whenever(preProcess(inputContext)).thenReturn(outputContext)
    }

    private val flowWaitingForHandler = mock<FlowWaitingForHandler<Any>>().apply {
        whenever(runOrContinue(eq(inputContext), any())).thenReturn(FlowContinuation.Run(Unit))
    }

    private val flowRequestHandler = mock<FlowRequestHandler<FlowIORequest.ForceCheckpoint>>().apply {
        whenever(getUpdatedWaitingFor(inputContext, FlowIORequest.ForceCheckpoint)).thenReturn(WaitingFor(Wakeup()))
        whenever(postProcess(inputContext, FlowIORequest.ForceCheckpoint)).thenReturn(outputContext)
    }

    private val flowGlobalPostProcessor = mock<FlowGlobalPostProcessor>().apply {
        whenever(postProcess(inputContext)).thenReturn(outputContext)
    }

    private val runFlowCompletion = mock<Future<FlowIORequest<*>>>()

    private val flowRunner = mock<FlowRunner>().apply {
        whenever(runFlow(any(), any())).thenReturn(runFlowCompletion)
    }

    private val pipeline = FlowEventPipelineImpl(
        flowEventHandler,
        mapOf(Wakeup::class.java to flowWaitingForHandler),
        mapOf(FlowIORequest.ForceCheckpoint::class.java to flowRequestHandler),
        flowRunner,
        flowGlobalPostProcessor,
        inputContext
    )

    companion object {
        @JvmStatic
        fun runFlowContinuationConditions(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(FlowContinuation.Run()),
                Arguments.of(FlowContinuation.Error(Exception()))
            )
        }
    }

    @Test
    fun `eventPreProcessing calls the FlowEventHandler`() {
        assertEquals(outputContext, pipeline.eventPreProcessing().context)
        verify(flowEventHandler).preProcess(inputContext)
    }

    @ParameterizedTest(name = "runOrContinue runs a flow when {0} is returned by the FlowWaitingForHandler with suspend result")
    @MethodSource("runFlowContinuationConditions")
    fun `runOrContinue runs a flow with suspend result`(outcome: FlowContinuation) {
        val flowResult = FlowIORequest.SubFlowFinished(null)
        val expectedFiber = ByteBuffer.wrap(byteArrayOf(1))
        val suspendRequest = FlowIORequest.FlowSuspended(expectedFiber, flowResult)

        whenever(flowWaitingForHandler.runOrContinue(eq(inputContext), any())).thenReturn(outcome)
        whenever(runFlowCompletion.get()).thenReturn(suspendRequest)

        val state = pipeline.runOrContinue()

        verify(flowRunner).runFlow(pipeline.context, outcome)
        verify(flowWaitingForHandler).runOrContinue(inputContext, Wakeup())
        assertThat(state.context.checkpoint!!.fiber).isEqualByComparingTo(expectedFiber)
        assertThat(state.output).isSameAs(flowResult)
    }

    @ParameterizedTest(name = "runOrContinue runs a flow when {0} is returned by the FlowWaitingForHandler with flow completion result")
    @MethodSource("runFlowContinuationConditions")
    fun `runOrContinue runs a flow when with flow completion result`(outcome: FlowContinuation) {
        val flowResult = FlowIORequest.FlowFinished("")
        val expectedFiber = ByteBuffer.wrap(byteArrayOf())

        whenever(flowWaitingForHandler.runOrContinue(eq(inputContext), any())).thenReturn(outcome)
        whenever(runFlowCompletion.get()).thenReturn(flowResult)

        val state = pipeline.runOrContinue()

        verify(flowRunner).runFlow(pipeline.context, outcome)
        verify(flowWaitingForHandler).runOrContinue(inputContext, Wakeup())
        assertThat(state.context.checkpoint!!.fiber).isEqualByComparingTo(expectedFiber)
        assertThat(state.output).isSameAs(flowResult)
    }

    @Test
    fun `runOrContinue runs a flow when FlowContinuation#Error is returned by the FlowWaitingForHandler`() {
        whenever(flowWaitingForHandler.runOrContinue(eq(inputContext), any()))
            .thenReturn(FlowContinuation.Error(IllegalStateException("I'm broken")))
        whenever(runFlowCompletion.get()).thenReturn(FlowIORequest.FlowFinished(""))
        pipeline.runOrContinue()
        verify(flowRunner).runFlow(any(), any())
        verify(flowWaitingForHandler).runOrContinue(inputContext, Wakeup())
    }

    @Test
    fun `runOrContinue does not run a flow when FlowContinuation#Continue is returned by the FlowWaitingForHandler`() {
        whenever(flowWaitingForHandler.runOrContinue(eq(inputContext), any())).thenReturn(FlowContinuation.Continue)
        assertEquals(pipeline, pipeline.runOrContinue())
        verify(flowRunner, never()).runFlow(any(), any())
        verify(flowWaitingForHandler).runOrContinue(inputContext, Wakeup())
    }

    @Test
    fun `setCheckpointSuspendedOn sets the checkpoint's suspendedOn property when output is set`() {
        val pipeline = this.pipeline.copy(output = FlowIORequest.ForceCheckpoint)
        val output = inputContext.copy(Checkpoint().apply {
            fiber = ByteBuffer.wrap(byteArrayOf(0, 0, 0, 0))
            flowState = StateMachineState().apply {
                suspendedOn = FlowIORequest.ForceCheckpoint::class.qualifiedName
                waitingFor = WaitingFor(Wakeup())
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
    fun `globalPostProcessing calls the FlowGlobalPostProcessor when output is set`() {
        val pipeline = this.pipeline.copy(output = FlowIORequest.ForceCheckpoint)
        assertEquals(outputContext, pipeline.globalPostProcessing().context)
        verify(flowGlobalPostProcessor).postProcess(inputContext)
    }

    @Test
    fun `globalPostProcessing calls the FlowGlobalPostProcessor when output is not set`() {
        val pipeline = this.pipeline.copy(output = null)
        assertEquals(outputContext, pipeline.globalPostProcessing().context)
        verify(flowGlobalPostProcessor).postProcess(inputContext)
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
        assertEquals(
            StateAndEventProcessor.Response(pipeline.context.checkpoint, records),
            pipeline.toStateAndEventResponse()
        )
    }

    @Test
    fun `toStateAndEventResponse returns no events if the context contained no output records`() {
        val pipeline = this.pipeline.copy(
            context = inputContext.copy(
                outputRecords = emptyList()
            )
        )
        assertEquals(
            StateAndEventProcessor.Response(pipeline.context.checkpoint, emptyList()),
            pipeline.toStateAndEventResponse()
        )
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