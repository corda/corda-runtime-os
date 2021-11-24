package net.corda.dependency.injection

import net.corda.flow.statemachine.FlowFiber
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.serialization.SingletonSerializeAsToken

/**
 * The Flow dependency injector is responsible for injecting services into CordApp flows.
 */
interface FlowDependencyInjector {

    /**
     * Set any property on the flow marked with @[CordaInject] with an instance of the type specified.
     * @param flow The instance of a [Flow] to be setup
     * @param flowFiber The state machine for the flow.
     */
    fun injectServices(flow: Flow<*>, flowFiber: FlowFiber<*>)

    /**
     * @return A list of singletons registered with the injector.
     */
    fun getRegisteredAsTokenSingletons(): Set<SingletonSerializeAsToken>
}