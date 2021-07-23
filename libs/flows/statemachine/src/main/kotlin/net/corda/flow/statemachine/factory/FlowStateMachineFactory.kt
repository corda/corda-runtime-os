package net.corda.flow.statemachine.factory

import co.paralleluniverse.fibers.FiberScheduler
import net.corda.data.flow.FlowKey
import net.corda.flow.statemachine.FlowStateMachine
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.identity.Party

interface FlowStateMachineFactory {

    fun createStateMachine(
        clientId: String?,
        id: FlowKey,
        logic: Flow<*>,
        ourIdentity: Party,
        scheduler: FiberScheduler
    ) : FlowStateMachine<Any?>
}
