package net.corda.dependency.injection

import net.corda.flow.statemachine.FlowStateMachine
import net.corda.virtual.node.sandboxgroup.SandboxGroupContext

interface InjectableFactory<T> {

    val target: Class<T>

    fun create(flowStateMachine: FlowStateMachine<*>, sandboxGroupContext: SandboxGroupContext) : T
}