package net.corda.flow.maintenance

import net.corda.data.flow.FlowCheckpointTermination
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.utilities.debug
import net.corda.utilities.trace
import org.slf4j.LoggerFactory

/**
 * This processor is used to delete checkpoints that have reached their terminal state within the flow engine.
 * Checkpoints are held onto for a configurable amount of time to allow for duplicates, and replayed inputs to be passed the flow engine
 * and for the flow engine to replay the outputs for each input.
 */
class FlowCheckpointTerminationCleanupProcessor(
    private val stateManager: StateManager
) : DurableProcessor<String, FlowCheckpointTermination> {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun onNext(events: List<Record<String, FlowCheckpointTermination>>): List<Record<*, *>> {
        events.mapNotNull { it.value }.forEach {
            process(it)
        }
        return listOf()
    }

    private fun process(event: FlowCheckpointTermination) {
        logger.debug { "Checkpoint termination event received with ${event.checkpointStateKeys.size} keys to remove" }
        val states = stateManager.get(event.checkpointStateKeys)
        logger.trace { "Looked up ${states.size} states" }
        val failed = stateManager.delete(states.values)
        if (failed.isNotEmpty()) {
            logger.info(
                "Failed to delete ${failed.size} checkpoint states when executing a checkpoint termination event. Failed IDs: ${
                    failed.keys.joinToString(
                        ","
                    )
                }"
            )
        }
    }

    override val keyClass = String::class.java
    override val valueClass = FlowCheckpointTermination::class.java
}
