package net.corda.flow.manager.impl

import net.corda.flow.manager.Checkpoint
import net.corda.v5.application.flows.StateMachineRunId
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.serialization.SerializedBytes

@CordaSerializable
class CheckpointImpl(
    val flowId: StateMachineRunId,
    val fiber: SerializedBytes<FlowStateMachineImpl<*>>?,
    val flowState: FlowState
) : Checkpoint {
    override fun toString(): String {
        return "CheckpointEvent[flowId=$flowId suspends=${flowState.suspendCount}] size=${fiber?.size}"
    }
}