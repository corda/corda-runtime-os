package net.corda.flow.pipeline

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.state.checkpoint.Checkpoint

/**
 * Service to handle any logic around setting MDC data in the flow pipeline.
 */
interface FlowMDCService {

    /**
     * Extract out the MDC logging info from a flow event and checkpoint.
     * @param checkpoint the checkpoint for a flow. can be null if it is the first flow event for this key.
     * @param event the flow event received. MDC info can be extracted from the [StartFlow] event when the checkpoint is null.
     * @param flowId the id of the flow.
     * @return Map of fields to populate within the MDC taken from the flow.
     */
    fun getMDCLogging(checkpoint: Checkpoint?, event: FlowEvent?, flowId: String): Map<String, String>
}