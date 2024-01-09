package net.corda.messaging.mediator

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.metadata
import net.corda.messaging.api.constants.MessagingMetadataKeys.FAILED_STATE
import net.corda.messaging.api.processor.StateAndEventProcessor

/**
 * Helper for working with [StateManager], used by [MultiSourceEventMediatorImpl].
 */
class StateManagerHelper<S : Any>(
    private val stateSerializer: CordaAvroSerializer<S>,
    private val stateDeserializer: CordaAvroDeserializer<S>,
) {

    /**
     * Creates an updated [State] or a new one if there was no previous version.
     *
     * @param key Event's key.
     * @param persistedState State being updated.
     * @param newState Updated state.
     */
    fun createOrUpdateState(
        key: String,
        persistedState: State?,
        newState: StateAndEventProcessor.State<S>?,
    ) = serialize(newState?.value)?.let { serializedValue ->
        State(
            key,
            serializedValue,
            persistedState?.version ?: State.VERSION_INITIAL_VALUE,
            mergeMetadata(persistedState?.metadata, newState?.metadata),
        )
    }

    fun failStateProcessing(key: String, originalState: State?) : State {
        val newMetadata = (originalState?.metadata?.toMutableMap() ?: mutableMapOf()).also {
            it[FAILED_STATE] = true
        }
        return State(
            key,
            byteArrayOf(),
            version = originalState?.version ?: State.VERSION_INITIAL_VALUE,
            metadata = Metadata(newMetadata)
        )
    }

    private fun mergeMetadata(existing: Metadata?, newMetadata: Metadata?): Metadata {
        val map = (existing ?: metadata()).toMutableMap()
        newMetadata?.forEach {
            map[it.key] = it.value
        }
        return Metadata(map)
    }

    /**
     * Serializes state value.
     *
     * @param value State value.
     * @return Serialized state value.
     */
    private fun serialize(value: S?) =
        value?.let { stateSerializer.serialize(it) }

    /**
     * Deserializes state value.
     *
     * @param state State.
     * @return Deserialized state value.
     */
    fun deserializeValue(state: State?) =
        state?.value?.let { stateDeserializer.deserialize(it) }
}