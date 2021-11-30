package net.corda.dependency.injection

import net.corda.flow.statemachine.FlowStateMachine
import net.corda.sandbox.SandboxGroup
import net.corda.v5.serialization.SingletonSerializeAsToken

/**
 *  Implementations of InjectableFactory are used to by the flow dependency injection infrastructure to create instances
 *  of types that can be injected into a flow using the [CordaInject] annotation.
 *
 *  It is expected that each injectable service will provide a proxy implementation of <T> and an implementaion
 *  of this factory to create it. The proxy will be used to control how the internal service is called in the context
 *  of a sandbox ([SandboxGroup]) and flow ([FlowStateMachine]).
 *
 *  These proxy should be expected to be serialised by the checkpoint serialization process and therefore they are
 *  required to expose any references they hold to [SingletonSerializeAsToken] via the getSingletons() method.
 */
interface InjectableFactory<T> {

    /**
     * @return the target type is the interface type injected into a flow using the [CordaInject] annotation.
     */
    val target: Class<T>

    /**
     * Used to access a set of the singletons referenced by the implementaion of <T>
     *
     * @return a set of instances that implement the [SingletonSerializeAsToken]
     */
    fun getSingletons(): Set<SingletonSerializeAsToken>

    /**
     * Creates the instance of a proxy service of <T>.
     *
     * @param flowStateMachine used to determine the flow context.
     * @param SandboxGroup used to determine the sandbox context.
     *
     * @return an instance of the type injected into the flow using the [CordaInject] annotation.
     */
    fun create(flowStateMachine: FlowStateMachine<*>, sandboxGroup: SandboxGroup) : T
}