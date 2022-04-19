package net.corda.flow.acceptance.dsl

import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.runner.FlowRunner
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

class MockFlowRunner : FlowRunner {

    private var fibers = mutableMapOf<String, MockFlowFiber>()

    override fun runFlow(
        context: FlowEventContext<Any>,
        flowContinuation: FlowContinuation
    ): Future<FlowIORequest<*>> {
        val flowId = checkNotNull(context.checkpoint.flowId) { "No flow id is set, context: $context" }
        val fiber = checkNotNull(fibers[flowId]) { "No flow with flow id: $flowId has been set up within the mocking framework" }
        return CompletableFuture.completedFuture(fiber.dequeueSuspension())
    }

    fun addFlowFiber(fiber: MockFlowFiber) {
        fibers[fiber.flowId] = fiber
    }
}