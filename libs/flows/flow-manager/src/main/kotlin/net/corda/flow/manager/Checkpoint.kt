package net.corda.flow.manager


import net.corda.v5.application.flows.StateMachineRunId
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.serialization.SerializedBytes

@CordaSerializable
class Checkpoint(
    val flowId: StateMachineRunId,
    val fiber: SerializedBytes<FlowStateMachine<*>>?,
    val flowState: FlowState
) {
    override fun toString(): String {
        return "CheckpointEvent[flowId=$flowId suspends=${flowState.suspendCount}] size=${fiber?.size}"
    }
}