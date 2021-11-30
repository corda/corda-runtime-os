package net.corda.dependency.injection

import net.corda.flow.statemachine.FlowStateMachine
import net.corda.sandbox.SandboxGroup
import net.corda.v5.serialization.SingletonSerializeAsToken

/**
 *  add detailed comments for implementors
 */
interface InjectableFactory<T> {

    val target: Class<T>

    fun getSingletons(): Set<SingletonSerializeAsToken>

    fun create(flowStateMachine: FlowStateMachine<*>, sandboxGroup: SandboxGroup) : T
}