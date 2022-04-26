package net.corda.flow.acceptance

import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.acceptance.dsl.MockFlowFiber
import net.corda.flow.acceptance.dsl.MockFlowRunner
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant

class MockFlowRunnerTest {

    private val runner = MockFlowRunner()
    private val flowId = "id1"
    private val fiber = MockFlowFiber(flowId)
    private val emptyBytes = ByteBuffer.wrap(byteArrayOf(0))
    private val holdingIdentity = BOB_X500_HOLDING_IDENTITY
    private val startContext = FlowStartContext.newBuilder()
        .setStatusKey(FlowKey("request id", holdingIdentity))
        .setInitiatorType(FlowInitiatorType.RPC)
        .setRequestId("request id")
        .setIdentity(holdingIdentity)
        .setCpiId("cpi id")
        .setInitiatedBy(holdingIdentity)
        .setFlowClassName("flow class name")
        .setCreatedTimestamp(Instant.MIN)
        .build()

    private val startRPCFlowPayload = StartFlow.newBuilder()
        .setStartContext(startContext)
        .setFlowStartArgs(" { \"json\": \"args\" }")
        .build()

    private val checkpoint = mock<FlowCheckpoint>()

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        whenever(checkpoint.flowId).thenReturn(flowId)
        whenever(checkpoint.flowStartContext).thenReturn(startContext)
    }

    @Test
    fun `runFlow finds a fiber and returns a completed future containing the fibers specified suspension`() {
        fiber.queueSuspension(FlowIORequest.ForceCheckpoint)
        runner.addFlowFiber(fiber)
        val future = runner.runFlow(
            context = buildFlowEventContext(checkpoint, inputEventPayload = startRPCFlowPayload),
            flowContinuation = FlowContinuation.Run(Unit)
        )
        assertTrue(future.isDone)
        assertEquals(future.get(), FlowIORequest.FlowSuspended(emptyBytes, FlowIORequest.ForceCheckpoint))
    }

    @Test
    fun `runFlow throws if the input context does not have a flow id`() {
        runner.addFlowFiber(fiber)
        whenever(checkpoint.flowKey).thenReturn(null)

        assertThrows<IllegalStateException>{
            runner.runFlow(
                context = buildFlowEventContext(checkpoint, startRPCFlowPayload),
                flowContinuation = FlowContinuation.Run(Unit)
            )
        }
    }

    @Test
    fun `runFlow throws if there are no registered fibers`() {
        assertThatThrownBy {
            runner.runFlow(
                context = buildFlowEventContext(checkpoint, startRPCFlowPayload),
                flowContinuation = FlowContinuation.Run(Unit)
            )
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("No flow with flow id")
    }

    @Test
    fun `runFlow throws if there no fiber matches the input context's flow id`() {
        runner.addFlowFiber(MockFlowFiber("another id").apply { queueSuspension(FlowIORequest.ForceCheckpoint) })
        assertThatThrownBy {
            runner.runFlow(
                context = buildFlowEventContext(checkpoint, startRPCFlowPayload),
                flowContinuation = FlowContinuation.Run(Unit)
            )
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("No flow with flow id")
    }

    @Test
    fun `runFlow can handle multiple fibers`() {
        val fiber2 = MockFlowFiber("another id")
        fiber.queueSuspension(FlowIORequest.ForceCheckpoint)
        fiber2.queueSuspension(FlowIORequest.WaitForSessionConfirmations)
        runner.addFlowFiber(fiber)
        runner.addFlowFiber(fiber2)

        val future1 = runner.runFlow(
            context = buildFlowEventContext(checkpoint, startRPCFlowPayload),
            flowContinuation = FlowContinuation.Run(Unit)
        )
        assertEquals(future1.get(), FlowIORequest.FlowSuspended(emptyBytes, FlowIORequest.ForceCheckpoint))

        whenever(checkpoint.flowId).thenReturn("another id")

        val future2 = runner.runFlow(
            context = buildFlowEventContext(checkpoint,startRPCFlowPayload),
            flowContinuation = FlowContinuation.Run(Unit)
        )
        assertEquals(future2.get(), FlowIORequest.FlowSuspended(emptyBytes, FlowIORequest.WaitForSessionConfirmations))
    }
}