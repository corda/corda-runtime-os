package net.corda.messaging.mediator

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.STATE_TYPE
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.metadata
import net.corda.messaging.api.constants.MessagingMetadataKeys.PROCESSING_FAILURE
import net.corda.messaging.api.processor.StateAndEventProcessor
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Helper for working with [StateManager], used by [MultiSourceEventMediatorImpl].
 */
class StateManagerHelper<S : Any>(
    private val serializer: CordaAvroSerializer<Any>,
    private val stateDeserializer: CordaAvroDeserializer<S>,
) {
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /**
     * Creates an updated [State] or a new one if there was no previous version.
     *
     * @param key Event's key.
     * @param mediatorState Mediator wrapper state.
     * @param persistedState State being updated.
     * @param newState Updated state.
     */
    fun createOrUpdateState(
        key: String,
        persistedState: State?,
        newState: StateAndEventProcessor.State<S>?,
    ) = serialize(newState?.value)?.let { serializedValue ->
        val stateType = newState!!.value!!::class.java.name
        State(
            key,
            serializedValue,
            persistedState?.version ?: State.VERSION_INITIAL_VALUE,
            mergeMetadata(persistedState?.metadata, newState.metadata, stateType),
        )
    }

    /**
     * Marks a state as having failed event mediator processing.
     *
     * In the event processing failed for a non-existent state, a new state is created with the metadata key set. This
     * allows clients to detect issues with any failed processing.
     *
     * @param key the unique identifier of the [State] to mark as failed.
     * @param originalState the original [State] that will me be marked as failed.
     * @param reason the actual reason for which the [State] will be marked as a failure, for logging purposes only.
     */
    fun failStateProcessing(key: String, originalState: State?, reason: String): State {
        logger.warn("State with key $key will be marked as failed, $reason.")

        val newMetadata = (originalState?.metadata?.toMutableMap() ?: mutableMapOf()).also {
            it[PROCESSING_FAILURE] = true
        }
        return State(
            key,
            originalState?.value ?: byteArrayOf(),
            version = originalState?.version ?: State.VERSION_INITIAL_VALUE,
            metadata = Metadata(newMetadata)
        )
    }

    private fun mergeMetadata(existing: Metadata?, newMetadata: Metadata?, stateType: String): Metadata {
        val map = (existing ?: metadata()).toMutableMap()
        newMetadata?.forEach { map[it.key] = it.value }
        map[STATE_TYPE] = stateType

        return Metadata(map)
    }

    /**
     * Serializes state value.
     *
     * @param value State value.
     * @return Serialized state value.
     */
    private fun serialize(value: S?) =
        value?.let { serializer.serialize(it) }

    /**
     * Deserializes state value.
     *
     * @param state State.
     * @return Deserialized state value.
     */
    fun deserializeValue(state: State?) =
        state?.value?.let { stateDeserializer.deserialize(it) }
}
