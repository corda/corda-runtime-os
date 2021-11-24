package net.corda.dependency.injection

import net.corda.flow.statemachine.FlowFiber
import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.injection.CordaInject

/**
 *  Implementations of InjectableFactory are used to by the flow dependency injection infrastructure to create instances
 *  of types that can be injected into a flow using the [CordaInject] annotation.
 *
 *  It is expected that each injectable service will provide a proxy implementation of <T> and an implementation
 *  of this factory to create it. The proxy will be used to control how the internal service is called in the context
 *  of a sandbox ([SandboxGroup]) and flow ([FlowFiber]).
 */
interface InjectableFactory<T> {

    /**
     * @return the target type is the interface type injected into a flow using the [CordaInject] annotation.
     */
    val target: Class<T>

    /**
     * Creates the instance of a proxy service of <T>.
     *
     * @param flowFiber used to determine the flow context.
     * @param sandboxGroup used to determine the sandbox context.
     *
     * @return an instance of the type injected into the flow using the [CordaInject] annotation.
     */
    fun create(flowFiber: FlowFiber<*>, sandboxGroup: SandboxGroup) : T
}