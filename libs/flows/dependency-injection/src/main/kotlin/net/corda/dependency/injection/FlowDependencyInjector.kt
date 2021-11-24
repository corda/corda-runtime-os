package net.corda.dependency.injection

import net.corda.flow.statemachine.FlowStateMachine
import net.corda.v5.application.flows.Flow
import net.corda.v5.serialization.SingletonSerializeAsToken

interface FlowDependencyInjector {
    fun injectServices(flow: Flow<*>, flowStateMachine: FlowStateMachine<*>)

    fun getRegisteredAsTokenSingletons(): Set<SingletonSerializeAsToken>
}