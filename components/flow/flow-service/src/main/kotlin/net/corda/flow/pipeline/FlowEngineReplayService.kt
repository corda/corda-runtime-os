package net.corda.flow.pipeline

import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.flow.state.checkpoint.SavedOutputs
import net.corda.messaging.api.records.Record

/**
 * Service used by the Flow Engine to assist in detecting replays and generating output events to store in the Checkpoint.
 */
interface FlowEngineReplayService {

    /**
     * Examine the [checkpoint] to see if the [inputEventHash] is present in the saved output events and return any replay events
     * associated with that hash.
     * @param inputEventHash The hash of the input event
     * @param checkpoint The checkpoint
     * @return If the [inputEventHash] is present, return the list of output events associated with this input event.
     * If it is not present, then it is not replayed event. Null is returned
     */
    fun getReplayEvents(
        inputEventHash: String,
        checkpoint: Checkpoint?
    ): List<Record<*, *>>?

    /**
     * Generate a [SavedOutputs] to store output records from the flow engine in the [Checkpoint].
     * [SavedOutputs] will contain all the asynchronous [outputRecords] to be sent to the bus.
     * @param inputEventHash hash of the input event
     * @param outputRecords outputs to store in the checkpoint, associated with this hash.
     * @return The [SavedOutputs] to be added to the checkpoint
     */
    fun generateSavedOutputs(inputEventHash: String, outputRecords: List<Record<*, *>>): SavedOutputs
}
