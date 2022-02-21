package net.corda.flow.manager.impl.acceptance.dsl

import net.corda.flow.manager.fiber.FlowContinuation
import net.corda.flow.manager.fiber.FlowIORequest
import net.corda.flow.manager.impl.FlowEventContext
import net.corda.flow.manager.impl.runner.FlowRunner
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

class MockFlowRunner : FlowRunner {

    private var fibers = mutableMapOf<String, MockFlowFiber>()

    override fun runFlow(
        context: FlowEventContext<Any>,
        flowContinuation: FlowContinuation
    ): Future<FlowIORequest<*>> {
        val flowId = checkNotNull(context.checkpoint?.flowKey?.flowId) { "No flow id is set, context: $context" }
        val fiber = checkNotNull(fibers[flowId]) { "No flow with flow id: $flowId has been set up within the mocking framework" }
        return CompletableFuture.completedFuture(fiber.dequeueSuspension())
    }

    fun addFlowFiber(fiber: MockFlowFiber) {
        fibers[fiber.flowId] = fiber
    }
}