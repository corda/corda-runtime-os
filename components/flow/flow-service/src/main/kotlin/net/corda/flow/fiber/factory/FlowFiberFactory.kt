package net.corda.flow.fiber.factory

import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.fiber.FlowLogicAndArgs
import java.util.concurrent.Future

interface FlowFiberFactory {

    fun createFlowFiber(flowId: String, logic: FlowLogicAndArgs) : FlowFiber

    fun createAndResumeFlowFiber(
        flowFiberExecutionContext: FlowFiberExecutionContext,
        suspensionOutcome: FlowContinuation
    ): Future<FlowIORequest<*>>
}
