package net.corda.flow.pipeline

import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.state.FlowCheckpoint
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity

/**
 * Initializes the Checkpoint
 *
 * This interface initializes the Checkpoint.
 *
 * @param checkpoint a FlowCheckpoint.
 * @param waitingFor a WaitingFor.
 * @param holdingIdentity a HoldingIdentity.
 * @param contextBuilder a lambda which takes a Set<SecureHash> and returns a FlowStartContext.
 */

interface CheckpointInitializer {
    fun initialize(
        checkpoint: FlowCheckpoint,
        waitingFor: WaitingFor,
        holdingIdentity: HoldingIdentity,
        contextBuilder
        : (Set<SecureHash>) -> FlowStartContext
    )
}