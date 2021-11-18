package net.corda.flow.statemachine.factory

import co.paralleluniverse.fibers.FiberScheduler
import net.corda.data.flow.FlowKey
import net.corda.flow.statemachine.FlowStateMachine
import net.corda.v5.application.flows.Flow

interface FlowStateMachineFactory {

    @Suppress("LongParameterList")
    fun createStateMachine(
        clientId: String?,
        id: FlowKey,
        logic: Flow<*>,
        cpiId: String,
        flowName: String,
        scheduler: FiberScheduler
    ) : FlowStateMachine<Any?>
}
