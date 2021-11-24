package net.corda.flow.statemachine.requests

import net.corda.flow.statemachine.FlowStateMachine

interface InjectableFactory<T> {

    val target: Class<T>

    fun create(fiber: FlowStateMachine<*>, sandboxGroup: SandboxGroup) : T
}

interface SandboxGroup