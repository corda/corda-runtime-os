package net.corda.flow.fiber.factory

import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowIORequest
import net.corda.v5.application.flows.Flow
import java.util.concurrent.Future

interface FlowFiberFactory {

    fun createFlowFiber(flowId: String, logic: Flow<*>, args: Any? = null) : FlowFiber<Any?>

    fun createAndResumeFlowFiber(
        flowFiberExecutionContext: FlowFiberExecutionContext,
        suspensionOutcome: FlowContinuation
    ): Future<FlowIORequest<*>>
}
