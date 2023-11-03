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

class TimeoutEventCleanupProcessor(
    private val checkpointCleanupHandler: CheckpointCleanupHandler,
    private val stateManager: StateManager,
    private val avroDeserializer: CordaAvroDeserializer<Checkpoint>,
    private val flowCheckpointFactory: FlowCheckpointFactory,
    private val config: SmartConfig
) : DurableProcessor<String, FlowTimeout> {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun onNext(events: List<Record<String, FlowTimeout>>): List<Record<*, *>> {
        logger.debug { "Processing ${events.size} flows for timeout" }
        val statesToRecords = stateManager.get(events.mapNotNull {
            it.value?.checkpointStateKey
        }).mapNotNull { (_, state) ->
            avroDeserializer.deserialize(state.value)?.let {
                state to generateCleanupRecords(it)
            }
        }.toMap()
        if (statesToRecords.size < events.size) {
            logger.info(
                "Could not process ${events.size - statesToRecords.size} events for flow session timeout cleanup as the " +
                        "checkpoint did not deserialize cleanly."
            )
        }
//        val undeletedStates = stateManager.delete(statesToRecords.keys)
//        if (undeletedStates.isNotEmpty()) {
//            logger.info("Failed to delete ${undeletedStates.size} checkpoints when handling flow session timeout.")
//        }
//        val records = statesToRecords.filterKeys { !undeletedStates.containsKey(it.key) }.map {
//            it.value
//        }.flatten()
        return listOf()
    }

    private fun generateCleanupRecords(checkpoint: Checkpoint): List<Record<*, *>> {
        val flowCheckpoint = flowCheckpointFactory.create(checkpoint.flowId, checkpoint, config)
        return checkpointCleanupHandler.cleanupCheckpoint(
            flowCheckpoint,
            config,
            FlowFatalException("A session was timed out")
        )
    }

    override val keyClass = String::class.java
    override val valueClass = FlowTimeout::class.java
}