package net.corda.flow.fiber.factory

import co.paralleluniverse.fibers.FiberScheduler
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.v5.application.flows.Flow

interface FlowFiberFactory {

    val currentScheduler: FiberScheduler

    fun createFlowFiber(flowId: String, logic: Flow<*>) : FlowFiber<Any?>

    fun createFlowFiber(flowFiberExecutionContext: FlowFiberExecutionContext) : FlowFiber<*>
}
