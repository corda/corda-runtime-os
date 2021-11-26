package net.corda.flow.statemachine.factory

import co.paralleluniverse.fibers.FiberScheduler
import net.corda.data.flow.FlowInfo
import net.corda.flow.statemachine.FlowStateMachine
import net.corda.v5.application.flows.Flow

interface FlowStateMachineFactory {

    @Suppress("LongParameterList")
    fun createStateMachine(
        clientId: String?,
        id: FlowInfo,
        logic: Flow<*>,
        flowName: String,
        scheduler: FiberScheduler
    ) : FlowStateMachine<Any?>
}
