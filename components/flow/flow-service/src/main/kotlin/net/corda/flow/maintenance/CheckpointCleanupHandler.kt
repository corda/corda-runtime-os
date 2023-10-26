package net.corda.flow.maintenance

import net.corda.flow.state.FlowCheckpoint
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
import java.lang.Exception

interface CheckpointCleanupHandler {

    /**
     * Generate any records to clean up the system when a flow is terminated.
     *
     * This will also modify the checkpoint state to indicate that it should be removed.
     *
     * @param checkpoint The checkpoint to evaluate in order to generate any cleanup records.
     * @param config Flow configuration
     * @param exception The exception causing the checkpoint to be cleaned up.
     * @return A list of records to be published to the rest of the system to clean up any state for this flow.
     */
    fun cleanupCheckpoint(checkpoint: FlowCheckpoint, config: SmartConfig, exception: Exception) : List<Record<*, *>>
}