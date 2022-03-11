package net.corda.flow.fiber.factory

import co.paralleluniverse.fibers.FiberScheduler
import net.corda.data.flow.FlowKey
import net.corda.flow.fiber.FlowFiber
import net.corda.v5.application.flows.Flow

interface FlowFiberFactory {

    fun createFlowFiber(flowKey: FlowKey, logic: Flow<*>, scheduler: FiberScheduler) : FlowFiber<Any?>
}
