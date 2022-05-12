package net.corda.flow.pipeline.impl

import net.corda.data.flow.FlowStackItem
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.FLOW_ID_1
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowGlobalPostProcessor
import net.corda.flow.pipeline.exceptions.FlowProcessingException
import net.corda.flow.pipeline.handlers.events.FlowEventHandler
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.flow.pipeline.runner.FlowRunner
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.util.concurrent.Future
import java.util.stream.Stream
import net.corda.data.flow.state.waiting.Wakeup as WakeUpWaitingFor

class FlowEventPipelineImplTest {

    private val wakeUpEvent = Wakeup()
    private val waitingForWakeup = WaitingFor(WakeUpWaitingFor())
    private val retryStartFlow = StartFlow()

    private val retryEvent = FlowEvent().apply {
        flowId = FLOW_ID_1
        payload = retryStartFlow
    }

    private val checkpoint = mock<FlowCheckpoint>().apply {
        whenever(waitingFor).thenReturn(waitingForWakeup)
        whenever(inRetryState).thenReturn(false)
    }

    private val inputContext = buildFlowEventContext<Any>(checkpoint, wakeUpEvent)
    private val outputContext = buildFlowEventContext<Any>(checkpoint, wakeUpEvent)

    private val wakeUpFlowEventHandler = mock<FlowEventHandler<Any>>().apply {
        whenever(preProcess(inputContext)).thenReturn(outputContext)
    }

    private val startFlowEventHandler = mock<FlowEventHandler<Any>>().apply {
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
        mapOf(Wakeup::class.java to wakeUpFlowEventHandler, StartFlow::class.java to startFlowEventHandler),
        mapOf(WakeUpWaitingFor()::class.java to flowWaitingForHandler),
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
    fun `eventPreProcessing with no retry calls the event handler`() {
        assertEquals(outputContext, pipeline.eventPreProcessing().context)
        verify(wakeUpFlowEventHandler).preProcess(inputContext)
    }

    @Test
    fun `eventPreProcessing wakeup with retry uses retry event handler`() {
        val retryHandlerOutputContext = buildFlowEventContext<Any>(checkpoint, retryEvent)
        whenever(checkpoint.inRetryState).thenReturn(true)
        whenever(checkpoint.retryEvent).thenReturn(retryEvent)
        whenever(startFlowEventHandler.preProcess(any())).thenReturn(retryHandlerOutputContext)

        assertEquals(retryHandlerOutputContext, pipeline.eventPreProcessing().context)
        verify(startFlowEventHandler).preProcess(argThat { this.inputEvent == retryEvent && this.inputEventPayload == retryEvent.payload })
    }

    @ParameterizedTest(name = "runOrContinue runs a flow when {0} is returned by the FlowWaitingForHandler with suspend result")
    @MethodSource("runFlowContinuationConditions")
    fun `runOrContinue runs a flow with suspend result`(outcome: FlowContinuation) {
        val flowResult = FlowIORequest.SubFlowFinished(FlowStackItem())
        val expectedFiber = ByteBuffer.wrap(byteArrayOf(1))
        val suspendRequest = FlowIORequest.FlowSuspended(expectedFiber, flowResult)

        whenever(flowWaitingForHandler.runOrContinue(eq(inputContext), any())).thenReturn(outcome)
        whenever(runFlowCompletion.get()).thenReturn(suspendRequest)

        val state = pipeline.runOrContinue()

        verify(flowRunner).runFlow(pipeline.context, outcome)
        verify(flowWaitingForHandler).runOrContinue(inputContext, WakeUpWaitingFor())
        verify(checkpoint).serializedFiber = expectedFiber
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
        verify(flowWaitingForHandler).runOrContinue(inputContext, waitingForWakeup.value)
        verify(checkpoint).serializedFiber = expectedFiber
        assertThat(state.output).isSameAs(flowResult)
    }

    @Test
    fun `runOrContinue runs a flow when FlowContinuation#Error is returned by the FlowWaitingForHandler`() {
        whenever(flowWaitingForHandler.runOrContinue(eq(inputContext), any()))
            .thenReturn(FlowContinuation.Error(IllegalStateException("I'm broken")))
        whenever(runFlowCompletion.get()).thenReturn(FlowIORequest.FlowFinished(""))
        pipeline.runOrContinue()
        verify(flowRunner).runFlow(any(), any())
        verify(flowWaitingForHandler).runOrContinue(inputContext, WakeUpWaitingFor())
    }

    @Test
    fun `runOrContinue does not run a flow when FlowContinuation#Continue is returned by the FlowWaitingForHandler`() {
        whenever(flowWaitingForHandler.runOrContinue(eq(inputContext), any())).thenReturn(FlowContinuation.Continue)
        assertEquals(pipeline, pipeline.runOrContinue())
        verify(flowRunner, never()).runFlow(any(), any())
        verify(flowWaitingForHandler).runOrContinue(inputContext, WakeUpWaitingFor())
    }

    @Test
    fun `setCheckpointSuspendedOn sets the checkpoint's suspendedOn property when output is set`() {
        val pipeline = this.pipeline.copy(output = FlowIORequest.ForceCheckpoint)
        pipeline.setCheckpointSuspendedOn()
        verify(checkpoint).suspendedOn = FlowIORequest.ForceCheckpoint::class.qualifiedName
    }

    @Test
    fun `setCheckpointSuspendedOn does not set the checkpoint's suspendedOn property when output is not set`() {
        val pipeline = this.pipeline.copy(output = null)
        pipeline.setCheckpointSuspendedOn()
        verify(checkpoint, never()).suspendedOn
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
}