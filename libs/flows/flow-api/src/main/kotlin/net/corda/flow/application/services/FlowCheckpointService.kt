package net.corda.flow.application.services

import net.corda.flow.state.FlowCheckpoint

interface FlowCheckpointService {

    /**
     * Retrieves the checkpoint
     */
    fun getCheckpoint(): FlowCheckpoint
}