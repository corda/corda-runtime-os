package net.corda.flow.manager.impl.acceptance

import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.FlowStatusKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.StateMachineState
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.manager.fiber.FlowContinuation
import net.corda.flow.manager.fiber.FlowIORequest
import net.corda.flow.manager.impl.FlowEventContext
import net.corda.flow.manager.impl.acceptance.dsl.MockFlowFiber
import net.corda.flow.manager.impl.acceptance.dsl.MockFlowRunner
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.time.Instant

class MockFlowRunnerTest {

    private companion object {
        const val FLOW_ID = "flow id"
        val emptyBytes: ByteBuffer = ByteBuffer.wrap(byteArrayOf(0))
    }

    private val runner = MockFlowRunner()
    private val fiber = MockFlowFiber(FLOW_ID)

    private val flowKey = FlowKey(FLOW_ID, HoldingIdentity("x500 name", "group id"))

    private val holdingIdentity = HoldingIdentity("x500 name","group id")
    private val startContext = FlowStartContext.newBuilder()
        .setStatusKey(FlowStatusKey("request id",holdingIdentity))
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

    private val startFlowEvent = FlowEvent(flowKey, startRPCFlowPayload)

    private val checkpoint = Checkpoint.newBuilder()
        .setFlowKey(flowKey)
        .setFiber(ByteBuffer.wrap(byteArrayOf()))
        .setFlowStartContext(startContext)
        .setFlowState(StateMachineState())
        .setSessions(mutableListOf())
        .setFlowStackItems(mutableListOf())
        .build()

    @Test
    fun `runFlow finds a fiber and returns a completed future containing the fibers specified suspension`() {
        fiber.queueSuspension(FlowIORequest.ForceCheckpoint)
        runner.addFlowFiber(fiber)
        val future = runner.runFlow(
            context = FlowEventContext(
                checkpoint,
                inputEvent = startFlowEvent,
                inputEventPayload = startRPCFlowPayload,
                outputRecords = emptyList()
            ),
            flowContinuation = FlowContinuation.Run(Unit)
        )
        assertTrue(future.isDone)
        assertEquals(future.get(), FlowIORequest.FlowSuspended(emptyBytes, FlowIORequest.ForceCheckpoint))
    }

    @Test
    fun `runFlow throws if the input context does not have a flow key`() {
        runner.addFlowFiber(fiber)
        assertThatThrownBy {
            runner.runFlow(
                context = FlowEventContext(
                    checkpoint.apply { flowKey = null },
                    inputEvent = startFlowEvent,
                    inputEventPayload = startRPCFlowPayload,
                    outputRecords = emptyList()
                ),
                flowContinuation = FlowContinuation.Run(Unit)
            )
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("No flow id is set")
    }

    @Test
    fun `runFlow throws if the input context does not have a flow id`() {
        runner.addFlowFiber(fiber)
        assertThatThrownBy {
            runner.runFlow(
                context = FlowEventContext(
                    checkpoint.also { flowKey.flowId = null },
                    inputEvent = startFlowEvent,
                    inputEventPayload = startRPCFlowPayload,
                    outputRecords = emptyList()
                ),
                flowContinuation = FlowContinuation.Run(Unit)
            )
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("No flow id is set")
    }

    @Test
    fun `runFlow throws if there are no registered fibers`() {
        assertThatThrownBy {
            runner.runFlow(
                context = FlowEventContext(
                    checkpoint,
                    inputEvent = startFlowEvent,
                    inputEventPayload = startRPCFlowPayload,
                    outputRecords = emptyList()
                ),
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
                context = FlowEventContext(
                    checkpoint,
                    inputEvent = startFlowEvent,
                    inputEventPayload = startRPCFlowPayload,
                    outputRecords = emptyList()
                ),
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
            context = FlowEventContext(
                checkpoint,
                inputEvent = startFlowEvent,
                inputEventPayload = startRPCFlowPayload,
                outputRecords = emptyList()
            ),
            flowContinuation = FlowContinuation.Run(Unit)
        )
        assertEquals(future1.get(), FlowIORequest.FlowSuspended(emptyBytes, FlowIORequest.ForceCheckpoint))

        val future2 = runner.runFlow(
            context = FlowEventContext(
                checkpoint.also { flowKey.flowId = "another id" },
                inputEvent = startFlowEvent,
                inputEventPayload = startRPCFlowPayload,
                outputRecords = emptyList()
            ),
            flowContinuation = FlowContinuation.Run(Unit)
        )
        assertEquals(future2.get(), FlowIORequest.FlowSuspended(emptyBytes, FlowIORequest.WaitForSessionConfirmations))
    }
}