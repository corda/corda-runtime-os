package net.corda.flow.fiber

import co.paralleluniverse.concurrent.util.ScheduledSingleThreadExecutor
import co.paralleluniverse.fibers.FiberExecutorScheduler
import co.paralleluniverse.fibers.FiberScheduler
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.state.FlowStack
import net.corda.serialization.checkpoint.CheckpointSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlowFiberImplTest {

    val mockFlowStack = mock<FlowStack>()
    val mockCheckpointSerializer = mock<CheckpointSerializer>()
    val mockFlowSandboxGroupContext = mock<FlowSandboxGroupContext>()
    val mockFlowFiberExecutionContext = mock<FlowFiberExecutionContext>()
    
    val fiberScheduler: FiberScheduler = FiberExecutorScheduler(
        "Same thread scheduler",
        ScheduledSingleThreadExecutor()
    )

    @BeforeAll
    fun setup() {
        whenever(mockFlowSandboxGroupContext.checkpointSerializer).thenReturn(mockCheckpointSerializer)
        whenever(mockFlowFiberExecutionContext.sandboxGroupContext).thenReturn(mockFlowSandboxGroupContext)
        whenever(mockCheckpointSerializer.serialize<FlowFiberImpl>(any())).thenReturn("bytes".toByteArray())
        whenever(mockFlowFiberExecutionContext.flowStackService).thenReturn(mockFlowStack)

        expectItemInFlowStack()
    }

    @Test
    fun `check innocuous flow is handled safely`() {
        val mockFlowLogic = mock<FlowLogicAndArgs>()
        whenever(mockFlowLogic.invoke()).then {
            // do nothing, innocuous flow
            ""
        }

        val flowFiber = FlowFiberImpl(UUID.randomUUID(), mockFlowLogic, fiberScheduler)
        val outcome = flowFiber.runUntilNotSuspended(mockFlowFiberExecutionContext, fiberScheduler)

        assertThat(outcome).isInstanceOfAny(FlowIORequest.FlowFinished::class.java)
        verify(mockFlowLogic).invoke()
    }

    @Test
    fun `check throwing flow is handled safely`() {
        val mockFlowLogic = mock<FlowLogicAndArgs>()
        whenever(mockFlowLogic.invoke()).then {
            throw Throwable()
        }

        val flowFiber = FlowFiberImpl(UUID.randomUUID(), mockFlowLogic, fiberScheduler)
        flowFiber.setUncaughtExceptionHandler { _, _ ->
            forceCancelOngoingRunUntilWithTestFailure()
        }
        val outcome = flowFiber.runUntilNotSuspended(mockFlowFiberExecutionContext, fiberScheduler)

        assertThat(outcome).isInstanceOfAny(FlowIORequest.FlowFailed::class.java)
        verify(mockFlowLogic).invoke()
    }

    private fun forceCancelOngoingRunUntilWithTestFailure() {
        class TestFailedDueToForceCancel : FlowIORequest<Unit>

        val completableFuture = flowFuture as CompletableFuture<FlowIORequest<*>>
        completableFuture.complete(TestFailedDueToForceCancel())
    }

    private fun expectItemInFlowStack() {
        whenever(mockFlowStack.size).thenReturn(1)
        val mockStackItem = mock<FlowStackItem>()
        whenever(mockStackItem.sessionIds).thenReturn(listOf("stack item"))
        whenever(mockFlowStack.peek()).thenReturn(mockStackItem)
    }

    lateinit var flowFuture: Future<FlowIORequest<*>>

    fun FlowFiber.runUntilNotSuspended(
        flowFiberExecutionContext: FlowFiberExecutionContext,
        scheduler: FiberScheduler
    ): FlowIORequest<*> {
        flowFuture = this.startFlow(flowFiberExecutionContext)
        while (flowFuture.get() is FlowIORequest.FlowSuspended<*>) {
            // Sanity check that any time the FlowFiber was suspended, it was serialized
            verify(mockCheckpointSerializer).serialize(this)
            clearInvocations(mockCheckpointSerializer)
            flowFuture = this.resume(flowFiberExecutionContext, FlowContinuation.Run(), scheduler)
        }
        return flowFuture.get()
    }
}