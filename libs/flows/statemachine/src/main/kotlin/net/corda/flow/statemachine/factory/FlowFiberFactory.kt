package net.corda.flow.statemachine.factory

import co.paralleluniverse.fibers.FiberScheduler
import net.corda.data.flow.FlowKey
import net.corda.flow.statemachine.FlowFiber
import net.corda.v5.application.flows.Flow

interface FlowFiberFactory {

    fun createFlowFiber(id: FlowKey, logic: Flow<*>, scheduler: FiberScheduler) : FlowFiber<Any?>
}
