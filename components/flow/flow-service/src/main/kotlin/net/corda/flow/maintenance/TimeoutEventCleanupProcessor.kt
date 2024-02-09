package net.corda.flow.maintenance

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.data.flow.FlowTimeout
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.state.impl.FlowCheckpointFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.utilities.debug
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
class TimeoutEventCleanupProcessor(
    private val checkpointCleanupHandler: CheckpointCleanupHandler,
    private val stateManager: StateManager,
    private val checkpointDeserializer: CordaAvroDeserializer<Checkpoint>,
    private val flowCheckpointFactory: FlowCheckpointFactory,
    private val config: SmartConfig
) : DurableProcessor<String, FlowTimeout> {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val keyClass = String::class.java
    override val valueClass = FlowTimeout::class.java

    private fun generateCleanupRecords(checkpoint: Checkpoint, timeOutReason: String): List<Record<*, *>> =
        checkpointCleanupHandler.cleanupCheckpoint(
            flowCheckpointFactory.create(checkpoint.flowId, checkpoint, config),
            config,
            FlowFatalException(timeOutReason)
        )

    override fun onNext(events: List<Record<String, FlowTimeout>>): List<Record<*, *>> {
        logger.debug { "Processing ${events.size} flows for timeout" }

        val timeOutReasonsByCheckpointId = events
            .mapNotNull { it.value }
            .associate { it.checkpointStateKey to it.reason }

        val statesToRecords = stateManager.get(events.mapNotNull {
            it.value?.checkpointStateKey
        }).mapNotNull { (key, state) ->
            checkpointDeserializer.deserialize(state.value)?.let {
                // This should never happen as we always get 'checkpointId' + 'reason' within 'FlowTimeout' record
                val timeOutReason = timeOutReasonsByCheckpointId[key] ?: "Unknown"
                logger.info("Flow '${it.flowId}' will be timed out due to '${timeOutReason}'")

                state to generateCleanupRecords(it, timeOutReason)
            }

        }.toMap()

        if (statesToRecords.size < events.size) {
            (events.mapNotNull { it.value?.checkpointStateKey }.toSet() - statesToRecords.keys.map { it.key }.toSet())
                .also {
                    logger.warn(
                        "Could not process flow timeout events for keys '${it.joinToString()}' as " +
                                "the checkpoint did not deserialize cleanly."
                    )
                }
        }

        val undeletedStates = stateManager.delete(statesToRecords.keys)
        if (undeletedStates.isNotEmpty()) {
            logger.info("Failed to delete checkpoints '${undeletedStates.keys.joinToString()}' when handling flow timeout.")
        }

        val records = statesToRecords.filterKeys {
            !undeletedStates.containsKey(it.key)
        }.map {
            it.value
        }.flatten()

        return records
    }
}
